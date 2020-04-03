package httpnio.server;

import httpnio.client.Request;
import io.vavr.control.Try;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public interface ApplicationLayerProtocol {
    enum Type {
        FILESERVER(directory -> Try.of(() -> new FileServerProtocol(directory)));

        Function<String, Try<ApplicationLayerProtocol>> protocol;

        Type(final Function<String, Try<ApplicationLayerProtocol>> protocol) {
            this.protocol = protocol;
        }
    }

    static Function<String, Try<ApplicationLayerProtocol>> of(final String name) {
        return Optional.of(name).map(n -> {
            switch (name.toLowerCase()) {
                case "https":
                case "http":
                    return Type.FILESERVER.protocol;
                default:
                    throw new IllegalArgumentException(name + " is not a valid TransportLayerProtocol name");
            }
        }).orElseThrow(() -> new IllegalArgumentException("TransportLayerProtocol name cannot be null"));
    }

    Response response(Request request) throws IOException;

    ApplicationLayerProtocol copy() throws IllegalAccessException, IOException;
}
