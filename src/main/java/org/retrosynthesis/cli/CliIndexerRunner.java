package org.retrosynthesis.cli;

import org.retrosynthesis.index.ComplexIndexer;
import org.retrosynthesis.index.IndexingFailure;
import org.retrosynthesis.index.InputReaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
@Profile("indexer")
public class CliIndexerRunner implements ApplicationRunner {

    /** Reactions that could not be indexed, written inside the index directory. */
    static final String ERR_FILE = "index.err";

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

        if (limitProp <=0 ) {
            limitProp = Long.MAX_VALUE;
        }

        List<String> lines;
        try (var stream = Files.lines(smilesFile)) {
            lines = stream.limit(limitProp).toList();
        }

        // Validate every ID before touching the index, so a bad input file leaves nothing behind.
        List<InputReaction> reactions;
        try {
            reactions = InputReaction.fromLines(lines);
        } catch (IllegalArgumentException e) {
            System.err.println(smilesFile + ": " + e.getMessage());
            System.exit(2);
            return;
        }

        Files.createDirectories(indexPath);

        long start = System.currentTimeMillis();
        List<IndexingFailure> failures;
        try (ComplexIndexer indexer = new ComplexIndexer(indexPath)) {
            failures = indexer.addAll(reactions);
        }
        Path errFile = indexPath.resolve(ERR_FILE);
        IndexingFailure.writeTsv(errFile, failures);
        long end = System.currentTimeMillis();
        System.out.println("Indexed " + (reactions.size() - failures.size()) + " of " + reactions.size() + " reactions to "
                + indexPath + " in " + (end - start)/1000 + " s; " + failures.size() + " failed"
                + (failures.isEmpty() ? "" : ", see " + errFile));

        // Exit after CLI run to avoid starting the web server in this mode
        System.exit(0);
    }
}


