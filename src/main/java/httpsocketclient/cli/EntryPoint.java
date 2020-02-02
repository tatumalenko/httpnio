package httpsocketclient.cli;

import httpsocketclient.cli.parse.CLI;
import httpsocketclient.cli.parse.Parser;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@CLI.Command(name = "httpc", description = "httpc is a curl-like application but supports HTTP protocol only.")
public class EntryPoint {
    @CLI.SubCommand(
        name = "get",
        argument = @CLI.Argument(
            name = "url",
            format = "https://some-website.ca",
            regex = "(https?://\\S+)",
            description = "The URL of the request."),
        description = "Get executes a HTTP GET request for a given URL.")
    String get;

    @CLI.SubCommand(
        name = "post",
        argument = @CLI.Argument(
            name = "url",
            format = "https://some-website.ca",
            regex = "(https?://\\S+)",
            description = "The URL of the request."),
        description = "Post executes a HTTP POST request for a given URL with inline data or from file.")
    String post;

    @CLI.Flag(
        name = "verbose",
        alias = {"--verbose", "-v"},
        required = false,
        description = "Prints the detail of the response such as protocol, status, and headers.")
    boolean verbose;

    @CLI.Option(
        name = "header",
        alias = {"--header", "-h"},
        argument = @CLI.Argument(
            name = "header",
            format = "key:value",
            regex = "([\\w:\\-\\/]+)",
            description = ""),
        description = "Associates headers to HTTP Request with the format 'key:value'")
    List<String> header;

    @CLI.Option(
        name = "data",
        alias = {"--data", "-d"},
        argument = @CLI.Argument(name = "data",
            format = "{\"prop\": \"value\"}",
            regex = "('\\{[\"\\w\":\\-/'\",\\s+]+\\})'",
            description = ""),
        description = "Associates an inline data to the body HTTP POST request.")
    String data;

    @CLI.Option(
        name = "file",
        alias = {"--file", "-f"},
        argument = @CLI.Argument(name = "file",
            format = "/some/file/location",
            regex = "(\\/[\\w\\W]+\\.txt)",
            description = ""),
        description = "Associates the content of a file to the body HTTP POST request")
    String file;

    public static void main(final String[] args) {
        final var parsed = Parser.parse(EntryPoint.class, "httpc get  --verbose --header User-Agent:Chrome --data '{ \"key\": \"value\" }' https://google.com");

    }

    public static URL parseURL(final String url) {
        try {
            return new URL(url);
        } catch (final MalformedURLException e) {
            throw new RuntimeException("The URL provided is malformed. Please ensure the url is valid and try again.");
        }
    }
}




