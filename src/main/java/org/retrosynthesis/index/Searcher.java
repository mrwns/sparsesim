package org.retrosynthesis.index;


import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.MMapDirectory;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.fingerprint.CircularFingerprinter;
import org.openscience.cdk.fingerprint.Fingerprinter;
import org.openscience.cdk.fingerprint.ICountFingerprint;
import org.openscience.cdk.fingerprint.IFingerprinter;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.similarity.Tanimoto;
import org.retrosynthesis.chem.FingerprintConverter;
import org.retrosynthesis.chem.ReactionFingerprinter;
import org.retrosynthesis.chem.SmartsFilter;
import org.retrosynthesis.chem.SmilesXMol;

import java.nio.file.Path;
import java.util.*;

import static org.retrosynthesis.index.FieldNames.*;

import java.io.IOException;
import org.apache.lucene.index.LeafReaderContext;

public class Searcher implements AutoCloseable {
    private final DirectoryReader reader;
    private final IndexSearcher searcher;
    private final MinOverlapSimilarity minSim = new MinOverlapSimilarity();
    Similarity similarity = new BM25Similarity();

    //Similarity similarity = new ChemicalSimilarity();;
    

    public Searcher(Path indexDir) throws Exception {
        var dir = new MMapDirectory(indexDir);
        dir.setPreload(true);  // madvise(MADV_WILLNEED) - prefetch index into page cache
        this.reader = DirectoryReader.open(dir);
        this.searcher = new IndexSearcher(reader);
        this.searcher.setSimilarity(similarity);
        this.searcher.setQueryCache(null);  // queries are unique per request; cache wastes heap
    }

    /**
     * Warm up the index by eagerly loading terms dictionaries and doc values
     * into the OS page cache. Call once after construction to ensure predictable
     * first-query latency.
     */
    public void warmup() throws IOException {
        for (LeafReaderContext leaf : reader.leaves()) {
            var leafReader = leaf.reader();
            // Warm NumericDocValues
            leafReader.getNumericDocValues(FIELD_FP_L1);
            // Warm terms dictionaries for all fingerprint fields
            leafReader.terms(FIELD_RXNFP_ECFP);
            leafReader.terms(FIELD_RXNFP_PATH);
            leafReader.terms(FIELD_PRODFPT_PATH);
            leafReader.terms(FIELD_PRODFPT_ECFP);
        }
    }

    public int numReactions() {
        return reader.numDocs();
    }

