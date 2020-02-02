package httpsocketclient.cli.parsing;

import httpsocketclient.cli.model.ArgumentResolvable;
import httpsocketclient.cli.model.ValueResolvable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
public class ParsedToken<T extends Enum<T> & ValueResolvable & ArgumentResolvable> {
    private T input;
    private int startIndex;
    private int endIndex;

    public ParsedToken(final T input, final int startIndex) {
        this.input = input;
        this.startIndex = startIndex;
        endIndex = this.startIndex + input.value().length();
    }
}