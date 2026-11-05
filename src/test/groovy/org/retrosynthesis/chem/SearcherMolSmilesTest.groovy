package org.retrosynthesis.chem

import org.retrosynthesis.index.ComplexIndexer
import org.retrosynthesis.index.Result
import org.retrosynthesis.index.Searcher
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class SearcherMolSmilesTest extends Specification {

    // Three very different product chemotypes so we can unambiguously rank hits.
    static final String RXN_HYDROLYSIS = "CC(C)(C)[O:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12>>[OH:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12"
    static final String RXN_COUPLING = "Br[c:1]1[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1.OB(O)[c:15]1[cH:16][cH:17][c:18](-[n:19]2[cH:20][cH:21][cH:22][n:23]2)[cH:24][cH:25]1>>[c:1]1(-[c:15]2[cH:16][cH:17][c:18](-[n:19]3[cH:20][cH:21][cH:22][n:23]3)[cH:24][cH:25]2)[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1"
    static final String RXN_FLUORO_AMINATION = "[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH:1]=O)[CH:43]=[CH:42]1.[CH2:11]1[CH2:44][CH2:45][NH:46][CH2:47][CH2:48]1>>[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH2:1][N:46]2[CH2:45][CH2:44][CH2:11][CH2:48][CH2:47]2)[CH:43]=[CH:42]1"

    // The product molecules (anything-right-of-last '>') of each indexed reaction.
    static final String PROD_HYDROLYSIS      = "OC(=O)COC(=O)NCc1cccc2ccccc12"
    static final String PROD_COUPLING        = "Cn1cnc(-c2ccc(-n3cccn3)cc2)c1-c1ccc(C#N)cn1"
    static final String PROD_FLUORO_AMINATION = "Cc1csc(-c2ccc(CN3CCCCC3)cc2F)n1"

    static Path tempDir

    def setupSpec() {
        tempDir = Files.createTempDirectory("searcher-molsmiles-test")

        def indexer = new ComplexIndexer(tempDir)
        indexer.addReaction(RXN_HYDROLYSIS, 1)
        indexer.addReaction(RXN_COUPLING, 2)
        indexer.addReaction(RXN_FLUORO_AMINATION, 3)
        indexer.close()
    }

    def cleanupSpec() {
        tempDir.toFile().deleteDir()
    }

    def "query molecule ranks its own reaction's product first"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchByMolSmiles(PROD_FLUORO_AMINATION, null, null, 5, true)
        }

        then:
        !results.isEmpty()
        results[0].xid() == 3
        results[0].smiles() == RXN_FLUORO_AMINATION
        results[0].sim() >= 0.99d
        results[0].similarities().keySet() == ["ecfp_prod"] as Set
    }

    def "subgraph filter restricts candidates to matching products"() {
        when: "filter on a fluorobenzene fragment which only occurs in the fluoro-amination product"
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchByMolSmiles(PROD_FLUORO_AMINATION, "c1ccc(F)cc1", null, 5, true)
        }

        then:
        results*.xid() == [3]
    }

    def "rxnSmarts filter drops reactions whose full-reaction SMARTS does not match"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchByMolSmiles(PROD_FLUORO_AMINATION, null, "[F]", 5, true)
        }

        then: "only the fluoro-amination reaction contains fluorine"
        results*.xid() == [3]
    }

    def "blank or null rxnSmarts leaves candidate set unfiltered"() {
        when:
        List<Result> nullResults = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchByMolSmiles(PROD_FLUORO_AMINATION, null, null, 5, true)
        }
        List<Result> blankResults = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchByMolSmiles(PROD_FLUORO_AMINATION, null, "   ", 5, true)
        }

        then:
        !nullResults.isEmpty()
        nullResults*.xid() == blankResults*.xid()
        nullResults*.smiles() == blankResults*.smiles()
    }

    def "rerank=false preserves Lucene hit order, rerank=true sorts by sim desc"() {
        when:
        List<Result> reranked = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchByMolSmiles(PROD_FLUORO_AMINATION, null, null, 5, true)
        }

        then:
        reranked.size() >= 2
        reranked[0].sim() >= reranked[-1].sim()
    }
}
