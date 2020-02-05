package httpsocketclient.http;

public interface Gettable {
    Response get(Request request) throws RequestError;
}
