package org.retrosynthesis.index;

import org.apache.lucene.document.*;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.analysis.TokenStream;
import org.openscience.cdk.fingerprint.CircularFingerprinter;
import org.openscience.cdk.fingerprint.Fingerprinter;
import org.openscience.cdk.fingerprint.ICountFingerprint;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IReaction;
import org.retrosynthesis.chem.FingerprintConverter;
import org.retrosynthesis.chem.FingerprintUtil;
import org.retrosynthesis.chem.IReactionFingerprinter;
import org.retrosynthesis.chem.ReactionFingerprinter;
import org.retrosynthesis.chem.SmilesXMol;

import static org.retrosynthesis.index.FieldNames.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


public class ComplexIndexer implements AutoCloseable {
    
    private final Directory dir;
    private final IndexWriter writer;
    private static final FieldType COUNT_FT;
    private static final FieldType BINARY_FT;

    static {
        COUNT_FT = new FieldType();
        COUNT_FT.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        COUNT_FT.setStored(false);
        COUNT_FT.setTokenized(true);
        COUNT_FT.freeze();

        BINARY_FT = new FieldType();
        BINARY_FT.setIndexOptions(IndexOptions.DOCS);
        BINARY_FT.setStored(false);
        BINARY_FT.setTokenized(true);
        BINARY_FT.freeze();
    }

    public ComplexIndexer(Path indexDir) throws Exception {
        this.dir = FSDirectory.open(indexDir);
        this.writer = new IndexWriter(dir,
                new IndexWriterConfig(/* analyzer not used since we give TokenStream */));
    }

    public void addReaction(InputReaction reaction) throws Exception {
        addReaction(reaction.smiles(), reaction.id());
    }


    public void addReaction(String smiles, String id) throws Exception {
        try {
        
        IReaction rxn = SmilesXMol.reaction(smiles);

        final IChemObjectBuilder bldr = rxn.getBuilder();
        final IAtomContainer mol = bldr.newInstance(IAtomContainer.class);
        
        for (IAtomContainer comp : rxn.getProducts().atomContainers()) {
            mol.add(comp);
        }
                        
        IReactionFingerprinter<Integer> fingerprinter = new ReactionFingerprinter<Integer>();
        
        Map<Integer, Integer> counts = fingerprinter.getRawFingerprint(rxn);

        var doc = new Document();
        
        doc.add(new StoredField(FieldNames.FIELD_RXN_SMILES, smiles));
        doc.add(new StringField(FieldNames.FIELD_RXN_ID, id, Store.YES));

        // doc.add(new StoredField(FIELD_FP_COUNTS, FingerprintUtil.countsToCsv(counts)));
        
        doc.add(new NumericDocValuesField(FIELD_FP_L1, FingerprintUtil.l1(counts)));

        // Index features with frequencies via custom TokenStream
        TokenStream ts1 = new CountTokenStream(counts);
        doc.add(new Field(FIELD_RXNFP_ECFP, ts1, COUNT_FT));

        IReactionFingerprinter<Integer> pathrxnfpr = new ReactionFingerprinter<Integer>(new Fingerprinter(768763, 6));
        
        ICountFingerprint pathrxnfp = pathrxnfpr.getCountFingerprint(rxn);
        Map<Integer, Integer> pathrxnCounts = FingerprintConverter.convert(pathrxnfp);
        TokenStream ts2a = new CountTokenStream(pathrxnCounts);
        doc.add(new Field(FIELD_RXNFP_PATH, ts2a, COUNT_FT));    

        Fingerprinter pathfp = new Fingerprinter(768763);
        BitSet bitSet = pathfp.getFingerprint(mol);

        TokenStream ts2 = new CountTokenStream(bitSet);
        doc.add(new Field(FIELD_PRODFPT_PATH, ts2, BINARY_FT));

        CircularFingerprinter ecfpfp = new CircularFingerprinter(CircularFingerprinter.CLASS_ECFP4);
        
        ICountFingerprint ecfp = ecfpfp.getCountFingerprint(mol);
        Map<Integer, Integer> ecfpCounts = FingerprintConverter.convert(ecfp);
        TokenStream ts3 = new CountTokenStream(ecfpCounts);

        doc.add(new Field(FIELD_PRODFPT_ECFP, ts3, COUNT_FT));

        writer.addDocument(doc);
         
      } catch (Exception e) {
             System.err.println(smiles + " error " + id);
             e.printStackTrace();
            //  throw new RuntimeException(e);
        }
        
    }

    /** Indexes reactions in parallel; each keeps the ID it comes with. */
    public void addAll(List<InputReaction> reactions) throws Exception {
        AtomicInteger done = new AtomicInteger(0);
        reactions.parallelStream().forEach(r -> {
            try {
                addReaction(r);
                int n = done.incrementAndGet();
                if (n % 5000 == 0) {
                    System.out.print(".");
                }
                if (n % 250000 == 0) {
                    System.out.print(String.format("%12s", n));
                    System.out.println();
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println();
        writer.forceMerge(1);  // collapse to single segment for faster reads
        writer.commit();
    }

    public void addAll(Stream<InputReaction> reactions) throws Exception {
        reactions.forEach(r -> {
            try {
                addReaction(r);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        writer.forceMerge(1);  // collapse to single segment for faster reads
        writer.commit();
    }

    @Override public void close() throws Exception { writer.close(); dir.close(); }
}
