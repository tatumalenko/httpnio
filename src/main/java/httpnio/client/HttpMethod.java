package httpsocketclient.client;

public enum HttpMethod {
    GET,
    POST;

    public static HttpMethod of(final String value) {
        switch (value) {
            case "GET":
            case "get":
                return GET;
            case "POST":
            case "post":
                return POST;
            default:
                throw new IllegalArgumentException("Invalid http method specified: " + value);
        }
    }
}
