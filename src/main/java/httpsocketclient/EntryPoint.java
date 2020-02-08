package httpsocketclient;

import httpsocketclient.cli.*;
import httpsocketclient.http.*;
import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.net.MalformedURLException;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Command(name = Const.CLI_NAME, description = "httpc is a curl-like application but supports HTTP protocol only.")
public class EntryPoint {
    @SubCommand(
        name = "get",
        argument = @Argument(
            name = "url",
            format = "https://some-website.ca",
            regex = "(^https?://\\S+$)",
            description = "The URL of the request."),
        description = "Get executes a HTTP GET request for a given URL.")
    String get;

    @SubCommand(
        name = "post",
        argument = @Argument(
            name = "url",
            format = "https://some-website.ca",
            regex = "(^https?://\\S+$)",
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

    @Option(
        name = "headers",
        alias = {"--header", "-h"},
        argument = @Argument(
            name = "header",
            format = "key:value",
            regex = "(^[^\\s\\:]+:[^\\s\\:]+$)",
            description = ""),
        subCommands = {"get", "post"},
        description = "Associates headers to HTTP Request with the format 'key:value'")
    List<String> headers;

    @Option(
        name = "data",
        alias = {"--data", "-d"},
        argument = @Argument(name = "data",
            format = "{\"prop\": \"value\"}",
            regex = "(^\\{(\\s*,?\\s*\\S+:\\s*\\S+\\s*)+\\}$)",
            description = ""),
        subCommands = {"post"},
        description = "Associates an inline data to the body HTTP POST request.")
    String data;

    @Option(
        name = "in",
        alias = {"--file", "-f"},
        argument = @Argument(name = "file",
            format = "/file/to/body",
            regex = "(^\\/[\\w\\W]+\\.txt$)",
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

    public static void main(final String[] args) {
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
                            if (success.get().verbose) {
                                System.out.println(response.request());
                            }
                            System.out.println(response.messageHeader());
                            System.out.println(response.messageBody());
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

    static Try<Response> exec(final EntryPoint ep) {
        try {
            if (ep.get == null && ep.post == null) {
                throw new ParseError("At least one of the possible sub-commands and a url must be specified.");
            }

            final var request = Request.builder()
                .method(ep.get != null ? HttpMethod.GET : HttpMethod.POST)
                .url(ep.get != null ? ep.get : ep.post)
                .headers(ep.headers)
                .body(ep.data)
                .in(ep.in)
                .out(ep.out)
                .build();

            return Try.of(() -> new Client().request(request));
        } catch (final ParseError e) {
            return Try.failure(e);
        } catch (final MalformedURLException | RequestError e) {
            return Try.failure(new RequestError(e.getMessage()));
        }
    }
}
