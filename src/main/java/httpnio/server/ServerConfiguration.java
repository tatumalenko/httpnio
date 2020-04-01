package httpnio.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

@AllArgsConstructor
@Getter
@Accessors(fluent = true)
public final class ServerConfiguration {
    private final int port;

    private final boolean verbose;

    private final String directory;
}
