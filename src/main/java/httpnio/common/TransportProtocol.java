package httpnio.common;

public interface TransportProtocol {
    enum Type {
        UDP,
        TCP;

        public static Type of(final String name) {
            switch (name) {
                case "UDP":
                case "udp":
                    return UDP;
                case "TCP":
                case "tcp":
                default:
                    return TCP;
            }
        }
    }

    static TransportProtocol of(final Type type) {
        switch (type) {
            case UDP:
                return new UDPSRProtocol();
            case TCP:
            default:
                return null;
        }
    }

    int windowSize();

    int maxPacketSize();

    int minPacketSize();

    int packetTimeoutMs();

    void send();

    void receive();
}

