package org.retrosynthesis.index;

import java.util.Map;

public record Result(String smiles, double sim, String xid, Map<String, Double> similarities) {

}
