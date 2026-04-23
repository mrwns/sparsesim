package org.retrosynthesis.chem;
import java.util.HashMap;
import java.util.Map;

public class FingerprintUtil {
    
    public static String countsToCsv(Map<Integer, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (!first) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
    
    public static Map<Integer, Integer> parseCsv(String csv) {
        Map<Integer, Integer> counts = new HashMap<>();
        if (csv == null || csv.trim().isEmpty()) {
            return counts;
        }
        
        String[] pairs = csv.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                try {
                    int hash = Integer.parseInt(parts[0]);
                    int count = Integer.parseInt(parts[1]);
                    counts.put(hash, count);
                } catch (NumberFormatException e) {
                    // Skip invalid entries
                }
            }
        }
        return counts;
    }
    
    public static long l1(Map<Integer, Integer> counts) {
        return counts.values().stream().mapToLong(Math::abs).sum();
    }

    public static long l2(Map<Integer, Integer> counts) {
        return counts.values().stream().mapToLong(x -> x * x).sum();
    }
}
