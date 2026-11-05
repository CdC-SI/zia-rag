package zas.admin.zia.rag.retrieval;

/**
 * Raised when the filters of a retrieval request are invalid (e.g. reserved metadata key).
 */
public class InvalidSearchFilterException extends RuntimeException {

    public InvalidSearchFilterException(String message) {
        super(message);
    }
}
