package httpnio.client;

import httpnio.Const;
import httpnio.cli.*;
import httpnio.common.HTTPMethod;
import httpnio.common.HTTPRequest;
import httpnio.common.HTTPResponse;
import httpnio.common.TransportProtocol;
import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Command(name = Const.HTTPC, description = "httpc is a curl-like application but supports HTTP protocol only.")
public class EntryPoint {
    @SubCommand(
        name = "get",
        argument = @Argument(
            name = "url",
            format = "https://some-website.ca",
            regex = "(^(https://)?\\S+$)",
            description = "The URL of the request."),
        description = "Get executes a HTTP GET request for a given URL.")
    String get;

    @SubCommand(
        name = "post",
        argument = @Argument(
            name = "url",
            format = "https://some-website.ca",
            regex = "(^(https://)?\\S+$)",
            description = "The URL of the request."),
        description = "Post executes a HTTP POST request for a given URL with inline data or from file.")
    String post;

    @Flag(
        name = "verbose",
        alias = {"--verbose", "-v"},
        required = false,
        subCommands = {"get", "post"},
        description = "Prints the detail of the response such as protocol, status, and headers.")
    boolean verbose;

    @Flag(
        name = "udp",
        alias = {"--udp"},
        required = false,
        subCommands = {"get", "post"},
        description = "Uses a UDP Selective Repeat protocol instead of the default TCP protocol.")
    boolean udp;

    @Option(
        name = "headers",
        alias = {"--header", "-h"},
        argument = @Argument(
            name = "header",
            format = "key:value",
            regex = "(^[^\\s\\:]+\\s*:\\s*[^\\s\\:]+$)",
            description = ""),
        subCommands = {"get", "post"},
        description = "Associates headers to HTTP Request with the format 'key:value'")
    List<String> headers;

    @Option(
        name = "data",
        alias = {"--data", "-d"},
        argument = @Argument(name = "data",
            format = "{\"prop\": \"value\"}",
            regex = "(.*)", //""(^\\{(\\s*,?\\s*\\S+:\\s*\\S+\\s*)+\\}$)",
            description = ""),
        subCommands = {"post"},
        description = "Associates an inline data to the body HTTP POST request.")
    String data;

    @Option(
        name = "in",
        alias = {"--file", "-f"},
        argument = @Argument(name = "file",
            format = "/file/to/body",
            regex = "(^.*$)",
            description = ""),
        subCommands = {"post"},
        description = "Associates the content of a file to the body HTTP POST request.")
    String in;

    @Option(
        name = "out",
        alias = {"--out", "-o"},
        argument = @Argument(name = "out",
            format = "/file/to/output",
            regex = "(^\\/[\\w\\W]+\\.txt$)",
            description = ""),
        subCommands = {"get", "post"},
        description = "Outputs the response of the HTTP request to a file.")
    String out;

    @Option(
        name = "path",
        alias = {"--path", "-p"},
        argument = @Argument(name = "path",
            format = "/some/path.txt",
            regex = "(.*)",
            description = ""),
        subCommands = {"get", "post"},
        description = "Associates a path to the request when UDP is used.")
    String path;

    public static void entryPoint(final String[] args) {
        final Parser<EntryPoint> parser = new Parser<>(EntryPoint.class);
        final Try<Either<String, EntryPoint>> result = parser.parse(String.join(" ", args));

        result
            .onSuccess(success -> {
                if (success.isLeft()) {
                    System.out.println(success.getLeft());
                }

                if (success.isRight()) {
                    exec(success.get())
                        .onSuccess(response -> {
                            var whatToPrint = "\n";
                            if (success.get().verbose) {
                                whatToPrint += response != null && response.request() != null ? response.request() + "\n" : "";
                                whatToPrint += response != null && response.request() != null ? response.messageHeader() : "\n\n";
                            }
                            whatToPrint += response != null && response.request() != null ? "\n\n" + response.body() : "\n";
                            if (success.get().out != null && response != null) {
                                var whatToPrintInBytes = whatToPrint.getBytes();
                                Try.of(() -> Files.write(Paths.get(success.get().out), whatToPrintInBytes))
                                    .onSuccess(nothing -> System.out.println("Output saved in " + success.get().out))
                                    .onFailure(failure -> System.out.println(
                                        "Something went wrong trying to save the contents of the response to the file. " + failure.getClass()
                                            .getSimpleName() + ": " + failure.getMessage()));
                            } else {
                                System.out.println(whatToPrint);
                            }
                        })
                        .onFailure(failure -> {
                            System.err.println(failure.getMessage());
                            System.err.println(parser.help());
                            System.exit(1);
                        });
                }
            })
            .onFailure(failure -> {
                System.err.println(failure.getMessage());
                System.err.println(parser.help());
                System.exit(1);
            });
    }

    static Try<HTTPResponse> exec(final EntryPoint ep) {
        try {
            if (ep.get == null && ep.post == null) {
                throw new ParseError("At least one of the possible sub-commands and a url must be specified.");
            }

            var host = ep.get != null ? ep.get : ep.post;

            if (ep.udp) {
                if (host.startsWith("http")) {
                    throw new ParseError("Invalid host url, do not specify an http:// prefix in UDP mode. Use the host address with " +
                        "port such as 'localhost:8007'");
                }
            } else {
                if (ep.path != null) {
                    throw new ParseError("Invalid option specified. Do not specify the '-p'/'--path' option while in TCP mode. Use the " +
                        "host path instead, such as 'http://localhost:80'");
                }

                if (!host.startsWith("http://") || !host.startsWith("https://")) {
                    host = "http://" + host;
                }
            }

            if (ep.get != null && ep.in != null) {
                throw new ParseError("Only specify both the '-f'/'--file' option when making a GET request.");
            }

            final var request = HTTPRequest.builder()
                .method(ep.get != null ? HTTPMethod.GET : HTTPMethod.POST)
                .url(host)
                .headers(ep.headers)
                .body(ep.data)
                .in(ep.in)
                .out(ep.out)
                .path(ep.path)
                .build();

            return Try.of(() -> new Client(TransportProtocol.of(TransportProtocol.Type.of(ep.udp ? "UDP" : "TCP"))).request(request));
        } catch (final ParseError e) {
            return Try.failure(e);
        } catch (final MalformedURLException e) {
            return Try.failure(new HTTPRequest.RequestError("Something went wrong while trying to parse the url. \n" + e.getClass()
                .getSimpleName() + ": " + e.getMessage()));
        } catch (final IOException e) {
            return Try.failure(new HTTPRequest.RequestError("Something went wrong while trying to read the contents of the input file. \n" + e
                .getClass()
                .getSimpleName() + ": " + e.getMessage()));
        } catch (final HTTPRequest.RequestError e) {
            return Try.failure(new HTTPRequest.RequestError("Something went wrong while processing the request. \n" + e.getClass()
                .getSimpleName() + ": " + e.getMessage()));
        }
    }
}
