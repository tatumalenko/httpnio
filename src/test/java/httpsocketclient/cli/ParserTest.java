package httpsocketclient.cli;

import httpsocketclient.EntryPoint;
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
    @MethodSource("okProvider")
    void parserParseReturnsOk(final String in, final Object expected) {
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

    static Stream<Arguments> okProvider() {
        return Stream.of(
            Arguments.of(
                "httpc get  --verbose --header User-Agent:Chrome --data '{ \"key\": \"value\" }' https://google.com",
                new EntryPoint("https://google.com", null, true, List.of("User-Agent:Chrome"), "'{ \"key\": \"value\" }'", null, null)),
            Arguments.of(
                "httpc help",
                "httpc: httpc is a curl-like application but supports HTTP protocol only.\n" +
                    "\n" +
                    "Usage:\n" +
                    "   httpc <subcommand> [options]\n" +
                    "The subcommands are:\n" +
                    "   get:\tGet executes a HTTP GET request for a given URL.\n" +
                    "   post:\tPost executes a HTTP POST request for a given URL with inline data or from file.\n" +
                    "   help:\tprints this output.\n" +
                    "Use \"httpc help <subcommand>\" for more information about a subcommand."),
            Arguments.of(
                "httpc get help",
                "\n" +
                    "Subcommand (get): Get executes a HTTP GET request for a given URL.\n" +
                    "Usage:\n" +
                    "   httpc get [options] url\n" +
                    "Options:\n")
        );
    }

    @ParameterizedTest
    @MethodSource("errorProvider")
    void parserParseReturnsError(final String in, final String cause) {
        final var parser = new Parser<>(EntryPoint.class);
        final var ep = parser.parse(in);
        assertThat(ep.getCause().getMessage()).isEqualTo(cause);
    }

    static Stream<Arguments> errorProvider() {
        return Stream.of(
            Arguments.of(
                "httpc get  --verbose --header --data '{ \"key\": \"value\" }' https://google.com",
                "--data is not a valid argument for option headers!"),
            Arguments.of(
                "httpc get  --verbose --header poop --data '{ \"key\": \"value\" }' https://google.com",
                "poop is not a valid argument for option headers!"),
            Arguments.of(
                "httpc get  --v --header poop --data '{ \"key\": \"value\" }' https://google.com",
                "--v is not a valid flag or option!"),
            Arguments.of(
                "httpc get --header Content-Type:application/json --header Content-Type:application/html http://postman-echo.com/get?foo1=bar1&foo2=bar2",
                "Error occurred while parsing the header: Content-Type:application/html. A duplicate key was found: Content-Type.")
        );
    }

}