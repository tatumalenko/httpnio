package httpsocketclient.http;

public class RequestError extends Exception {
    public RequestError(final String message) {
        super(message);
    }
}
