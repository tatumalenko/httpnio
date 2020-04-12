package httpnio.common;

import httpnio.client.Client;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static httpnio.common.Packet.State.*;

@Slf4j
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

    public interface Agent {
        void write(Packet packet) throws IOException;

        Packet read() throws InterruptedException, IOException;

        <T> T make(List<Packet> packets);
    }

    @AllArgsConstructor
    private static class SenderContext {
        int base;
        int retries;
        Packet[] packets;
        boolean isClient;
    }

    @AllArgsConstructor
    private static class ReceiverContext {
        int base;
        int retries;
        List<Packet> packets;
        boolean isClient;
    }

    @Override
    public boolean send(final Agent sender, final Packet[] packets) throws IOException, InterruptedException {
        final SenderContext context = new SenderContext(0, maxConsecutiveRetries(), packets, sender instanceof Client.UDPHandler);
        return sendRetry(sender, context);
    }

    private boolean sendRetry(final Agent sender, final SenderContext context) throws IOException, InterruptedException {
        if (context.retries <= 0) {
            return false;
        }

        if (context.base >= context.packets.length) {
            log.info("base>=packets.length, returning true");
            return true;
        }

        log.info("base={}, packets.length={}", context.base, context.packets.length);
        for (var i = context.base; i <= context.base + windowSize() - 1; i++) {
            if (i >= context.packets.length) {
                log.info("i>=packets.length, break");
                break;
            }

            context.packets[i] = context.packets[i].toBuilder()
                .build();

            if (context.packets[i].is(BFRD)) {
                sender.write(context.packets[i]);
            } else if (context.packets[i].is(TRSM)) {
                log.warn("outgoing packets[{}] skipped due to being already TRSM", i);
            } else {
                log.warn("outgoing packets[{}] is expected to be BFRD {}", i, context.packets[i]);
            }
        }

        final var packet = sender.read();

        if (inSendWindow(sender, packet, context)) {
            if (!packet.is(ACKDATA)) {
                log.warn("expected within window to be ACKDATA {}", packet);
            }

            final int index = index(packet);

            log.info("changing state of packet to {} of {}", TRSM, packet);
            context.packets[index] = packet.toBuilder()
                .state(TRSM)
                .build();

            context.retries = maxConsecutiveRetries();
            slideAndIncrementIfNeeded(context, index);
        }

        log.debug("{}", packetStatesInSequenceSpace(context));

        if (context.base >= context.packets.length) {
            log.info("base>=packets.length, returning={}", true);
            return true;
        } else {
            log.info("base<packets.length, decrementing retries={}", context.retries);
            context.retries--;
        }

        return sendRetry(sender, context);
    }

    @Override
    public <T> T receive(final Agent receiver) throws IOException, InterruptedException {
        final List<Packet> packets = new ArrayList<>(Collections.nCopies(windowSize() * 100, null));
        return receiveRetry(receiver, new ReceiverContext(0, maxConsecutiveRetries(), packets, receiver instanceof Client.UDPHandler));
    }

    private <T> T receiveRetry(final Agent receiver, final ReceiverContext context) throws IOException, InterruptedException {
        log.info("retries={}", context.retries);

        if (context.retries <= 0) {
            log.info("retries=0, returning null");
            return null;
        }

        var packet = receiver.read();

        if (packet == null) {
            log.info(
                "next packet was null, decrementing retries={}, and trying to make request with current packets buffered",
                context.retries);
            context.retries--;

            final T message = receiver.make(context.packets);
            if (message != null) {
                log.info("message returned is non-null, returning found message");
                log.info("message=\n{}", message.toString());
                context.retries = maxConsecutiveRetries();
                if (context.isClient) {
                    log.warn("attempting to unconditionally reply to server that response was received for remaining incoming packets");
                    Packet nextPacket = receiver.read();
                    int retries = 1;
                    while (nextPacket != null || retries-- > 0) {
                        if (nextPacket != null) {
                            acknowledge(receiver, nextPacket);
                        }
                        nextPacket = receiver.read();
                    }
                }

                return message;
            }
        }

        while (packet != null) {
            log.info("reading {}", packet);

            if (shouldAcknowledge(context, packet)) {
                acknowledge(receiver, packet);
            }
            log.info("bufferIdNeeded");
            bufferIfNeeded(context, packet);
            log.info("slideIfNeeded");
            slideAndIncrementIfNeeded(context);
            log.info("reading next packet");
            final var newPacket = receiver.read();
            if (newPacket != null && !newPacket.equals(packet)) {
                context.retries = maxConsecutiveRetries();
                break;
            } else {
                packet = newPacket;
            }
        }

        log.debug("{}", packetStatesInSequenceSpace(context));

        return receiveRetry(receiver, context);
    }

    private void acknowledge(final Agent receiver, final Packet packet) throws IOException {
        log.info("attempting to acknowledge {}", packet);
        receiver.write(packet.toBuilder()
            .state(ACKDATA)
            .build());
        log.info("sent ACKDATA version of {}", packet);
    }

    private String packetStatesInSequenceSpace(final ReceiverContext context) {
        final var windowIndex = context.base + windowSize();
        var s = "";
        for (var i = 0; i < windowIndex + 2; i++) {
            final var p = context.packets.get(i) != null ? context.packets.get(i).state() : "null";
            final var e = i == context.base || i == windowIndex
                ? "(" + p + ")"
                : p;

            if (i == 0) {
                s += "[" + e;
            } else {
                s += "," + e;
            }
        }
        s += "]";
        return s;
    }

    private String packetStatesInSequenceSpace(final SenderContext context) {
        final var windowIndex = context.base + windowSize() - 1;
        var s = "";
        for (var i = 0; i < context.packets.length; i++) {
            final var e = i == context.base || i == windowIndex
                ? "(" + context.packets[i].state() + ")"
                : context.packets[i].state();

            if (i == 0) {
                s += "[" + e;
            } else {
                s += "," + e;
            }
        }
        s += "]";
        return s;
    }

    private int index(final Packet packet) {
        // Should wrap around seqNum space
        return packet == null ? -1 : (int) packet.sequenceNumber();
    }

    private boolean inSendWindow(final Agent sender, final Packet packet, final SenderContext context) throws IOException {
        if (packet == null) {
            return false;
        }
        final int index = index(packet);
        log.info(
            "processing {}, index(packet)={}, base={}",
            packet,
            index,
            context.base);

        final var insideWindowIndices = index >= context.base
            && index <= context.base + windowSize() - 1;

        final var insideWindowIndicesAndIsAckData = insideWindowIndices && packet.is(ACKDATA);

        if (insideWindowIndicesAndIsAckData) {
            log.info("{} inside window and is ACKDATA", packet);
        } else if (insideWindowIndices) {
            log.warn("{} inside window but not ACKDATA", packet);
        } else {
            log.info("{} outside window and is {}", packet, packet.state());
        }

        if (packet.is(BFRD)) {
            log.warn("assuming BFRD packet is from previous sender/receiver exchange and acknowledging it using ACKUNK");
            sender.write(packet.toBuilder().state(ACKUNK).build());
        }

        if (packet.is(ACKUNK) && !context.isClient) {
            log.warn("received ACKUNK in sender phase as server, sending back ACKUNK to indicate client that request already received");
            sender.write(packet);
        }

        if (packet.is(ACKUNK) && context.isClient) {
            log.warn("received ACKUNK in sender phase as client, assuming server received request already!");
            for (var i = 0; i < context.packets.length; i++) {
                context.packets[i] = context.packets[i].toBuilder().state(TRSM).build();
            }
            context.base = context.packets.length;
        }

        return insideWindowIndicesAndIsAckData;
    }

    private void slideAndIncrementIfNeeded(final SenderContext context, final int index) {
        if (index >= context.packets.length) {
            log.info("index={}>packets.length={}", index, context.packets.length);
            return;
        }
        if (index == context.base && context.packets[context.base].is(TRSM)) {
            log.info("index=base={} && packets[{}].is(TRSM) as expected, incrementing base", context.base, context.base);
            context.base++;
            slideAndIncrementIfNeeded(context, index + 1);
        } else if (index == context.base) {
            log.warn(
                "can't slide because index=base={}, but packets[{}].state={}!=TRSM",
                context.base,
                index,
                context.packets[index].state());
        }
    }

    private void slideAndIncrementIfNeeded(final ReceiverContext context) {
        final var packet = context.packets.get(context.base);
        if (packet != null) {
            if (!packet.is(BFRD)) {
                log.info("{} is non-null but non-BFRD", packet);
                log.info("incrementing base (relunctantly) to index={}+1", context.base + 1);
            }
            context.base++;
            slideAndIncrementIfNeeded(context);
        } else {
            log.info("packets[base={}]=null, can't slide yet", context.base);
        }
    }

    private void bufferIfNeeded(final ReceiverContext context, Packet packet) {
        if (shouldBuffer(context, packet)) {
            if (!packet.is(BFRD) && !packet.is(ACKUNK)) {
                log.info("received {} should have been in BFRD state, but was not", packet);
                log.info("skipping non-BFRD {}", packet);
                return;
            }

            if (packet.is(ACKUNK)) {
                log.warn("received ACKUNK, assuming redundant confirmation from unsynched phases {}", packet);
            }

            packet = packet.toBuilder().state(BFRD).build();
            final var index = index(packet);
            final var bufferedPacket = context.packets.get(index);
            if (bufferedPacket == null) {
                context.packets.set(index, packet);
                log.info("{} received and properly buffered", packet);
            } else {
                log.info(
                    "attempting to buffer {} but another packet was found at this index={} with state={}",
                    bufferedPacket,
                    index,
                    bufferedPacket.state());
                log.info("buffered {}", bufferedPacket);
                log.info("received {}", packet);
            }
            context.packets.set(index, packet);
        }
    }

    private boolean inReceiveWindow(final ReceiverContext context, final Packet packet) {
        final var index = index(packet);
        return index >= context.base && index < context.base + windowSize() - 1;
    }

    private boolean alreadyAcknowledged(final ReceiverContext context, final Packet packet) {
        final var index = index(packet);
        return index >= context.base - windowSize() && index <= context.base - 1;
    }

    private boolean shouldAcknowledge(final ReceiverContext context, final Packet packet) {
        return alreadyAcknowledged(context, packet) || inReceiveWindow(context, packet);
    }

    private boolean shouldBuffer(final ReceiverContext context, final Packet packet) {
        return inReceiveWindow(context, packet);
    }
}
