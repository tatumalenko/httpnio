package httpsocketclient.cli.parsing;

import httpsocketclient.cli.model.Argument;
import httpsocketclient.cli.model.ArgumentResolvable;
import httpsocketclient.cli.model.ValueResolvable;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.regex.Pattern;

@Data
@Accessors(fluent = true)
public class ParsedInput<T extends Enum<T> & ValueResolvable & ArgumentResolvable> {
    private final ParsedToken<T> token;
    private final Argument argument;
    private final String rawValue;
    private final String parsedValue;

    public ParsedInput(final ParsedToken<T> token, final String rawValue) {
        this.token = token;
        argument = token.input().argument();
        this.rawValue = rawValue;

        final var p = Pattern.compile(argument.regex());
        final var m = p.matcher(rawValue.trim());
        parsedValue = m.find() ? m.group(0) : null;
    }

    public boolean isValid() {
        return parsedValue != null && argument.isValid(parsedValue);
    }
}