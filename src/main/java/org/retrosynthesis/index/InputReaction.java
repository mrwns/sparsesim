package org.retrosynthesis.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record InputReaction(String smiles, String id) {

    private static final int MAX_REPORTED_DUPLICATES = 10;

    /**
     * Parses reaction file lines of the form {@code SMILES [ID]}, the ID separated by whitespace. Blank lines and
     * {@code #} comments are skipped. A line without an ID gets {@code rxn_<i>}, {@code i} being its 0-based position
     * among the reactions.
     *
     * @throws IllegalArgumentException if any ID, supplied or generated, occurs more than once
     */
    public static List<InputReaction> fromLines(List<String> lines) {
        List<InputReaction> reactions = new ArrayList<>();
        Map<String, List<Integer>> lineNumbersById = new LinkedHashMap<>();
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            String id = parts.length == 2 ? parts[1] : "rxn_" + reactions.size();
            reactions.add(new InputReaction(parts[0], id));
            lineNumbersById.computeIfAbsent(id, k -> new ArrayList<>()).add(n + 1);
        }
        requireUniqueIds(lineNumbersById);
        return reactions;
    }

    private static void requireUniqueIds(Map<String, List<Integer>> lineNumbersById) {
        List<String> duplicates = lineNumbersById.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " (lines " + e.getValue().stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")")
                .toList();
        if (!duplicates.isEmpty()) {
            String shown = String.join("; ", duplicates.subList(0, Math.min(duplicates.size(), MAX_REPORTED_DUPLICATES)));
            String more = duplicates.size() > MAX_REPORTED_DUPLICATES ? "; ..." : "";
            throw new IllegalArgumentException(duplicates.size() + " duplicate reaction IDs: " + shown + more);
        }
    }
}
