package httpsocketclient.cli.parsing;

import httpsocketclient.cli.model.Command;
import httpsocketclient.cli.model.Option;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParserUtilTest {
    @Test
    void givenAllValidTokens_thenAllFoundAtSomeIndex() {
        final String input = "post -v -f /Users/tatumalenko/dev/body.txt -h Content-Type:application/json -d '{Assignment: 1}' http://httpbin.org/post";
        final List<ParsedToken<Option>> parsedOptionTokens = ParserUtil.parsedTokens(input, Option.class);
        final List<ParsedToken<Command>> parsedCommandTokens = ParserUtil.parsedTokens(input, Command.class);

        assertThat(parsedOptionTokens).containsExactly(
            new ParsedToken<>(Option.VERBOSE, 5),
            new ParsedToken<>(Option.FILE, 8),
            new ParsedToken<>(Option.HEADER, 43),
            new ParsedToken<>(Option.DATA, 76)
        );

        assertThat(parsedCommandTokens).containsExactly(new ParsedToken<>(Command.POST, 0));
    }

    @Test
    void givenAllValidTokensAndArguments_thenAllParsedCorrectly() {
        final String input = "post -v -f /Users/tatumalenko/dev/body.txt -h Content-Type:application/json -d '{Assignment: 1}' http://httpbin.org/post";
        final List<ParsedInput<Option>> parsedOptionInputs = ParserUtil.parsedInputs(input, ParserUtil.parsedTokens(input, Option.class));
        final List<ParsedInput<Command>> parsedCommandInputs = ParserUtil.parsedInputs(input, ParserUtil.parsedTokens(input, Command.class));

        assertThat(parsedOptionInputs).containsExactly(
            new ParsedInput<>(new ParsedToken<>(Option.VERBOSE, 5), ""),
            new ParsedInput<>(new ParsedToken<>(Option.FILE, 8), " /Users/tatumalenko/dev/body.txt"),
            new ParsedInput<>(new ParsedToken<>(Option.HEADER, 43), " Content-Type:application/json"),
            new ParsedInput<>(new ParsedToken<>(Option.DATA, 76), " '{Assignment: 1}' http://httpbin.org/post")
        );
        assertThat(parsedOptionInputs).element(0).hasFieldOrPropertyWithValue("parsedValue", "");
        assertThat(parsedOptionInputs).element(1).hasFieldOrPropertyWithValue("parsedValue", "/Users/tatumalenko/dev/body.txt");
        assertThat(parsedOptionInputs).element(2).hasFieldOrPropertyWithValue("parsedValue", "Content-Type:application/json");
        assertThat(parsedOptionInputs).element(3).hasFieldOrPropertyWithValue("parsedValue", "{Assignment: 1}");

        assertThat(parsedCommandInputs).containsExactly(
            new ParsedInput<>(new ParsedToken<>(Command.POST, 0), " -v -f /Users/tatumalenko/dev/body.txt -h Content-Type:application/json -d '{Assignment: 1}' http://httpbin.org/post")
        );
        assertThat(parsedCommandInputs).element(0).hasFieldOrPropertyWithValue("parsedValue", "http://httpbin.org/post");
    }

    @Test
    void givenHelpWithCommandArgumentInput_thenParsedCorrectly() {
        final String input = "help get";

        final List<ParsedInput<Command>> parsedCommandInputs = ParserUtil.parsedInputs(input, ParserUtil.parsedTokens(input, Command.class));

        assertThat(parsedCommandInputs).containsExactly(
            new ParsedInput<>(new ParsedToken<>(Command.HELP, 0), " get")
        );
    }
}