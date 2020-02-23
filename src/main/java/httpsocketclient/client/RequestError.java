package httpsocketclient.client;

public class RequestError extends Exception {
    public RequestError(final String message) {
        super(message);
    }
}
