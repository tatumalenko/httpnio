package httpnio.client;

import httpnio.server.Response;

public interface Gettable {
    Response get(Request request) throws RequestError;
}
