package httpsocketclient.cli;

public class ParseError extends Exception {
    public ParseError(final String message) {
        super(message);
    }

    public ParseError(final String message, final Throwable cause) {
        super(message, cause);
    }
}
