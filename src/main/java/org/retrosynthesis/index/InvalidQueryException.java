package org.retrosynthesis.index;

/** A search request the caller got wrong (bad SMILES/SMARTS, invalid knn, ...), as opposed to a server error. */
public class InvalidQueryException extends RuntimeException {

    public InvalidQueryException(String message) {
        super(message);
    }
}
