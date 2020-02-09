package httpsocketclient;

import httpsocketclient.cli.Parser;
import io.vavr.control.Try;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EntryPointTest {

    @ParameterizedTest
    @MethodSource("errorProvider")
    void execReturnsError(final String in, final String cause) {
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

    static Stream<Arguments> errorProvider() {
        return Stream.of(
            Arguments.of(
                "httpc get --header Content-Type:application/json --header Content-Type:application/html http://postman-echo.com/get?foo1=bar1&foo2=bar2",
                "Something went wrong while processing the request. RequestError: Error occurred while parsing the header: Content-Type:application/html. A duplicate key was found: Content-Type.")
        );
    }
}