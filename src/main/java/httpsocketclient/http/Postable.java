package httpsocketclient.http;

public interface Postable {

    Response post(Request request) throws RequestError;
}
