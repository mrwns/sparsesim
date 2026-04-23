package org.retrosynthesis.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.retrosynthesis.index.Result;
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
            @McpToolParam(description = "Maximum number of results to return", required = false) Integer knn,
            @McpToolParam(description = "Product SMILES for substructure filter", required = false) String subgraph,
            @McpToolParam(description = "Reaction SMARTS pattern to filter results", required = false) String rxnSmarts,
            @McpToolParam(description = "When true, also scores candidates by product ECFP4 Tanimoto and returns it as ecfp_prod in similarities. Only active for this call.", required = false) Boolean productsim,
            @McpToolParam(description = "Weight in [0,1] of the product ECFP4 Tanimoto in the final sim score when productsim=true. sim = (1 - productBoost) * reaction_score + productBoost * product_tanimoto. Ignored when productsim=false. Values outside [0,1] are clamped.", required = false) Float productBoost,
            @McpToolParam(description = "Rerank results by exact Tanimoto similarity", required = false) Boolean rerank
    ) throws Exception {
        List<Result> results = searcher.searchBySmiles(
                smiles,
                subgraph,
                rxnSmarts,
                knn != null ? knn : 10,
                true,
                productsim != null ? productsim : false,
                productBoost != null ? productBoost : 0.0f,
                rerank != null ? rerank : true
        );
        return mapper.writeValueAsString(results);
    }

    @McpTool(name = "search_reactions_by_molecule",
            description = "Search for reactions whose product is similar to a query molecule (ECFP4 Tanimoto on the product side).")
    public String searchReactionsByMolecule(
            @McpToolParam(description = "Query molecule SMILES (not a reaction SMILES)", required = true) String smiles,
            @McpToolParam(description = "Maximum number of results to return", required = false) Integer knn,
            @McpToolParam(description = "Molecule SMILES used as a product-side substructure filter (path fingerprint)", required = false) String subgraph,
            @McpToolParam(description = "Reaction SMARTS pattern to filter candidate reactions after retrieval", required = false) String rxnSmarts,
            @McpToolParam(description = "Rerank results by exact ECFP4 Tanimoto similarity", required = false) Boolean rerank
    ) throws Exception {
        List<Result> results = searcher.searchByMolSmiles(
                smiles,
                subgraph,
                rxnSmarts,
                knn != null ? knn : 10,
                rerank != null ? rerank : true
        );
        return mapper.writeValueAsString(results);
    }
}
