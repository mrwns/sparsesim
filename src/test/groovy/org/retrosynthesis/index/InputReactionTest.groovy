package org.retrosynthesis.index

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class InputReactionTest extends Specification {

    static final Path TRAIN_SMI = Path.of("src/test/resources/u50k_trainrxn.smi")

    def "supplied IDs are kept, separated by spaces or a tab, and may contain spaces"() {
        expect:
        InputReaction.fromLines([
                "CCO>>CC=O US123;1",
                "CCBr.O>>CCO\tR-42",
                "CCN>>CC=N   my reaction",
        ]) == [
                new InputReaction("CCO>>CC=O", "US123;1"),
                new InputReaction("CCBr.O>>CCO", "R-42"),
                new InputReaction("CCN>>CC=N", "my reaction"),
        ]
    }

    def "missing IDs become rxn_<i>, i counting reactions only (not blank or comment lines)"() {
        expect:
        InputReaction.fromLines([
                "# header",
                "CCO>>CC=O",
                "",
                "CCBr.O>>CCO given",
                "  CCN>>CC=N  ",
        ])*.id() == ["rxn_0", "given", "rxn_2"]
    }

    def "duplicate IDs fail with the IDs and their line numbers: #description"() {
        when:
        InputReaction.fromLines(lines)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == message

        where:
        description             | lines                                    | message
        "both supplied"         | ["A>>B x", "C>>D y", "E>>F x"]           | "1 duplicate reaction IDs: x (lines 1, 3)"
        "supplied vs generated" | ["A>>B", "C>>D rxn_0"]                   | "1 duplicate reaction IDs: rxn_0 (lines 1, 2)"
        "several"               | ["A>>B x", "C>>D y", "E>>F x", "G>>H y"] | "2 duplicate reaction IDs: x (lines 1, 3); y (lines 2, 4)"
    }

    def "only the first 10 duplicate IDs are listed"() {
        when:
        InputReaction.fromLines((0..<12).collectMany { ["A>>B d$it".toString(), "C>>D d$it".toString()] })

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("12 duplicate reaction IDs: d0 (lines 1, 2); ")
        e.message.contains("d9 (lines 19, 20)")
        !e.message.contains("d10")
        e.message.endsWith("; ...")
    }

    def "parallel indexing keeps every reaction with its own ID"() {
        given:
        Path dir = Files.createTempDirectory("input-reaction-test")
        def reactions = InputReaction.fromLines(Files.readAllLines(TRAIN_SMI).take(300))
        new ComplexIndexer(dir).withCloseable { it.addAll(reactions) }

        when: "each reaction is looked up by its own SMILES"
        def idsOfExactMatches = new Searcher(dir).withCloseable { searcher ->
            reactions.take(20).collect { r ->
                searcher.searchBySmiles(r.smiles(), 5).findAll { it.smiles() == r.smiles() }*.xid()
            }
        }

        then:
        [reactions.take(20), idsOfExactMatches].transpose().every { r, ids -> r.id() in ids }

        cleanup:
        dir.toFile().deleteDir()
    }
}
