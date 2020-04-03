package httpnio.server;

import io.vavr.control.Try;

import java.util.Optional;
import java.util.function.Function;

public interface TransportLayerProtocol {
    enum Type {
        UDP(options -> Try.of(() -> new UDPSelectiveRepeatProtocol())),
        TCP(options -> Try.of(() -> new UDPSelectiveRepeatProtocol()));

        private final Function<String, Try<TransportLayerProtocol>> protocol;

        Type(final Function<String, Try<TransportLayerProtocol>> protocol) {
            this.protocol = protocol;
        }
    }

    static Function<String, Try<TransportLayerProtocol>> of(final String name) {
        return Optional.of(name).map(n -> {
            switch (name.toLowerCase()) {
                case "udp":
                    return Type.UDP.protocol;
                case "tcp":
                case "https":
                case "http":
                    return Type.TCP.protocol;
                default:
                    throw new IllegalArgumentException(name + " is not a valid TransportLayerProtocol name");
            }
        }).orElseThrow(() -> new IllegalArgumentException("TransportLayerProtocol name cannot be null"));
    }
}
