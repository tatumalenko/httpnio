package httpsocketclient.client;

public interface Postable {

    Response post(Request request) throws RequestError;
}
