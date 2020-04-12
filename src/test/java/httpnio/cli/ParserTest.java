package httpnio.cli;

import httpnio.client.EntryPoint;
import io.vavr.control.Either;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class ParserTest {
    @ParameterizedTest
    @MethodSource("successProvider")
    void parserParseReturnsSuccess(final String in, final Object expected) {
        final var parser = new Parser<>(EntryPoint.class);
        final var ep = parser.parse(in);

        assertThat(ep.get()).isNotNull();
        assertThat(ep.get()).isOfAnyClassIn(Either.Left.class, Either.Right.class);

        ep.get().mapLeft(e -> {
            assertThat(e).isOfAnyClassIn(String.class);
            assertThat(e).isEqualTo(expected);
            return 0;
        });
        ep.get().map(e -> {
            assertThat(e).isOfAnyClassIn(EntryPoint.class);
            assertThat(e).isEqualTo(expected);
            return 0;
        });
    }

    static Stream<Arguments> successProvider() {
        return Stream.of(
            Arguments.of(
                "httpc get  --verbose --header User-Agent:Chrome --data { \"key\": \"value\" } https://google.com",
                new EntryPoint("https://google.com", null, true, false, List.of("{ \"key\": \"value\" }"), null, null, null, null)),
            Arguments.of(
                "httpc help",
                "\n" +
                    "httpc: httpc is a curl-like application but supports HTTP protocol only.\n" +
                    "\n" +
                    "Usage:\n" +
                    "   httpc <subCommand> [flags] [options]\n\n" +
                    "The subCommands are:\n" +
                    "   get                 Get executes a HTTP GET request for a given URL.\n" +
                    "   post                Post executes a HTTP POST request for a given URL with inline data or from file.\n" +
                    "   help                Prints this output.\n\n" +
                    "Use \"httpc help <subCommand>\" for more information about a subCommand"),
            Arguments.of(
                "httpc get help",
                "\n" +
                    "\n" +
                    "SubCommand (get): Get executes a HTTP GET request for a given URL.\n" +
                    "Usage:\n" +
                    "   httpc get [flags] [options] url\n" +
                    "Flags:\n" +
                    "   --verbose [-v]                Prints the detail of the response such as protocol, status, and headers.\n" +
                    "Options:\n" +
                    "   --header [-h] key:value       Associates headers to HTTP Request with the format 'key:value'\n" +
                    "   --out [-o] /file/to/output    Outputs the response of the HTTP request to a file."));
    }

    @ParameterizedTest
    @MethodSource("failureProvider")
    void parserParseReturnsFailure(final String in, final String cause) {
        final var parser = new Parser<>(EntryPoint.class);
        final var ep = parser.parse(in);
        assertThat(ep.getCause().getMessage()).isEqualTo(cause);
    }

    static Stream<Arguments> failureProvider() {
        return Stream.of(
            Arguments.of(
                "httpc get  --verbose --header --data '{ \"key\": \"value\" }' https://google.com",
                "--data is not a valid argument for option headers!"),
            Arguments.of(
                "httpc get  --verbose --header poop --data '{ \"key\": \"value\" }' https://google.com",
                "poop is not a valid argument for option headers!"),
            Arguments.of(
                "httpc get  --v --header poop --data '{ \"key\": \"value\" }' https://google.com",
                "--v is not a valid flag or option!")
        );
    }
}
