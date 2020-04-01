package httpnio;

import java.util.Arrays;

public class EntryPoint {

    public static void main(final String[] args) {
        if (!Arrays.asList(args).isEmpty()) {
            final var command = args[0];

            switch (command) {
                case Const.HTTPC:
                    httpnio.client.EntryPoint.entryPoint(args);
                    break;
                case Const.HTTPFS:
                    httpnio.server.EntryPoint.entryPoint(args);
                    break;
                default:
                    throw new IllegalArgumentException("Not a valid command.");
            }
        }
    }
}
