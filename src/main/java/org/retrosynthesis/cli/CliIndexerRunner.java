package org.retrosynthesis.cli;

import org.retrosynthesis.index.ComplexIndexer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Profile("indexer")
public class CliIndexerRunner implements ApplicationRunner {

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

        Files.createDirectories(indexPath);

        if (limitProp <=0 ) {
            limitProp = Long.MAX_VALUE;
        }

        List<String> smilesList = Files.lines(smilesFile)
                .map(String::trim)
                .limit(limitProp)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.startsWith("#"))
                .collect(Collectors.toList());

        long start = System.currentTimeMillis();
        try (ComplexIndexer indexer = new ComplexIndexer(indexPath)) {
            indexer.addAll(smilesList);
        }
        long end = System.currentTimeMillis();
        System.out.println("Indexed " + smilesList.size() + " reactions to " + indexPath + " in " + (end - start)/1000 + " s");

        // Exit after CLI run to avoid starting the web server in this mode
        System.exit(0);
    }
}


