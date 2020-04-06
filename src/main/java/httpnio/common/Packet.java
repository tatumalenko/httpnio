package httpnio.common;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Data
@Accessors(fluent = true)
public class Packet {
    public enum State {
        SYN, // seqNum = 0 (only sndr transmits, only recv receives)
        SYNACK, // seqNum = 1 (only recv transmits, only sendr receives)
        ACK, // seqNum = 2 (both sndr and recv transmit and receive)
        BFRD, // seqNum = 3 (only sndr transmits (initially and on timeouts), both have state: either ready to send, sent+ack, or sent+no ack)
        TRSM, // seqNum = 4 (only sndr state: transmission confirmed (when received ack in sndr window), if base = seq# -> base++ && test each pkt in base-base+N-1 if TRSM if so base++ again until it isnt where then break)
        ACKDATA; // seqNum = 5+

        public static State of(final int type) {
            final var states = State.values();
            if (type > states.length - 2) {
                return State.ACKDATA;
            } else {
                return states[type];
            }
        }

        public static int of(final State state) {
            return state.ordinal();
        }
    }

    public static final int MIN_LEN = 11;
    public static final int MAX_LEN = 11 + 1013;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final int type;

    private final long sequenceNumber;

    private final State state;

    private final InetSocketAddress peerAddress;

    private final InetSocketAddress routerAddress;

    private final byte[] payload;

    public Packet(
        final int type,
        final long sequenceNumber,
        final State state,
        final InetSocketAddress peerAddress,
        final InetSocketAddress routerAddress,
        final byte[] payload) {
        this.type = type;
        this.sequenceNumber = sequenceNumber;
        this.state = state;
        this.peerAddress = peerAddress;
        this.routerAddress = routerAddress;
        this.payload = payload;
    }

    public String payload(final Charset charset) {
        return new String(Objects.requireNonNullElse(payload, "null".getBytes()), charset);
    }

    public String payload() {
        return payload(UTF_8);
    }

    public boolean is(final State state) {
        return State.of(type) == state;
    }

    public ByteBuffer buffer() {
        final var buffers = buffers();
        //log.info("buffers[0]: {}", buffers[0]);

        return buffers[0];
    }

    private ByteBuffer bufferWithoutPayload() {
        final var buffer = ByteBuffer.allocate(MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        writeWithoutPayload(buffer);
        return buffer;
    }

    public String buffersToString() {
        return Arrays.stream(buffers()).map(b -> {
            try {
                return Packet.of(b).payload();
            } catch (IOException e) {
                e.printStackTrace();
                return "";
            }
        }).collect(Collectors.joining());
    }

    public Packet[] packets() throws IOException {
        final ByteBuffer[] buffers = buffers();
        final List<Packet> packets = new ArrayList<>();
        for (final ByteBuffer buffer : buffers) {
            final Packet packet = of(buffer);
            packets.add(packet);
        }
        return packets.toArray(new Packet[0]);
    }

    public ByteBuffer[] buffers() {
        final List<ByteBuffer> buffers = new ArrayList<>();
        ByteBuffer buffer = bufferWithoutPayload();
        final var payloadBuffer = ByteBuffer.wrap(payload);
        //log.info("payloadBuffer.hasRemainig: {}", payloadBuffer.hasRemaining());
        if (!payloadBuffer.hasRemaining()) {
            buffer.put("".getBytes());
            buffers.add(buffer);
        }
        while (payloadBuffer.hasRemaining()) {
            if (payloadBuffer.remaining() > buffer.remaining()) {
                final byte[] tailToAddToBuffer = new byte[buffer.remaining()];
                payloadBuffer.get(tailToAddToBuffer);
                buffer.put(tailToAddToBuffer);
                buffer.flip();
                buffers.add(buffer);
                buffer = bufferWithoutPayload();
            } else {
                buffer.put(payloadBuffer);
            }

            if (!payloadBuffer.hasRemaining()) {
                buffer.flip();
                buffers.add(buffer);
                buffer = ByteBuffer.allocate(MAX_LEN).order(ByteOrder.BIG_ENDIAN);
            }
        }

        return buffers.toArray(new ByteBuffer[0]);
    }

    public byte[] bytes() {
        final ByteBuffer buffer = buffer();
        final byte[] raw = new byte[buffer.remaining()];
        buffer.get(raw);
        return raw;
    }

    public static Builder of() {
        return new Builder();
    }

    // WARNING: peer is changed for server but not for client (i.e. client receives
    public static Packet of(final ByteBuffer buffer) throws IOException {
        if (buffer.limit() < MIN_LEN || buffer.limit() > MAX_LEN) {
            throw new IOException("Invalid length");
        }

        final Builder builder = new Builder();

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

    public static Packet of(final HTTPRequest request) {
        return new Builder()
            .sequenceNumber(State.SYN.ordinal())
            .state(State.SYN)
            .routerAddress(request.routerAddress())
            .peerAddress(request.socketAddress())
            .payload(request.toString().getBytes())
            .build();
    }

    @Override
    public String toString() {
        return String.format(
            "#%d peer=%s, router=%s, size=%d, \npayload=\n%s",
            sequenceNumber,
            Objects.requireNonNullElse(peerAddress, "null"),
            Objects.requireNonNullElse(routerAddress, "null"),
            Objects.requireNonNullElse(payload.length, 0),
            payload().substring(0, Math.min(20, payload().length())));
    }

    private void write(final ByteBuffer buffer) {
        writeWithoutPayload(buffer);
        buffer.put(payload);
    }

    private void writeWithoutPayload(final ByteBuffer buffer) {
        buffer.put((byte) type);
        buffer.putInt((int) sequenceNumber);
        buffer.put(peerAddress.getAddress().getAddress());
        buffer.putShort((short) peerAddress.getPort());
    }

    public Builder builder() {
        return new Builder()
            .sequenceNumber(sequenceNumber)
            .state(state)
            .peerAddress(peerAddress)
            .routerAddress(routerAddress)
            .payload(payload);
    }

    /**
     * Creates a builder using the passed value of the address
     * as the peer. Only required for client sending since
     * router already rewrites contents of peer inside packet
     * sent from client, and hence server can just use the
     * passed in value.
     *
     * @param server
     * @return
     */
    public Builder builder(final InetSocketAddress server) {
        return builder().peerAddress(server);
    }

    @Data
    @Accessors(fluent = true)
    public static class Builder {
        private int type;
        private long sequenceNumber;
        private State state;
        private InetSocketAddress peerAddress;
        private InetSocketAddress routerAddress;
        private byte[] payload;

        public Builder sequenceNumber(final long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            //state = State.of(sequenceNumber);
            return this;
        }

        public Builder state(final State state) {
            this.state = state;
            type = State.of(state);
            return this;
        }

        public Builder payload(final byte[] payload) {
            this.payload = payload;
            return this;
        }

        public Builder payload(final String payload) {
            this.payload = payload.getBytes();
            return this;
        }

        private Builder type() {
            return this;
        }

        private Builder type(final int type) {
            return this;
        }

        public Packet build() {
            return new Packet(
                type,
                sequenceNumber,
                State.of(type),
                peerAddress,
                routerAddress,
                payload != null && payload().length != 0 ? payload : " ".getBytes());
        }
    }
}
