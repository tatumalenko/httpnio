package httpnio.common;

public class UDPSRProtocol implements TransportProtocol {

    @Override
    public int windowSize() {
        return 5;
    }

    @Override
    public int maxPacketSize() {
        return Packet.MAX_LEN;
    }

    @Override
    public int minPacketSize() {
        return Packet.MIN_LEN;
    }

    @Override
    public int packetTimeoutMs() {
        return 500;
    }

    @Override
    public void send() {

    }

    @Override
    public void receive() {

    }
}
