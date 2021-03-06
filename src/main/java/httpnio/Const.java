package httpnio;

public interface Const {
    String HTTPC = "httpc";
    String HTTPFS = "httpfs";
    String CRLF = "\r\n";
    int DEFAULT_PORT = 80;
    int DEFAULT_SERVER_PORT = 8080;
    int DEFAULT_ROUTER_PORT = 3000;
    String DEFAULT_ROUTER_HOST = "localhost";
    int DEFAULT_ROUTER_POST = 3000;
    String DEFAULT_ROUTER_ADDRESS = DEFAULT_ROUTER_HOST + ":" + DEFAULT_ROUTER_POST;
    int TIMEOUT_LIMIT_SECONDS = 1000000000;
    int DEFAULT_THREAD_POOL_SIZE = 2;

    interface Headers {
        String CONTENT_TYPE = "Content-Type";
        String CONTENT_LENGTH = "Content-Length";
        String CONNECTION = "Connection";
        String HOST = "Host";
        String APPLICATION_JSON = "application/json";
        String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    }
}
