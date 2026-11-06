package org.retrosynthesis.chem

import org.retrosynthesis.index.ComplexIndexer
import org.retrosynthesis.index.Result
import org.retrosynthesis.index.Searcher
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class SearcherSmartsTest extends Specification {

    static final String RXN_HYDROLYSIS = "CC(C)(C)[O:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12>>[OH:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12"
    static final String RXN_COUPLING = "Br[c:1]1[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1.OB(O)[c:15]1[cH:16][cH:17][c:18](-[n:19]2[cH:20][cH:21][cH:22][n:23]2)[cH:24][cH:25]1>>[c:1]1(-[c:15]2[cH:16][cH:17][c:18](-[n:19]3[cH:20][cH:21][cH:22][n:23]3)[cH:24][cH:25]2)[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1"
    static final String RXN_FLUORO_AMINATION = "[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH:1]=O)[CH:43]=[CH:42]1.[CH2:11]1[CH2:44][CH2:45][NH:46][CH2:47][CH2:48]1>>[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH2:1][N:46]2[CH2:45][CH2:44][CH2:11][CH2:48][CH2:47]2)[CH:43]=[CH:42]1"

    static Path tempDir

    def setupSpec() {
        tempDir = Files.createTempDirectory("searcher-smarts-test")

        def indexer = new ComplexIndexer(tempDir)
        indexer.addReaction(RXN_HYDROLYSIS, "1")
        indexer.addReaction(RXN_COUPLING, "2")
        indexer.addReaction(RXN_FLUORO_AMINATION, "3")
        indexer.close()
    }

    def cleanupSpec() {
        tempDir.toFile().deleteDir()
    }

    def "rxnSmarts filters surviving search hits"() {
        when:
        List<Result> results = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, "[F]", 5, true, null, true)
        }

        then:
        results.size() == 1
        results.size() < 5
        results*.xid == ["3"]
        results*.smiles == [RXN_FLUORO_AMINATION]
    }

    def "blank or null rxnSmarts leaves search results unfiltered"() {
        when:
        List<Result> nullResults = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, null, 5, true, null, true)
        }
        List<Result> blankResults = new Searcher(tempDir).withCloseable { searcher ->
            searcher.searchBySmiles(RXN_FLUORO_AMINATION, null, "   ", 5, true, null, true)
        }

        then:
        nullResults*.xid == ["3", "2"]
        blankResults*.xid == nullResults*.xid
        blankResults*.smiles == nullResults*.smiles
    }
}
