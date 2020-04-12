package httpnio.common;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Data
@Accessors(fluent = true)
@Builder(toBuilder = true)
public class Packet {
    public enum State {
        SYN, // type = 0 (only sndr transmits, only recv receives)
        SYNACK, // type = 1 (only recv transmits, only sendr receives)
        ACK, // type = 2 (both sndr and recv transmit and receive)
        BFRD, // type = 3 (only sndr transmits (initially and on timeouts), both have state: either ready to send, sent+ack, or sent+no ack)
        TRSM, // type = 4 (only sndr state: transmission confirmed (when received ack in sndr window), if base = seq# -> base++ && test each pkt in base-base+N-1 if TRSM if so base++ again until it isnt where then break)
        ACKUNK, // type = 5 (only sndr state: read BFRD when expected ACKDATA, likely due to unsynched rec/sndr phases)
        ACKDATA; // type = 6+

        public static State of(final int type) {
            switch (type) {
                case 0:
                    return SYN;
                case 1:
                    return SYNACK;
                case 2:
                    return ACK;
                case 3:
                    return BFRD;
                case 4:
                    return TRSM;
                case 5:
                    return ACKUNK;
                default:
                    return ACKDATA;
            }
        }

        public static int type(final State state) {
            return state.ordinal();
        }
    }

    public static final int MIN_LEN = 11;

    public static final int MAX_LEN = 11 + 1013;

    public static final int PAYLOAD_SIZE = 1013;

    private final long sequenceNumber;

    private final State state;

    private final InetSocketAddress peerAddress;

    private final byte[] payload;

    public Packet(
        final long sequenceNumber,
        final State state,
        final InetSocketAddress peerAddress,
        final byte[] payload) {
        this.sequenceNumber = sequenceNumber;
        this.state = state;
        this.peerAddress = peerAddress;
        this.payload = payload;
    }

    public String payload(final Charset charset) {
        return new String(Objects.requireNonNullElse(payload, "null".getBytes()), charset);
    }

    public String payload() {
        return payload(UTF_8);
    }

    public static Packet of(final ByteBuffer buffer) throws IOException {
        if (buffer.limit() < MIN_LEN || buffer.limit() > MAX_LEN) {
            throw new IOException("Invalid length of " + buffer.limit());
        }

        final PacketBuilder builder = new PacketBuilder();

        builder.state(State.of(Byte.toUnsignedInt(buffer.get())));
        builder.sequenceNumber(Integer.toUnsignedLong(buffer.getInt()));

        final byte[] host = new byte[]{buffer.get(), buffer.get(), buffer.get(), buffer.get()};
        final var peerAddress = new InetSocketAddress(InetAddress.getByAddress(host), Short.toUnsignedInt(buffer.getShort()));
        builder.peerAddress(peerAddress);

        final byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        builder.payload(payload);

        return builder.build();
    }

    public static Packet of(final byte[] bytes) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        buffer.put(bytes);
        buffer.flip();
        return of(buffer);
    }

    public boolean is(final State state) {
        return this.state == state;
    }

    public ByteBuffer buffer() {
        final var buffer = PacketUtil.emptyBuffer();
        buffer.put((byte) State.type(state));
        buffer.putInt((int) sequenceNumber);
        buffer.put(peerAddress.getAddress().getAddress());
        buffer.putShort((short) peerAddress.getPort());
        if (payload != null) buffer.put(payload);
        buffer.flip();
        return buffer;
    }

    public void debugPrint() throws UnknownHostException {
        final var b = buffer();
        var s = "";
        s += "type: " + Byte.toUnsignedInt(b.get()) + "\n";
        s += "seqNum: " + Integer.toUnsignedLong(b.getInt()) + "\n";
        s += "peer: " + InetAddress.getByAddress(new byte[]{b.get(), b.get(), b.get(), b.get()}) + "\n";
        s += "port: " + Short.toUnsignedInt(b.getShort()) + "\n";
        final var pay = new byte[b.remaining()];
        b.get(pay);
        s += "payload: " + new String(pay);
        log.debug(s);
    }

    public static PacketBuilder builder() {
        return new PacketBuilder();
    }

    @Override
    public String toString() {
        return String.format(
            "#%d peer=%s, state=%s, size=%d, payload=%s",
            sequenceNumber,
            Objects.requireNonNullElse(peerAddress, "null"),
            state,
            payload != null ? payload.length : 0,
            payload != null ? payload.length != 0 ? payload().replaceAll("\n", " ")
                .substring(0, Math.min(20, payload().length())) : "n/a" : "null");
    }
}
