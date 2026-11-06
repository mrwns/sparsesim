package org.retrosynthesis.index;

/** Limits the search APIs (REST and MCP) put on requests, on top of what {@link Searcher} itself accepts. */
public final class SearchLimits {

    public static final int DEFAULT_KNN = 10;
    public static final int MAX_KNN = 1000;

    private SearchLimits() {
    }

    public static int requireKnnWithinLimit(int knn) {
        if (knn > MAX_KNN) {
            throw new InvalidQueryException("knn must be at most " + MAX_KNN + ", got " + knn);
        }
        return knn;
    }
}
