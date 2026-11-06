package org.retrosynthesis.index

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class SearcherTest extends Specification {

    static final List<String> TRAIN = Files.readAllLines(Path.of("src/test/resources/u50k_trainrxn.smi"))

    def "an index searches the same with preload=#preload"() {
        given:
        Path dir = Files.createTempDirectory("searcher-test")
        new ComplexIndexer(dir).withCloseable { it.addAll(InputReaction.fromLines(TRAIN.take(50))) }

        expect:
        new Searcher(dir, preload).withCloseable { it.searchBySmiles(TRAIN[3], 1)*.xid() } == ["rxn_3"]

        cleanup:
        dir.toFile().deleteDir()

        where:
        preload << [true, false]
    }
}
