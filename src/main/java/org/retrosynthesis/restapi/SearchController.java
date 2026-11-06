package org.retrosynthesis.restapi;

import org.retrosynthesis.index.InvalidQueryException;
import org.retrosynthesis.index.Result;
import org.retrosynthesis.index.SearchLimits;
import org.retrosynthesis.index.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@RestController
@Profile("web")
public class SearchController {

    private final Searcher searcher;
    private final Logger logger = LoggerFactory.getLogger(SearchController.class);

    public SearchController(Searcher searcher) {
        this.searcher = searcher;
    }

    /**
     * JSON body for {@link #search(SearchRequest)}. Semantics match {@code SPARSESIM_ENDPOINT.md} ({@code QUERY /reactions/similarity/by-reaction}).
     *
     * @param smiles       one or more query reaction SMILES (reactants {@literal >>} products)
     * @param knn          maximum Lucene hits per query string (before SMARTS post-filter), 1 to {@link SearchLimits#MAX_KNN}
     * @param weights      optional score weights; omitted for pure reaction similarity, see {@link Weights}
     * @param subgraph     optional product SMILES for a path-fingerprint substructure {@code FILTER}; may be {@code null}
     * @param rxnSmarts    optional SMARTS filter on the full reaction after retrieval; may be {@code null}
     * @param rerank       when {@code true}, sort by {@code sim} descending
     */
    public record SearchRequest(
            List<String> smiles,
            int knn,
            Weights weights,
            String subgraph,
            String rxnSmarts,
            boolean rerank
    ) {
        @JsonCreator
        public static SearchRequest create(
                @JsonProperty("smiles") List<String> smiles,
                @JsonProperty("knn") Integer knn,
                @JsonProperty("weights") Weights weights,
                @JsonProperty("subgraph") String subgraph,
                @JsonProperty("rxnSmarts") String rxnSmarts,
                @JsonProperty("rerank") Boolean rerank
        ) {
            return new SearchRequest(
                    smiles,
                    knn != null ? knn : SearchLimits.DEFAULT_KNN,
                    weights,
                    subgraph,
                    rxnSmarts,
                    rerank != null ? rerank : true
            );
        }

        /** The product weight to search with, or {@code null} for pure reaction similarity. */
        Double productWeight() {
            if (weights == null) {
                return null;
            }
            if (weights.product() == null) {
                throw new InvalidQueryException("weights must include product");
            }
            return weights.product();
        }
    }

    /**
     * Score weights. Giving {@code product} turns on product similarity: it broadens retrieval, is reported as
     * {@code ecfp_prod}, and is blended into {@code sim = (1 - product) * reaction_score + product * ecfp_prod};
     * it must be in {@code [0, 1]}.
     */
    public record Weights(Double product) {
    }

    /**
     * JSON body for {@link #molSearch(MolSearchRequest)} ({@code QUERY /reactions/similarity/by-product}).
     *
     * @param smiles     one or more query molecule SMILES (not reaction SMILES)
     * @param knn        maximum Lucene hits per query string, 1 to {@link SearchLimits#MAX_KNN}
     * @param subgraph   optional molecule SMILES for product path-fingerprint substructure filter; may be {@code null}
     * @param rxnSmarts  optional SMARTS filter on the full reaction after retrieval; may be {@code null}
     * @param rerank     when {@code true}, sort by product ECFP4 Tanimoto ({@code sim}) descending
     */
    public record MolSearchRequest(
            List<String> smiles,
            int knn,
            String subgraph,
            String rxnSmarts,
            boolean rerank
    ) {
        @JsonCreator
        public static MolSearchRequest create(
                @JsonProperty("smiles") List<String> smiles,
                @JsonProperty("knn") Integer knn,
                @JsonProperty("subgraph") String subgraph,
                @JsonProperty("rxnSmarts") String rxnSmarts,
                @JsonProperty("rerank") Boolean rerank
        ) {
            return new MolSearchRequest(
                    smiles,
                    knn != null ? knn : SearchLimits.DEFAULT_KNN,
                    subgraph,
                    rxnSmarts,
                    rerank != null ? rerank : true
            );
        }
    }

    /**
     * Reaction similarity search. Delegates to {@link Searcher#searchBySmiles}.
     */
    @HttpQueryMapping("/reactions/similarity/by-reaction")
    public ResponseEntity<List<List<Result>>> search(@RequestBody SearchRequest request) throws Exception {
        
        long t1 = System.currentTimeMillis();

        logger.info("Search request: {}", request);
        
        requireSmiles(request.smiles);
        SearchLimits.requireKnnWithinLimit(request.knn);
        Double productWeight = request.productWeight();
        List<List<Result>> results = new ArrayList<>();
        for (String smiles : request.smiles) {

            results.add(searcher.searchBySmiles(
                smiles,
                request.subgraph,
                request.rxnSmarts,
                request.knn,
                true,
                productWeight,
                request.rerank
            ));
        }
        long t2 = System.currentTimeMillis();

        logger.info("/reactions/similarity/by-reaction: {} ms", t2-t1);
        return ResponseEntity.ok(results);
    }

    /**
     * Molecule similarity over indexed reaction products. Delegates to {@link Searcher#searchByMolSmiles}.
     */
    @HttpQueryMapping("/reactions/similarity/by-product")
    public ResponseEntity<List<List<Result>>> molSearch(@RequestBody MolSearchRequest request) throws Exception {

        long t1 = System.currentTimeMillis();

        logger.info("Mol search request: {}", request);

        requireSmiles(request.smiles);
        SearchLimits.requireKnnWithinLimit(request.knn);
        List<List<Result>> results = new ArrayList<>();
        for (String smiles : request.smiles) {

            results.add(searcher.searchByMolSmiles(
                smiles,
                request.subgraph,
                request.rxnSmarts,
                request.knn,
                request.rerank
            ));
        }
        long t2 = System.currentTimeMillis();

        logger.info("/reactions/similarity/by-product: {} ms", t2-t1);
        return ResponseEntity.ok(results);
    }

    /** Any other method on the search paths gets 405 + {@code Allow: QUERY} rather than a 404. */
    @RequestMapping({"/reactions/similarity/by-reaction", "/reactions/similarity/by-product"})
    public void wrongMethod(HttpMethod method) throws HttpRequestMethodNotSupportedException {
        throw new HttpRequestMethodNotSupportedException(method.name(), List.of(QueryMethodConfig.QUERY));
    }

    private static void requireSmiles(List<String> smiles) {
        if (smiles == null || smiles.isEmpty()) {
            throw new InvalidQueryException("smiles must contain at least one SMILES");
        }
    }

    // @PostMapping("/tanimoto")
    // public float tanimoto){

    // }

}


