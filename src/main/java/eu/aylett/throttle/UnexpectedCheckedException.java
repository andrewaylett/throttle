package eu.aylett.throttle;

public class UnexpectedCheckedException extends RuntimeException {
    public UnexpectedCheckedException(Throwable cause) {
        super(cause);
    }
}
