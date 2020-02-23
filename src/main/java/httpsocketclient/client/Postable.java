package httpsocketclient.client;

import httpsocketclient.server.Response;

public interface Postable {

    Response post(Request request) throws RequestError;
}
