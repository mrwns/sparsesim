package org.retrosynthesis.cli;

import org.retrosynthesis.index.ComplexIndexer;
import org.retrosynthesis.index.IndexingFailure;
import org.retrosynthesis.index.InputReaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.stream.Stream;

@Component
@Profile("indexer")
public class CliIndexerRunner implements ApplicationRunner {

    /** Reactions that could not be indexed, written inside the index directory. */
    static final String ERR_FILE = "index.err";

    /** Reactions held in memory at a time while indexing. */
    static final int BATCH_SIZE = 10_000;

    @Value("${smiles:}")
    private String smilesFileProp;

    @Value("${indexdir:}")
    private String indexDirProp;

    // Limit the number of reactions to index
    @Value("${limit:-1}")
    private long limitProp;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // If CLI properties are not provided, do nothing; the REST app will start normally.
        if (smilesFileProp == null || smilesFileProp.isBlank() || indexDirProp == null || indexDirProp.isBlank()) {
            System.err.println("Argument error: SMILES file and index directory must be provided");
            System.exit(2);
        }

        Path smilesFile = Path.of(smilesFileProp);
        Path indexPath = Path.of(indexDirProp);

        if (!Files.exists(smilesFile)) {
            System.err.println("SMILES file not found: " + smilesFile);
            System.exit(2);
        }

        long limit = limitProp <= 0 ? Long.MAX_VALUE : limitProp;
        // Validate every ID before touching the index, so a bad input file leaves nothing behind.
        try {
            requireUniqueIds(smilesFile, limit);
        } catch (IllegalArgumentException e) {
            System.err.println(smilesFile + ": " + e.getMessage());
            System.exit(2);
            return;
        }
        Summary summary = index(smilesFile, indexPath, limit, BATCH_SIZE);
        System.out.println("Indexed " + (summary.reactions() - summary.failures()) + " of " + summary.reactions()
                + " reactions to " + indexPath + " in " + summary.seconds() + " s; " + summary.failures() + " failed"
                + (summary.failures() == 0 ? "" : ", see " + indexPath.resolve(ERR_FILE)));

        // Exit after CLI run to avoid starting the web server in this mode
        System.exit(0);
    }

    record Summary(int reactions, int failures, long seconds) {
    }

    /**
     * Reads the first {@code lineLimit} lines of {@code smilesFile} one at a time, keeping only the reaction IDs.
     *
     * @throws IllegalArgumentException if any reaction ID occurs more than once
     */
    static void requireUniqueIds(Path smilesFile, long lineLimit) throws IOException {
        InputReaction.UniqueIds ids = new InputReaction.UniqueIds();
        forEachReaction(smilesFile, lineLimit, (reaction, lineNumber) -> ids.add(reaction.id(), lineNumber));
        ids.requireUnique();
    }

    /**
     * Indexes the first {@code lineLimit} lines of {@code smilesFile} into {@code indexPath}, reading the file one
     * line at a time and indexing {@code batchSize} reactions at a time, so it never has to be held in memory. IDs
     * must already be unique ({@link #requireUniqueIds}). Reactions that cannot be indexed are listed in
     * {@value #ERR_FILE}.
     */
    static Summary index(Path smilesFile, Path indexPath, long lineLimit, int batchSize) throws Exception {
        Files.createDirectories(indexPath);
        long start = System.currentTimeMillis();
        List<IndexingFailure> failures = new ArrayList<>();
        int[] reactions = {0};
        try (ComplexIndexer indexer = new ComplexIndexer(indexPath)) {
            List<InputReaction> batch = new ArrayList<>(batchSize);
            forEachReaction(smilesFile, lineLimit, (reaction, lineNumber) -> {
                reactions[0]++;
                batch.add(reaction);
                if (batch.size() == batchSize) {
                    failures.addAll(indexer.add(batch));
                    batch.clear();
                }
            });
            failures.addAll(indexer.add(batch));
            indexer.commit();
        }
        IndexingFailure.writeTsv(indexPath.resolve(ERR_FILE), failures);
        return new Summary(reactions[0], failures.size(), (System.currentTimeMillis() - start) / 1000);
    }

    private static void forEachReaction(Path smilesFile, long lineLimit, ObjIntConsumer<InputReaction> action) throws IOException {
        try (Stream<String> lines = Files.lines(smilesFile)) {
            InputReaction.forEach(lines.limit(lineLimit).iterator(), action);
        }
    }
}