    public long indexSize(String field){
        long indexSize = 0;
        try {
            Terms terms = MultiTerms.getTerms(reader, field);
            if (terms == null) {
                return 0;
            }
            indexSize = terms.size();
            if (indexSize == -1) {
                System.out.println("Index size not available from codec, counting manually");
                // Size not available from codec, count manually
                TermsEnum termsEnum = terms.iterator();
                indexSize = 0;
                while (termsEnum.next() != null) {
                    indexSize++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return indexSize;
    }

    /** Build SHOULD clauses for each feature with boost = qtf (injects qtf into MinOverlapSimilarity). */
    private Query buildCountFpOrQuery(Map<Integer, Integer> qCounts, String field, float boost) {

        BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (var e : qCounts.entrySet()) {
            int hash = e.getKey(); 
            int count = e.getValue();
            Term t = new Term(field, "f" + Math.signum(count) + "_" + hash);
            // Encode qtf via boost: our Similarity reads this as the term's qtf weight.
            Query tq = new BoostQuery(new TermQuery(t), Math.abs(count * boost));
            b.add(tq, BooleanClause.Occur.SHOULD);
        }
        return b.build();
    }

    private Query buildBinaryQuery(BitSet hashes, String field, boolean must, float boost) {

        BooleanQuery.Builder b = new BooleanQuery.Builder();
        hashes.stream().forEach(hash -> {
            Term t = new Term(field, "f" + Math.signum(1) + "_" + hash);
            // Encode qtf via boost: our Similarity reads this as the term's qtf weight.
            Query tq = new BoostQuery(new TermQuery(t), boost);
            b.add(tq, must ? BooleanClause.Occur.MUST : BooleanClause.Occur.SHOULD);
        });
        return b.build();
    }

    public List<Result> searchBySmiles(String querySmiles, int topK) throws Exception {
    
        return searchBySmiles(querySmiles, null, null, topK, true, false, 0.0f, true);
    }

    /**
     * Molecule-only similarity search against the indexed product side.
     *
     * Scoring uses only the product ECFP4 count fingerprint. The path
     * fingerprint on the product is used solely as an optional substructure
     * filter (via {@code subgraphSmiles}). A reaction SMARTS filter is applied
     * post-retrieval against the full stored reaction, identical to
     * {@link #searchBySmiles}.
     *
     * @param queryMolSmiles  Query molecule SMILES (not a reaction SMILES).
     * @param subgraphSmiles  Optional molecule SMILES used as a substructure
     *                        filter on the indexed product path fingerprint.
     * @param rxnSmarts       Optional SMARTS applied to the candidate reaction
     *                        after retrieval (see {@link SmartsFilter}).
     * @param topK            Lucene top-K cap.
     * @param rerank          When true, sort results by the recomputed
     *                        ECFP4 Tanimoto.
     */
    public List<Result> searchByMolSmiles(String queryMolSmiles, String subgraphSmiles, String rxnSmarts, int topK, boolean rerank) throws Exception {

        requireValidTopK(topK);

        IAtomContainer queryMol = parseQueryMol(queryMolSmiles, "molecule");

        SmartsFilter rxnSmartsFilter = parseSmartsFilter(rxnSmarts);

        IFingerprinter ecfpr = new CircularFingerprinter(CircularFingerprinter.CLASS_ECFP4);
        ICountFingerprint queryEcfp = ecfpr.getCountFingerprint(queryMol);
        Map<Integer, Integer> queryEcfpCounts = FingerprintConverter.convert(queryEcfp);

        var builder = new BooleanQuery.Builder();

        Query ecfpQuery = buildCountFpOrQuery(queryEcfpCounts, FIELD_PRODFPT_ECFP, 1.0f);
        builder.add(ecfpQuery, BooleanClause.Occur.SHOULD);

        if (subgraphSmiles != null && !subgraphSmiles.isBlank()) {

            IAtomContainer subMol = parseQueryMol(subgraphSmiles, "subgraph");
            Fingerprinter pathfp = new Fingerprinter(768763);
            BitSet bitSet = pathfp.getFingerprint(subMol);

            Query p = buildBinaryQuery(bitSet, FIELD_PRODFPT_PATH, true, 1.0f);
            builder.add(p, BooleanClause.Occur.FILTER);
        }

        Query q = builder.build();

        TopDocs hits = searcher.search(q, topK);

        List<Result> out = new ArrayList<>(Math.min(hits.scoreDocs.length, topK));

        ScoreDoc[] scoredocs = hits.scoreDocs;

        for (ScoreDoc sd : scoredocs) {

            Document d = searcher.doc(sd.doc);

            String ressmiles = d.get(FIELD_RXN_SMILES);
            int xid = Integer.parseInt(d.get(FIELD_RXN_ID));
            IReaction resrxn = SmilesXMol.reaction(ressmiles);

            if (!rxnSmartsFilter.filter(resrxn)) {
                continue;
            }

            ICountFingerprint resEcfp = ecfpr.getCountFingerprint(products(resrxn));

            double prodtanimoto = Tanimoto.method1(queryEcfp, resEcfp);

            out.add(new Result(
                ressmiles,
                prodtanimoto,
                xid,
                Map.of("ecfp_prod", prodtanimoto)
            ));
        }

        if (rerank) {
            out.sort((a, b) -> Double.compare(b.sim(), a.sim()));
        }

        return out;
    }
    
    public List<Result> searchBySmiles(String queryRxnSmiles, String subgraphSmiles, String rxnSmarts, int topK, boolean reactionSim, boolean productsim, float productBoost, boolean rerank) throws Exception {

        requireValidTopK(topK);

        float pb = Math.max(0.0f, Math.min(1.0f, productBoost));

        IReaction rxn = parseQueryReaction(queryRxnSmiles);

        SmartsFilter rxnSmartsFilter = parseSmartsFilter(rxnSmarts);

        ReactionFingerprinter<Integer> ecfpRxnFpr = new ReactionFingerprinter<Integer>();
        Map<Integer, Integer> ecRfp = ecfpRxnFpr.getRawFingerprint(rxn);
        int pathDepth = 6;
        ReactionFingerprinter<Integer> pathRxnFpr = new ReactionFingerprinter<Integer>(new Fingerprinter(768763, pathDepth));
        ICountFingerprint pathRFP = pathRxnFpr.getCountFingerprint(rxn);
        Map<Integer, Integer> pathRfp = FingerprintConverter.convert(pathRFP);

        var builder = new BooleanQuery.Builder();

        if (reactionSim) {

            Query q = buildCountFpOrQuery(ecRfp, FIELD_RXNFP_ECFP, 1.0f);
            Query q2 = buildCountFpOrQuery(pathRfp, FIELD_RXNFP_PATH, 1.0f);

            builder = builder.add(q, BooleanClause.Occur.SHOULD).add(q2, BooleanClause.Occur.SHOULD);

        }

        if (subgraphSmiles != null && !subgraphSmiles.isBlank()) {

            IAtomContainer productMol = parseQueryMol(subgraphSmiles, "subgraph");

            Fingerprinter pathfp = new Fingerprinter(768763);

            BitSet bitSet = pathfp.getFingerprint(productMol);

            Query p = buildBinaryQuery(bitSet, FIELD_PRODFPT_PATH, true, 1.0f);

            builder = builder.add(p, BooleanClause.Occur.FILTER);
        }

        IFingerprinter ecfpr = null;
        ICountFingerprint queryProductEcfp = null;

        if (productsim) {

            if (rxn.getProductCount() == 0) {
                throw new InvalidQueryException("productsim requires a reaction with products: " + queryRxnSmiles);
            }

            ecfpr = new CircularFingerprinter(CircularFingerprinter.CLASS_ECFP4);
            queryProductEcfp = ecfpr.getCountFingerprint(products(rxn));
            Map<Integer, Integer> ecfpCounts = FingerprintConverter.convert(queryProductEcfp);

            Query p = buildCountFpOrQuery(ecfpCounts, FIELD_PRODFPT_ECFP, 1.0f);
            builder = builder.add(p, BooleanClause.Occur.SHOULD);
        }

        Query q = builder.build();

        TopDocs hits = searcher.search(q, topK);

        List<Result> out = new ArrayList<>(Math.min(hits.scoreDocs.length, topK));

        ScoreDoc[] scoredocs = hits.scoreDocs;

        ICountFingerprint ref = ecfpRxnFpr.transform(ecRfp);

        for (ScoreDoc sd : scoredocs) {

            Document d = searcher.doc(sd.doc);

            String ressmiles = d.get(FIELD_RXN_SMILES);
            int xid = Integer.parseInt(d.get(FIELD_RXN_ID));
            IReaction resrxn = SmilesXMol.reaction(ressmiles);
            ICountFingerprint fp = ecfpRxnFpr.getCountFingerprint(resrxn);
            ICountFingerprint pathfp = pathRxnFpr.getCountFingerprint(resrxn);

            if (!rxnSmartsFilter.filter(resrxn)) {
                continue;
            }

            double ecfptanimoto = Tanimoto.method1(ref, fp);

            double pathtanimoto = Tanimoto.method1(pathRFP, pathfp);

            double reactionScore = (ecfptanimoto + pathtanimoto) * 0.5;

            double total;
            Map<String, Double> similarities;
            if (productsim) {
                ICountFingerprint resProductEcfp = ecfpr.getCountFingerprint(products(resrxn));
                double prodtanimoto = Tanimoto.method1(queryProductEcfp, resProductEcfp);

                total = (1.0 - pb) * reactionScore + pb * prodtanimoto;
                similarities = Map.of(
                    "ecfp4", ecfptanimoto,
                    "path_" + pathDepth, pathtanimoto,
                    "ecfp_prod", prodtanimoto
                );
            } else {
                total = reactionScore;
                similarities = Map.of(
                    "ecfp4", ecfptanimoto,
                    "path_" + pathDepth, pathtanimoto
                );
            }

            out.add(new Result(
                ressmiles,
                total,
                xid,
                similarities
            ));
        };

        if (rerank) {
            out.sort((a,b) -> Double.compare(b.sim(), a.sim()));
        }

        return out;

    }

    /** All products of a reaction combined into one container. */
    private static IAtomContainer products(IReaction rxn) {
        IAtomContainer combined = rxn.getBuilder().newInstance(IAtomContainer.class);
        for (IAtomContainer comp : rxn.getProducts().atomContainers()) {
            combined.add(comp);
        }
        return combined;
    }

    private static void requireValidTopK(int topK) {
        if (topK < 1) {
            throw new InvalidQueryException("knn must be at least 1, got " + topK);
        }
    }

    private static IReaction parseQueryReaction(String smiles) {
        try {
            return SmilesXMol.reaction(smiles);
        } catch (InvalidSmilesException e) {
            throw new InvalidQueryException("Invalid reaction SMILES: " + e.getMessage());
        }
    }

    private static IAtomContainer parseQueryMol(String smiles, String role) {
        try {
            return SmilesXMol.mol(smiles);
        } catch (InvalidSmilesException e) {
            throw new InvalidQueryException("Invalid " + role + " SMILES: " + e.getMessage());
        }
    }

    private static SmartsFilter parseSmartsFilter(String smarts) {
        try {
            return new SmartsFilter(smarts);
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryException("Invalid rxnSmarts: " + e.getMessage());
        }
    }

    @Override public void close() throws Exception { reader.close(); }
}

