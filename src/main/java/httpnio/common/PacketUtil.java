package httpnio.common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class PacketUtil {
    private PacketUtil() {
        throw new IllegalStateException("Static util class");
    }

    public static ByteBuffer emptyBuffer() {
        return ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);
    }

    public static ByteBuffer emptyPayloadBuffer() {
        return ByteBuffer.allocate(Packet.MAX_LEN - Packet.MIN_LEN).order(ByteOrder.BIG_ENDIAN);
    }

    public static ByteBuffer[] split(final byte[] data) {
        final ByteBuffer dataBuffer = ByteBuffer.wrap(data);
        final ToIntFunction<ByteBuffer> amountOfBytesToCopy = (ByteBuffer b) -> Math.min(b.remaining(), Packet.PAYLOAD_SIZE);

        final List<ByteBuffer> buffers = new ArrayList<>();

        ByteBuffer buffer;

        while (dataBuffer.hasRemaining()) {
            final int nBytes = amountOfBytesToCopy.applyAsInt(dataBuffer);
            buffer = ByteBuffer.allocate(nBytes).order(ByteOrder.BIG_ENDIAN);
            final byte[] nDataBytes = new byte[nBytes];
            dataBuffer.get(nDataBytes);
            buffer.put(nDataBytes);
            buffer.flip();
            buffers.add(buffer.compact());
        }

        return buffers.toArray(new ByteBuffer[0]);
    }
}
