package httpsocketclient.client;

public interface Gettable {
    Response get(Request request) throws RequestError;
}
