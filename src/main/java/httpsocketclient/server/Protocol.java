package httpsocketclient.server;

import httpsocketclient.client.Request;

import java.io.IOException;

public interface Protocol {

    Response response(Request request) throws IOException;

    Protocol copy() throws IllegalAccessException;
}
