package httpsocketclient.server;

import lombok.AllArgsConstructor;

import java.util.Arrays;

@AllArgsConstructor
public final class ServerLogger {

    private final boolean standardOutput;
    
    private final String id;

    public void debug(final String text) {
        if (standardOutput) {
            System.out.println();
            Arrays.stream(text.split("\n"))
                .forEach(line -> System.out.println(id + ": " + line));
        }
    }

    public void error(final String text) {
        if (standardOutput) {
            System.out.println();
            System.err.println(id + ": " + text);
        }
    }
}
