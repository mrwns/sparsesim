package org.retrosynthesis.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import org.retrosynthesis.index.InvalidQueryException
import org.retrosynthesis.index.Searcher
import spock.lang.Specification

class SimilarityToolsTest extends Specification {

    Searcher searcher = Mock()
    SimilarityTools tools = new SimilarityTools(searcher, new ObjectMapper())

    def "knn above 1000 is rejected before searching by reaction"() {
        when:
        tools.searchSimilarReactions("CCO>>CC=O", 1001, null, null, null, null)

        then:
        def e = thrown(InvalidQueryException)
        e.message == "knn must be at most 1000, got 1001"
        0 * searcher._
    }

    def "knn above 1000 is rejected before searching by product"() {
        when:
        tools.searchReactionsByMolecule("CCO", 1001, null, null, null)

        then:
        def e = thrown(InvalidQueryException)
        e.message == "knn must be at most 1000, got 1001"
        0 * searcher._
    }

    def "productWeight is passed to the search as the product weight"() {
        when:
        tools.searchSimilarReactions("CCO>>CC=O", null, null, null, weight, null)

        then:
        1 * searcher.searchBySmiles("CCO>>CC=O", null, null, 10, true, weight, true) >> []

        where:
        weight << [null, 0.0d, 0.4d]
    }

    def "knn of exactly 1000 is accepted"() {
        when:
        tools.searchReactionsByMolecule("CCO", 1000, null, null, null)

        then:
        1 * searcher.searchByMolSmiles("CCO", null, null, 1000, true) >> []
    }

    def "knn defaults to 10"() {
        when:
        tools.searchReactionsByMolecule("CCO", null, null, null, null)

        then:
        1 * searcher.searchByMolSmiles("CCO", null, null, 10, true) >> []
    }
}
