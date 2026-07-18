package allocation.api.error;

/**
 * Simple JSON error response returned for invalid API requests.
 */
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path
) {
}
