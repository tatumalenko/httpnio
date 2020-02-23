package httpsocketclient.client;

import httpsocketclient.server.Response;

public interface Gettable {
    Response get(Request request) throws RequestError;
}
