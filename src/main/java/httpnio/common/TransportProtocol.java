package httpnio.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

    default int windowSize() {
        return 5;
    }

    default int maxPacketSize() {
        return Packet.MAX_LEN;
    }

    default int minPacketSize() {
        return Packet.MIN_LEN;
    }

    default int maxConsecutiveRetries() {
        return 20;
    }

    default ByteBuffer emptyBuffer() {
        return ByteBuffer
            .allocateDirect(maxPacketSize())
            .order(ByteOrder.BIG_ENDIAN);
    }

    default int packetTimeoutMs() {
        return 500;
    }

    boolean send(UDPSRProtocol.Agent sender, Packet[] packets) throws IOException, InterruptedException;

    <T> T receive(UDPSRProtocol.Agent receiver) throws IOException, InterruptedException;
}

