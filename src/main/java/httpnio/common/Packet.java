package httpnio.common;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Packet represents a simulated network packet.
 * As we don't have unsigned types in Java, we can achieve this by using a larger type.
 */
public class Packet {

    public static final int MIN_LEN = 11;
    public static final int MAX_LEN = 11 + 1024;

    private final int type;
    private final long sequenceNumber;
    private final InetSocketAddress peerAddress;
    private final int peerPort;
    private final byte[] payload;

    public Packet(
        final int type,
        final long sequenceNumber,
        final InetSocketAddress peerAddress,
        final int peerPort,
        final byte[] payload) {
        this.type = type;
        this.sequenceNumber = sequenceNumber;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.payload = payload;
    }

    public int getType() {
        return type;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public InetSocketAddress getPeerAddress() {
        return peerAddress;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * Creates a builder from the current packet.
     * It's used to create another packet by re-using some parts of the current packet.
     */
    public Builder toBuilder() {
        return new Builder()
            .setType(type)
            .setSequenceNumber(sequenceNumber)
            .setPeerAddress(peerAddress)
            .setPortNumber(peerPort)
            .setPayload(payload);
    }

    /**
     * Writes a raw presentation of the packet to byte buffer.
     * The order of the buffer should be set as BigEndian.
     */
    private void write(final ByteBuffer buf) throws UnknownHostException {
        buf.put((byte) type);
        buf.putInt((int) sequenceNumber);
        buf.put(peerAddress.getAddress().getAddress());
        buf.putShort((short) peerPort);
        buf.put(payload);
    }

    /**
     * Create a byte buffer in BigEndian for the packet.
     * The returned buffer is flipped and ready for get operations.
     */
    public ByteBuffer toBuffer() throws UnknownHostException {
        final ByteBuffer buf = ByteBuffer.allocate(MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        write(buf);
        buf.flip();
        return buf;
    }

    /**
     * Returns a raw representation of the packet.
     */
    public byte[] toBytes() throws UnknownHostException {
        final ByteBuffer buf = toBuffer();
        final byte[] raw = new byte[buf.remaining()];
        buf.get(raw);
        return raw;
    }

    /**
     * fromBuffer creates a packet from the given ByteBuffer in BigEndian.
     */
    public static Packet fromBuffer(final ByteBuffer buf) throws IOException {
        if (buf.limit() < MIN_LEN || buf.limit() > MAX_LEN) {
            throw new IOException("Invalid length");
        }

        final Builder builder = new Builder();

        builder.setType(Byte.toUnsignedInt(buf.get()));
        builder.setSequenceNumber(Integer.toUnsignedLong(buf.getInt()));

        final byte[] host = new byte[]{buf.get(), buf.get(), buf.get(), buf.get()};
        final var peerAddress = new InetSocketAddress(InetAddress.getByAddress(host), Short.toUnsignedInt(buf.getShort()));
        builder.setPeerAddress(peerAddress);
        builder.setPortNumber(peerAddress.getPort());

        final byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        builder.setPayload(payload);

        return builder.create();
    }

    /**
     * fromBytes creates a packet from the given array of bytes.
     */
    public static Packet fromBytes(final byte[] bytes) throws IOException {
        final ByteBuffer buf = ByteBuffer.allocate(MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        buf.put(bytes);
        buf.flip();
        return fromBuffer(buf);
    }

    @Override
    public String toString() {
        return String.format("#%d peer=%s:%d, size=%d", sequenceNumber, peerAddress, peerPort, payload.length);
    }

    public static class Builder {
        private int type;
        private long sequenceNumber;
        private InetSocketAddress peerAddress;
        private int portNumber;
        private byte[] payload;

        public Builder setType(final int type) {
            this.type = type;
            return this;
        }

        public Builder setSequenceNumber(final long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder setPeerAddress(final InetSocketAddress peerAddress) {
            this.peerAddress = peerAddress;
            return this;
        }

        public Builder setPortNumber(final int portNumber) {
            this.portNumber = portNumber;
            return this;
        }

        public Builder setPayload(final byte[] payload) {
            this.payload = payload;
            return this;
        }

        public Packet create() {
            return new Packet(type, sequenceNumber, peerAddress, portNumber, payload);
        }
    }
}
