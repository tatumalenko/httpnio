package httpsocketclient.cli.parsing;

import httpsocketclient.cli.model.Argument;
import httpsocketclient.cli.model.ArgumentResolvable;
import httpsocketclient.cli.model.Command;
import httpsocketclient.cli.model.ValueResolvable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ParserUtil {
    private ParserUtil() {
        throw new IllegalStateException("Util class");
    }

    public static <T extends Enum<T> & ValueResolvable & ArgumentResolvable> List<ParsedToken<T>> parsedTokens(final String input, final Class<T> clazz) {
        return Arrays.stream(clazz.getEnumConstants())
            .map(e -> new ParsedToken<>(e, input.indexOf(e.value())))
            .filter(e -> e.startIndex() != -1)
            .sorted(new ParsedTokenComparator<>())
            .collect(Collectors.toList());
    }

    public static <T extends Enum<T> & ValueResolvable & ArgumentResolvable> List<ParsedInput<T>> parsedInputs(final String input, final List<ParsedToken<T>> parsedTokens) {
        final List<ParsedInput<T>> parsedInputs = new ArrayList<>();

        if (parsedTokens == null || parsedTokens.isEmpty()) {
            return Collections.emptyList();
        }

        if (parsedTokens.get(0).input().argument() == Argument.COMMAND
            && parsedTokens.get(0).input().value().equalsIgnoreCase(Command.HELP.value())) {
            final ParsedToken<T> token = parsedTokens.get(0);
            return List.of(new ParsedInput<>(token, input.substring(token.endIndex())));
        }

        if (parsedTokens.size() == 1) {
            final ParsedToken<T> token = parsedTokens.get(0);
            return List.of(new ParsedInput<>(token, input.substring(token.startIndex() + token.input().value().length())));
        }

        for (int i = 0; i < parsedTokens.size() - 1; i++) {
            final ParsedToken<T> first = parsedTokens.get(i);
            final ParsedToken<T> second = parsedTokens.get(i + 1);
            parsedInputs.add(new ParsedInput<>(first, input.substring(first.endIndex(), second.startIndex() - 1)));

            if (i == parsedTokens.size() - 2) {
                parsedInputs.add(new ParsedInput<>(second, input.substring(second.startIndex() + second.input().value().length())));
            }
        }

        return parsedInputs;
    }
}
