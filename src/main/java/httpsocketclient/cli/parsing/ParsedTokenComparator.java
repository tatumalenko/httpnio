package httpsocketclient.cli.parsing;

import httpsocketclient.cli.model.ArgumentResolvable;
import httpsocketclient.cli.model.ValueResolvable;

import java.util.Comparator;

public class ParsedTokenComparator<T extends Enum<T> & ValueResolvable & ArgumentResolvable> implements Comparator<ParsedToken<T>> {
    @Override
    public int compare(final ParsedToken<T> pi1, final ParsedToken<T> pi2) {
        return Integer.compare(pi1.startIndex(), pi2.startIndex());
    }
}