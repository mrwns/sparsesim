package org.retrosynthesis.index;

import java.util.Map;

public record Result(String smiles, double sim, int xid, Map<String, Double> similarities) {

}
