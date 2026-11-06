package org.retrosynthesis.chem

import org.retrosynthesis.index.ComplexIndexer
import org.retrosynthesis.index.InvalidQueryException
import org.retrosynthesis.index.Result
import org.retrosynthesis.index.Searcher
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class SearcherProductSimTest extends Specification {

    // Three chemically distinct reactions so ranking contributions are clearly attributable.
    static final String RXN_HYDROLYSIS = "CC(C)(C)[O:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12>>[OH:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12"
    static final String RXN_COUPLING = "Br[c:1]1[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1.OB(O)[c:15]1[cH:16][cH:17][c:18](-[n:19]2[cH:20][cH:21][cH:22][n:23]2)[cH:24][cH:25]1>>[c:1]1(-[c:15]2[cH:16][cH:17][c:18](-[n:19]3[cH:20][cH:21][cH:22][n:23]3)[cH:24][cH:25]2)[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1"
    static final String RXN_FLUORO_AMINATION = "[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH:1]=O)[CH:43]=[CH:42]1.[CH2:11]1[CH2:44][CH2:45][NH:46][CH2:47][CH2:48]1>>[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH2:1][N:46]2[CH2:45][CH2:44][CH2:11][CH2:48][CH2:47]2)[CH:43]=[CH:42]1"

    static final double EPS = 1e-9

    static Path tempDir

    def setupSpec() {
        tempDir = Files.createTempDirectory("searcher-productsim-test")

        def indexer = new ComplexIndexer(tempDir)
        indexer.addReaction(RXN_HYDROLYSIS, "1")
        indexer.addReaction(RXN_COUPLING, "2")
        indexer.addReaction(RXN_FLUORO_AMINATION, "3")
        indexer.close()
    }

    def cleanupSpec() {
        tempDir.toFile().deleteDir()
    }

    def "no product weight preserves legacy similarities shape"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, null, 5, true, null, true)
        }

        then:
        !results.isEmpty()
        results.every { it.similarities().containsKey("ecfp4") }
        results.every { it.similarities().containsKey("path_6") }
        results.every { !it.similarities().containsKey("ecfp_prod") }
    }

    def "a product weight surfaces ecfp_prod in similarities for every hit"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, null, 5, true, 0.0d, true)
        }

        then:
        !results.isEmpty()
        results.every { it.similarities().containsKey("ecfp4") }
        results.every { it.similarities().containsKey("path_6") }
        results.every { it.similarities().containsKey("ecfp_prod") }
        results[0].xid() == "3"
    }

    def "product weight 0 keeps sim equal to reaction-only blend"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, null, 5, true, 0.0d, false)
        }

        then:
        !results.isEmpty()
        results.every { r ->
            double expected = (r.similarities().get("ecfp4") + r.similarities().get("path_6")) * 0.5
            Math.abs(r.sim() - expected) < EPS
        }
    }

    def "product weight 1 makes sim equal to ecfp_prod"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, null, 5, true, 1.0d, false)
        }

        then:
        !results.isEmpty()
        results.every { r ->
            Math.abs(r.sim() - r.similarities().get("ecfp_prod")) < EPS
        }
    }

    def "product weight 0.5 blends reaction and product scores"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, null, 5, true, 0.5d, false)
        }

        then:
        !results.isEmpty()
        results.every { r ->
            double reaction = (r.similarities().get("ecfp4") + r.similarities().get("path_6")) * 0.5
            double expected = 0.5d * reaction + 0.5d * r.similarities().get("ecfp_prod")
            Math.abs(r.sim() - expected) < EPS
        }
    }

    def "product weight #weight outside [0, 1] is rejected"() {
        when:
        new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, null, 5, true, weight, false)
        }

        then:
        def e = thrown(InvalidQueryException)
        e.message == "product weight must be between 0 and 1, got $weight"

        where:
        weight << [-0.1d, 1.5d, Double.NaN]
    }

    def "self-match scores ecfp_prod near 1.0"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, null, 5, true, 0.5d, true)
        }

        then:
        results[0].xid() == "3"
        results[0].similarities().get("ecfp_prod") >= 0.99d
    }
}
