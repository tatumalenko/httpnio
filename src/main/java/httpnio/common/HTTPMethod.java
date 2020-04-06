package httpnio.common;

public enum HTTPMethod {
    GET,
    POST;

    public static HTTPMethod of(final String value) {
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
