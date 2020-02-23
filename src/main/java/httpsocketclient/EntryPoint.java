package httpsocketclient;

import java.util.Arrays;

public class EntryPoint {

    public static void main(final String[] args) {
        if (!Arrays.asList(args).isEmpty()) {
            final var command = args[0];

            switch (command) {
                case Const.HTTPC:
                    httpsocketclient.client.EntryPoint.entryPoint(args);
                    break;
                case Const.HTTPFS:
                    httpsocketclient.server.EntryPoint.entryPoint(args);
                    break;
                default:
                    throw new IllegalStateException("Not a valid command.");
            }
        }
    }
}
