package org.retrosynthesis.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A reaction the indexer skipped because it could not be parsed or fingerprinted. */
public record IndexingFailure(InputReaction reaction, String error) {

    static IndexingFailure of(InputReaction reaction, Exception e) {
        return new IndexingFailure(reaction, e.toString().replaceAll("\\s+", " ").trim());
    }

    /** Writes one {@code ID<TAB>SMILES<TAB>error} line per failure, replacing the file if it exists. */
    public static void writeTsv(Path file, List<IndexingFailure> failures) throws IOException {
        Files.write(file, failures.stream()
                .map(f -> f.reaction().id() + "\t" + f.reaction().smiles() + "\t" + f.error())
                .toList());
    }
}
