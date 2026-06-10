package org.example.aiscanner_server.common;

/**
 * Thrown when the AI analysis service is unreachable or returns an error,
 * after all retry attempts have been exhausted.
 */
public class AiServiceException extends RuntimeException {

    private final int attempts;

    public AiServiceException(String message, Throwable cause, int attempts) {
        super(message, cause);
        this.attempts = attempts;
    }

    public int getAttempts() { return attempts; }
}
