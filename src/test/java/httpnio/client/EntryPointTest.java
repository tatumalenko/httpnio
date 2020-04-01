package httpnio.client;

import httpnio.Util;
import httpnio.cli.Parser;
import httpnio.server.Response;
import io.vavr.control.Try;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EntryPointTest {

    @ParameterizedTest
    @MethodSource("successProvider")
    void execReturnsSuccess(final String in, final Response expected) {
        final var parser = new Parser<>(EntryPoint.class);
        final var ep = parser.parse(in);
        ep
            .onSuccess(either -> {
                var tryResponse = EntryPoint.exec(either.get());
                assertThat(tryResponse).isOfAnyClassIn(Try.Success.class);
                assertThat(tryResponse
                    .onSuccess(response -> {
                        response.headers().remove("Date");
                        expected.headers().remove("Date");
                        assertThat(response.request().toString()).isEqualTo(expected.request().toString());
                        assertThat(response.statusLine()).isEqualTo(expected.statusLine());
                        assertThat(response.statusCode()).isEqualTo(expected.statusCode());
                        assertThat(response.headers()).isEqualTo(expected.headers());
                        assertThat(Util.messageBodyWithoutNonIdempotentHeaders(response.body())).isEqualTo(Util.messageBodyWithoutNonIdempotentHeaders(
                            expected.body()));
                    })
                    .onFailure(failure -> {
                        throw new AssertionError("This should not happen");
                    }));
            })
            .onFailure(failure -> {
                throw new AssertionError("This should not happen");
            });
    }

    static Stream<Arguments> successProvider() throws IOException, RequestError {
        return Stream.of(
            Arguments.of(
                "httpc post -v -h Content-Type: application/json -f " + Util.absolutePathFromRelativePath("in1.txt") + " http://httpbin.org/anything",
                new Response(Request.builder()
                    .method(HttpMethod.POST)
                    .url("http://httpbin.org/anything")
                    .headers(List.of("Content-Type: application/json"))
                    .body("{ \"Assignment\": 1 }")
                    .build(), "\n" +
                    "HTTP/1.1 200 OK\n" +
                    "Date: Sun, 09 Feb 2020 18:15:05 GMT\n" +
                    "Content-Type: application/json\n" +
                    "Content-Length: 398\n" +
                    "Connection: close\n" +
                    "Server: gunicorn/19.9.0\n" +
                    "Access-Control-Allow-Origin: *\n" +
                    "Access-Control-Allow-Credentials: true", "\n\n" +
                    "{\n" +
                    "  \"args\": {}, \n" +
                    "  \"data\": \"{ \\\"Assignment\\\": 1 }\", \n" +
                    "  \"files\": {}, \n" +
                    "  \"form\": {}, \n" +
                    "  \"headers\": {\n" +
                    "    \"Content-Length\": \"19\", \n" +
                    "    \"Content-Type\": \"application/json\", \n" +
                    "    \"Host\": \"httpbin.org\", \n" +
                    "    \"X-Amzn-Trace-Id\": \"Root=1-5e404c29-9c64236b06d0f7ec29712b5f\"\n" +
                    "  }, \n" +
                    "  \"json\": {\n" +
                    "    \"Assignment\": 1\n" +
                    "  }, \n" +
                    "  \"method\": \"POST\", \n" +
                    "  \"origin\": \"147.253.2.73\", \n" +
                    "  \"url\": \"http://httpbin.org/anything\"\n" +
                    "}")
            ),
            Arguments.of(
                "httpc post -v -h Content-Type: application/json -f " + Util.absolutePathFromRelativePath("in1.txt") + " http://httpbin.org/anything",
                new Response(Request.builder()
                    .method(HttpMethod.POST)
                    .url("http://httpbin.org/anything")
                    .headers(List.of("Content-Type: application/json"))
                    .in(Util.absolutePathFromRelativePath("in1.txt"))
                    .build(), "\n" +
                    "HTTP/1.1 200 OK\n" +
                    "Date: Sun, 09 Feb 2020 18:15:05 GMT\n" +
                    "Content-Type: application/json\n" +
                    "Content-Length: 398\n" +
                    "Connection: close\n" +
                    "Server: gunicorn/19.9.0\n" +
                    "Access-Control-Allow-Origin: *\n" +
                    "Access-Control-Allow-Credentials: true", "\n\n" +
                    "{\n" +
                    "  \"args\": {}, \n" +
                    "  \"data\": \"{ \\\"Assignment\\\": 1 }\", \n" +
                    "  \"files\": {}, \n" +
                    "  \"form\": {}, \n" +
                    "  \"headers\": {\n" +
                    "    \"Content-Length\": \"19\", \n" +
                    "    \"Content-Type\": \"application/json\", \n" +
                    "    \"Host\": \"httpbin.org\", \n" +
                    "    \"X-Amzn-Trace-Id\": \"Root=1-5e404c29-9c64236b06d0f7ec29712b5f\"\n" +
                    "  }, \n" +
                    "  \"json\": {\n" +
                    "    \"Assignment\": 1\n" +
                    "  }, \n" +
                    "  \"method\": \"POST\", \n" +
                    "  \"origin\": \"147.253.2.73\", \n" +
                    "  \"url\": \"http://httpbin.org/anything\"\n" +
                    "}")
            ),
            Arguments.of(
                "httpc post -v -h Content-Type: application/json -d { \"Assignment\" : { \"SubObject\": \"Quoted'Character\" }} http://httpbin.org/anything",
                new Response(Request.builder()
                    .method(HttpMethod.POST)
                    .url("http://httpbin.org/anything")
                    .headers(List.of("Content-Type: application/json"))
                    .body("{ \"Assignment\" : { \"SubObject\": \"Quoted'Character\" }}")
                    .build(), "\n" +
                    "HTTP/1.1 200 OK\n" +
                    "Date: Sun, 09 Feb 2020 20:23:27 GMT\n" +
                    "Content-Type: application/json\n" +
                    "Content-Length: 480\n" +
                    "Connection: close\n" +
                    "Server: gunicorn/19.9.0\n" +
                    "Access-Control-Allow-Origin: *\n" +
                    "Access-Control-Allow-Credentials: true", "\n" +
                    "\n" +
                    "{\n" +
                    "  \"args\": {}, \n" +
                    "  \"data\": \"{ \\\"Assignment\\\" : { \\\"SubObject\\\": \\\"Quoted'Character\\\" }}\", \n" +
                    "  \"files\": {}, \n" +
                    "  \"form\": {}, \n" +
                    "  \"headers\": {\n" +
                    "    \"Content-Length\": \"53\", \n" +
                    "    \"Content-Type\": \"application/json\", \n" +
                    "    \"Host\": \"httpbin.org\", \n" +
                    "  }, \n" +
                    "  \"json\": {\n" +
                    "    \"Assignment\": {\n" +
                    "      \"SubObject\": \"Quoted'Character\"\n" +
                    "    }\n" +
                    "  }, \n" +
                    "  \"method\": \"POST\", \n" +
                    "  \"origin\": \"147.253.2.73\", \n" +
                    "  \"url\": \"http://httpbin.org/anything\"\n" +
                    "}")
            ),
            Arguments.of(
                "httpc post -v -h Content-Type: application/json -f " + Util.absolutePathFromRelativePath("in2.txt") + " http://httpbin.org/anything",
                new Response(Request.builder()
                    .method(HttpMethod.POST)
                    .url("http://httpbin.org/anything")
                    .headers(List.of("Content-Type: application/json"))
                    .body("url = https://www.google.com")
                    .build(), "\n" +
                    "HTTP/1.1 200 OK\n" +
                    "Date: Sun, 09 Feb 2020 18:15:05 GMT\n" +
                    "Content-Type: application/json\n" +
                    "Content-Length: 384\n" +
                    "Connection: close\n" +
                    "Server: gunicorn/19.9.0\n" +
                    "Access-Control-Allow-Origin: *\n" +
                    "Access-Control-Allow-Credentials: true", "\n\n" +
                    "{\n" +
                    "  \"args\": {}, \n" +
                    "  \"data\": \"url = https://www.google.com\", \n" +
                    "  \"files\": {}, \n" +
                    "  \"form\": {}, \n" +
                    "  \"headers\": {\n" +
                    "    \"Content-Length\": \"28\", \n" +
                    "    \"Content-Type\": \"application/json\", \n" +
                    "    \"Host\": \"httpbin.org\", \n" +
                    "    \"X-Amzn-Trace-Id\": \"Root=1-5e404c29-9c64236b06d0f7ec29712b5f\"\n" +
                    "  }, \n" +
                    "  \"json\": null, \n" +
                    "  \"method\": \"POST\", \n" +
                    "  \"origin\": \"147.253.2.73\", \n" +
                    "  \"url\": \"http://httpbin.org/anything\"\n" +
                    "}")
            )
        );
    }

    @ParameterizedTest
    @MethodSource("failureProvider")
    void execReturnsFailure(final String in, final String cause) {
        final var parser = new Parser<>(EntryPoint.class);
        final var ep = parser.parse(in);
        ep
            .onSuccess(either -> {
                var tryResponse = EntryPoint.exec(either.get());
                assertThat(tryResponse).isOfAnyClassIn(Try.Failure.class);
                assertThat(tryResponse
                    .onSuccess(response -> {
                        throw new AssertionError("This should not happen");
                    })
                    .onFailure(failure -> {
                        assertThat(failure.getMessage()).isEqualTo(cause);
                    }));
            })
            .onFailure(failure -> {
                throw new AssertionError("This should not happen");
            });
    }

    static Stream<Arguments> failureProvider() {
        return Stream.of(
            Arguments.of(
                "httpc get --header Content-Type:application/json --header Content-Type:application/html http://postman-echo.com/get?foo1=bar1&foo2=bar2",
                "Something went wrong while processing the request. RequestError: Error occurred while parsing the header: Content-Type:application/html. A duplicate key was found: Content-Type."),
            Arguments.of(
                "httpc post -v -h Content-Type: application/json -f /bad/path/invalidFile.txt http://httpbin.org/anything",
                "Something went wrong while trying to read the contents of the input file. NoSuchFileException: /bad/path/invalidFile.txt"
            )
        );
    }
}
