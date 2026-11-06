package org.retrosynthesis.index

import org.apache.lucene.codecs.lucene90.Lucene90StoredFieldsFormat
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.SegmentReader
import org.apache.lucene.store.FSDirectory
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class IndexingFailureTest extends Specification {

    static final List<String> TRAIN = Files.readAllLines(Path.of("src/test/resources/u50k_trainrxn.smi"))

    Path dir = Files.createTempDirectory("indexing-failure-test")

    def cleanup() {
        dir.toFile().deleteDir()
    }

    def "unparseable reactions are skipped and returned in input order; the rest is indexed"() {
        given:
        def reactions = InputReaction.fromLines([
                TRAIN[0] + " good-0",
                "not-a-smiles bad-1",
                TRAIN[1] + " good-2",
                "C1CC>>CC bad-3",
                TRAIN[2] + " good-4",
        ])

        when:
        def failures = new ComplexIndexer(dir).withCloseable { it.addAll(reactions) }

        then:
        failures*.reaction()*.id() == ["bad-1", "bad-3"]
        failures*.reaction()*.smiles() == ["not-a-smiles", "C1CC>>CC"]
        failures.every { it.error().startsWith("org.openscience.cdk.exception.InvalidSmilesException: ") && !it.error().contains("\n") }

        and:
        new Searcher(dir).withCloseable { it.numReactions() } == 3
    }

    def "writeTsv writes ID, SMILES and error per line and replaces an existing file"() {
        given:
        Path file = dir.resolve("index.err")
        Files.writeString(file, "stale content\n")
        def failures = [new IndexingFailure(new InputReaction("C1CC>>CC", "bad-3"), "boom: unclosed ring")]

        when:
        IndexingFailure.writeTsv(file, failures)

        then:
        Files.readAllLines(file) == ["bad-3\tC1CC>>CC\tboom: unclosed ring"]

        when: "there are no failures"
        IndexingFailure.writeTsv(file, [])

        then: "the file is emptied rather than left stale"
        Files.readAllLines(file).isEmpty()
    }

    def "new indexes store reactions with BEST_COMPRESSION"() {
        given:
        new ComplexIndexer(dir).withCloseable { it.addAll(InputReaction.fromLines(TRAIN.take(50))) }

        when:
        def modes = DirectoryReader.open(FSDirectory.open(dir)).withCloseable { reader ->
            reader.leaves().collect { (it.reader() as SegmentReader).segmentInfo.info.getAttribute(Lucene90StoredFieldsFormat.MODE_KEY) }
        }

        then:
        !modes.isEmpty()
        modes.every { it == "BEST_COMPRESSION" }
    }

    def "an index directory containing the err file still opens and searches"() {
        given:
        new ComplexIndexer(dir).withCloseable { it.addAll(InputReaction.fromLines([TRAIN[0]])) }
        IndexingFailure.writeTsv(dir.resolve("index.err"), [])

        expect:
        new Searcher(dir).withCloseable { it.searchBySmiles(TRAIN[0], 1)*.xid() } == ["rxn_0"]
    }
}
