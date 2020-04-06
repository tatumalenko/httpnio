package httpnio.common;

public interface ErrorFactory {
    String INVALID_TRANSPORT_PROTOCOL = "Expected transport protocol to be one of UDP or TCP, but received: ";

    String INVALID_APPLICATION_PROTOCOL = "Expected application protocol to be one of HTTP, but received: ";

    static RuntimeException invalidTransportProtocol(final String s) {
        return new IllegalArgumentException(INVALID_TRANSPORT_PROTOCOL + s);
    }

    static RuntimeException invalidApplicationProtocol(final String s) {
        return new IllegalArgumentException(INVALID_APPLICATION_PROTOCOL + s);
    }
}
