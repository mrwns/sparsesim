package org.retrosynthesis.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.retrosynthesis.index.Result;
import org.retrosynthesis.index.SearchLimits;
import org.retrosynthesis.index.Searcher;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("web")
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
public class SimilarityTools {

    private final Searcher searcher;
    private final ObjectMapper mapper;

    public SimilarityTools(Searcher searcher, ObjectMapper mapper) {
        this.searcher = searcher;
        this.mapper = mapper;
    }

    @McpTool(name = "search_similar_reactions",
            description = "Search for chemical reactions similar to a query reaction.")
    public String searchSimilarReactions(
            @McpToolParam(description = "Reaction SMILES in reactants>>products format", required = true) String smiles,
            @McpToolParam(description = "Maximum number of results to return, 1 to 1000 (default 10)", required = false) Integer knn,
            @McpToolParam(description = "Product SMILES for substructure filter", required = false) String subgraph,
            @McpToolParam(description = "Reaction SMARTS pattern to filter results", required = false) String rxnSmarts,
            @McpToolParam(description = "Weight in [0,1] of product similarity. Omit to rank by reaction similarity only. When given, candidates are also scored by product ECFP4 Tanimoto (returned as ecfp_prod) and sim = (1 - productWeight) * reaction_score + productWeight * ecfp_prod. Values outside [0,1] are rejected.", required = false) Double productWeight,
            @McpToolParam(description = "Rerank results by exact Tanimoto similarity", required = false) Boolean rerank
    ) throws Exception {
        List<Result> results = searcher.searchBySmiles(
                smiles,
                subgraph,
                rxnSmarts,
                SearchLimits.requireKnnWithinLimit(knn != null ? knn : SearchLimits.DEFAULT_KNN),
                true,
                productWeight,
                rerank != null ? rerank : true
        );
        return mapper.writeValueAsString(results);
    }

    @McpTool(name = "search_reactions_by_molecule",
            description = "Search for reactions whose product is similar to a query molecule (ECFP4 Tanimoto on the product side).")
    public String searchReactionsByMolecule(
            @McpToolParam(description = "Query molecule SMILES (not a reaction SMILES)", required = true) String smiles,
            @McpToolParam(description = "Maximum number of results to return, 1 to 1000 (default 10)", required = false) Integer knn,
            @McpToolParam(description = "Molecule SMILES used as a product-side substructure filter (path fingerprint)", required = false) String subgraph,
            @McpToolParam(description = "Reaction SMARTS pattern to filter candidate reactions after retrieval", required = false) String rxnSmarts,
            @McpToolParam(description = "Rerank results by exact ECFP4 Tanimoto similarity", required = false) Boolean rerank
    ) throws Exception {
        List<Result> results = searcher.searchByMolSmiles(
                smiles,
                subgraph,
                rxnSmarts,
                SearchLimits.requireKnnWithinLimit(knn != null ? knn : SearchLimits.DEFAULT_KNN),
                rerank != null ? rerank : true
        );
        return mapper.writeValueAsString(results);
    }
}
