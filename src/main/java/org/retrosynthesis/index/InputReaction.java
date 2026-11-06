package org.retrosynthesis.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;

public record InputReaction(String smiles, String id) {

    private static final int MAX_REPORTED_DUPLICATES = 10;

    /**
     * Parses reaction file lines of the form {@code SMILES [ID]}, the ID separated by whitespace. Blank lines and
     * {@code #} comments are skipped. A line without an ID gets {@code rxn_<i>}, {@code i} being its 0-based position
     * among the reactions. Reactions are passed to {@code action} one at a time, in order, with their 1-based line
     * number, so a file never has to be held in memory.
     */
    public static void forEach(Iterator<String> lines, ObjIntConsumer<InputReaction> action) {
        int lineNumber = 0;
        int position = 0;
        while (lines.hasNext()) {
            lineNumber++;
            String line = lines.next().trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            String id = parts.length == 2 ? parts[1] : "rxn_" + position;
            position++;
            action.accept(new InputReaction(parts[0], id), lineNumber);
        }
    }

    /**
     * All reactions of {@code lines}, see {@link #forEach}.
     *
     * @throws IllegalArgumentException if any ID, supplied or generated, occurs more than once
     */
    public static List<InputReaction> fromLines(List<String> lines) {
        List<InputReaction> reactions = new ArrayList<>();
        UniqueIds ids = new UniqueIds();
        forEach(lines.iterator(), (reaction, lineNumber) -> {
            reactions.add(reaction);
            ids.add(reaction.id(), lineNumber);
        });
        ids.requireUnique();
        return reactions;
    }

    /** Collects reaction IDs with their line numbers, to report any duplicates. */
    public static final class UniqueIds {

        private final Map<String, Integer> firstLineById = new HashMap<>();
        private final Map<String, List<Integer>> duplicateLinesById = new LinkedHashMap<>();

        public void add(String id, int lineNumber) {
            Integer firstLine = firstLineById.putIfAbsent(id, lineNumber);
            if (firstLine != null) {
                duplicateLinesById.computeIfAbsent(id, k -> new ArrayList<>(List.of(firstLine))).add(lineNumber);
            }
        }

        /** @throws IllegalArgumentException listing the duplicate IDs and their line numbers, if there are any */
        public void requireUnique() {
            List<String> duplicates = duplicateLinesById.entrySet().stream()
                    .map(e -> e.getKey() + " (lines " + e.getValue().stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")")
                    .toList();
            if (!duplicates.isEmpty()) {
                String shown = String.join("; ", duplicates.subList(0, Math.min(duplicates.size(), MAX_REPORTED_DUPLICATES)));
                String more = duplicates.size() > MAX_REPORTED_DUPLICATES ? "; ..." : "";
                throw new IllegalArgumentException(duplicates.size() + " duplicate reaction IDs: " + shown + more);
            }
        }
    }
}
