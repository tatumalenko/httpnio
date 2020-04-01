package httpnio.client;

import httpnio.server.Response;

public interface Postable {

    Response post(Request request) throws RequestError;
}
