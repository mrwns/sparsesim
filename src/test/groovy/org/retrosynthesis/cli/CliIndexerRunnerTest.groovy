package org.retrosynthesis.cli

import org.retrosynthesis.index.Searcher
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class CliIndexerRunnerTest extends Specification {

    static final List<String> TRAIN = Files.readAllLines(Path.of("src/test/resources/u50k_trainrxn.smi"))

    Path dir = Files.createTempDirectory("cli-indexer-test")
    Path smiles = dir.resolve("reactions.smi")
    Path index = dir.resolve("index")

    def cleanup() {
        dir.toFile().deleteDir()
    }

    def "indexing in batches keeps IDs, positions and failures across batch boundaries"() {
        given:
        Files.write(smiles, [
                "# header",
                TRAIN[0],
                "",
                "not-a-smiles bad-1",
                TRAIN[1] + " given-2",
                TRAIN[2],
                "C1CC>>CC",
                TRAIN[3],
        ])

        when:
        def summary = CliIndexerRunner.index(smiles, index, Long.MAX_VALUE, 2)

        then:
        summary.reactions() == 6
        summary.failures() == 2

        and: "failures are logged in input order"
        Files.readAllLines(index.resolve(CliIndexerRunner.ERR_FILE))*.split("\t")*.take(2) == [["bad-1", "not-a-smiles"], ["rxn_4", "C1CC>>CC"]]

        and: "every indexed reaction keeps its ID; generated ones count reactions, not lines"
        new Searcher(index).withCloseable { searcher ->
            [TRAIN[0], TRAIN[1], TRAIN[2], TRAIN[3]].collect { searcher.searchBySmiles(it, 1)[0].xid() }
        } == ["rxn_0", "given-2", "rxn_3", "rxn_5"]
    }

    def "only the first lineLimit lines are indexed"() {
        given:
        Files.write(smiles, TRAIN.take(10))

        when:
        def summary = CliIndexerRunner.index(smiles, index, 4, 3)

        then:
        summary.reactions() == 4
        new Searcher(index).withCloseable { it.numReactions() } == 4
    }

    def "duplicate IDs are reported with their line numbers"() {
        given:
        Files.write(smiles, [TRAIN[0] + " x", TRAIN[1], TRAIN[2] + " x", TRAIN[3] + " rxn_1"])

        when:
        CliIndexerRunner.requireUniqueIds(smiles, Long.MAX_VALUE)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "2 duplicate reaction IDs: x (lines 1, 3); rxn_1 (lines 2, 4)"
    }
}
