package httpnio.server;

import httpnio.common.HTTPRequest;
import httpnio.common.HTTPResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
public class HTTPResponseTests {
    @Test
    void invalidResponseBodyLength() {
        final var rawResponse = "HTTP/1.1 200 OK\n" +
            "Accept: */*\n" +
            "Content-Length: 804\n" +
            "\n" +
            "File contents successfully written to: /some/hello.txt";

        final var response = HTTPResponse.of(null, rawResponse);

        assertThat(response).isNull();
    }

    @Test
    void validResponseBodyLength() {
        final var rawResponse = "HTTP/1.1 200 OK\n" +
            "Accept: */*\n" +
            "Content-Length: 54\n" +
            "\n" +
            "File contents successfully written to: /some/hello.txt";

        final var response = HTTPResponse.of(null, rawResponse);

        assertThat(response).isNotNull();
    }

    @Test
    void validRequestBodyLength() throws HTTPRequest.RequestError {
        final var rawRequest = "POST /some/hello.txt HTTP/1.0\n" +
            "Host: localhost\n" +
            "Content-Type: application/json\n" +
            "Content-Length: 2184\n" +
            "\n" +
            "hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello! hello!";

        final var request = HTTPRequest.of(rawRequest);

        assertThat(request).isNull();
    }
}
