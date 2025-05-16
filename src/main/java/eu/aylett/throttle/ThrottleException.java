package eu.aylett.throttle;

public class ThrottleException extends RuntimeException {
  public ThrottleException(String message) {
    super(message);
  }
}
