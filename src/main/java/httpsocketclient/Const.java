package httpsocketclient;

public interface Const {
    String HTTPC = "httpc";
    String HTTPFS = "httpfs";
    String CRLF = "\r\n";
    int DEFAULT_PORT = 80;
    int TIMEOUT_LIMIT_SECONDS = 1000000000;

    interface Headers {
        String CONTENT_TYPE = "Content-Type";
        String CONTENT_LENGTH = "Content-Length";
        String HOST = "Host";
        String APPLICATION_JSON = "application/json";
        String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    }
}
