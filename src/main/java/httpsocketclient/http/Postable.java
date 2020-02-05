package httpsocketclient.http;

import io.vavr.control.Try;

import java.io.File;

public interface Postable {
    enum FileOption {BODY, OUT}

    Response post(Request request) throws RequestError;

    Try<Response> post(Request request, final File body, FileOption fileKind);

    Try<Response> post(Request request, final File body, final File out);
}
