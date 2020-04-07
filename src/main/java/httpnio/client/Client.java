package httpnio.client;

import httpnio.Const;
import httpnio.common.*;
import httpnio.common.HTTPRequest.RequestError;
import io.vavr.control.Try;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.util.Scanner;
import java.util.concurrent.*;

import static httpnio.common.Packet.State.*;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class Client {

    TransportProtocol transportProtocol;

    public Client(final TransportProtocol transportProtocol) {
        this.transportProtocol = transportProtocol;
    }

    public HTTPResponse request(final HTTPRequest request) throws RequestError {
        final Callable<HTTPResponse> task = dispatch(request);
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<HTTPResponse> future = executorService.submit(task);
        executorService.shutdown();

        try {
            return future.get(Const.TIMEOUT_LIMIT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException ie) {
            throw new RequestError("InterruptedException: The task was interrupted. Please ensure the request is valid.\n" + (ie.getMessage() != null ? ie
                .getMessage() : ""));
        } catch (final ExecutionException ee) {
            throw new RequestError(
                "ExecutionException: An error occurred during the execution of the task. Please ensure the request is valid.\n" + (ee.getMessage() != null ? ee
                    .getMessage() : ""));
        } catch (final TimeoutException te) {
            throw new RequestError("TimeoutException: The request was not received after " + Const.TIMEOUT_LIMIT_SECONDS + " seconds. Please ensure the request is valid or retrieve a smaller payload.\n" + (te
                .getMessage() != null ? te.getMessage() : ""));
        } catch (final Exception e) {
            throw new RequestError(e.getClass()
                .getSimpleName() + ": An unknown error occurred. Please ensure the request is valid.\n" + (e.getMessage() != null ? e
                .getMessage() : ""));
        }
    }

    private Callable<HTTPResponse> dispatch(final HTTPRequest request) throws RequestError {
        try {
            switch (TransportProtocol.Type.of(request.url().protocol())) {
                case TCP:
                    return new TCPHandler(request, transportProtocol);
                case UDP:
                    return new UDPHandler(request, transportProtocol);
                default:
                    throw new RequestError("URL protocol invalid: expected 'https', 'http', or 'udp', but got '" + request.url()
                        .protocol() + "'\n");
            }
        } catch (final Exception e) {
            throw new RequestError(e.getClass().getSimpleName() + ": " + e.getMessage() + "\n\nRequest: \n" + request.toString() + "\n");
        }
    }

    private static class TCPHandler implements Callable<HTTPResponse> {

        private final HTTPRequest request;

        private final TransportProtocol transportProtocol;

        public TCPHandler(final HTTPRequest request, final TransportProtocol transportProtocol) throws IOException {
            this.request = request;
            this.transportProtocol = transportProtocol;
        }

        @Override
        public HTTPResponse call() throws IOException {
            return callHelper(request);
        }

        private HTTPResponse callHelper(final HTTPRequest request) throws IOException {
            final StringBuilder message = new StringBuilder();

            try (final Socket socket = new Socket(request.host(), request.url().port());
                 final PrintWriter writer = new PrintWriter(socket.getOutputStream());
                 final Scanner reader = new Scanner(new InputStreamReader(socket.getInputStream()))) {

                writer.print(request.toString());
                writer.flush();

                while (reader.hasNextLine()) {
                    final String line = reader.nextLine();
                    message.append(String.format("%n%s", line));
                }
                log.info("payload is: {} in bytes", message.toString().getBytes(UTF_8).length);
                HTTPResponse response = new HTTPResponse(request, message.toString());

                if (response.statusCode() != null && response.statusCode().matches("3\\d+")) {
                    final String location = response.headers().get("Location");
                    final InetLocation redirectInetLocation = Try.of(() -> InetLocation.fromSpec(location))
                        .getOrElse(() ->
                            Try.of(() -> request.url().toBuilder().path(location).build())
                                .getOrElse(() -> null));

                    response = callHelper(request.toBuilder().inetLocation(redirectInetLocation).build());
                }

                return response;
            }
        }
    }

    private static class UDPHandler implements Callable<HTTPResponse> {

        private final HTTPRequest request;

        private final TransportProtocol transportProtocol;

        private final DatagramChannel channel;

        private final Selector selector;

        final Packet[] packets;

        final int windowSize;

        int base;

        public UDPHandler(final HTTPRequest request, final TransportProtocol transportProtocol) throws IOException {
            this.request = request;
            this.transportProtocol = transportProtocol;
            channel = DatagramChannel.open();
            selector = Selector.open();
            packets = Packet.of(request).packets();
            windowSize = transportProtocol.windowSize();
            base = 0;

            channel.configureBlocking(false);
            channel.register(selector, OP_READ);
            channel.connect(request.routerAddress());

            for (var i = 0; i < packets.length; i++) {
                packets[i] = packets[i].builder()
                    .peerAddress(request.socketAddress())
                    .routerAddress(request.routerAddress())
                    .state(BFRD)
                    .sequenceNumber(i)
                    .build();
            }
        }

        @SneakyThrows
        @Override
        public HTTPResponse call() {
            try {
                if (!handshake()) {
                    log.error("unable to handshake");
                }

                if (!pipeline(10)) {
                    log.error("request was not successfully sent to server");
                } else {
                    log.info("request was successfully sent to server!");
                }

                return null; //new HTTPResponse(request, request.toString());
            } catch (final Exception e) {
                log.error("{}", e.getMessage());
                e.printStackTrace();
                throw e;
            } finally {
                log.info("closing connection");
                channel.disconnect();
                channel.close();
                selector.close();
            }
        }

        private int index(final Packet packet) {
            // Should wrap around seqNum space
            return packet == null ? -1 : (int) packet.sequenceNumber();
        }

        private boolean inWindow(final Packet packet) {
            if (packet == null) {
                return false;
            }
            if (packet.sequenceNumber() >= base
                && packet.sequenceNumber() <= base + windowSize - 1
                && !packet.is(ACKDATA)) {
                log.warn("{} inside window but not ACKDATA", packet);
            }
            if (packet.sequenceNumber() >= base
                && packet.sequenceNumber() <= base + windowSize - 1
                && packet.is(ACKDATA)) {
                log.info("{} inside window and is ACKDATA", packet);
            } else {
                log.info("{} outside window and is {}", packet, packet.state());
            }
            return packet.sequenceNumber() >= base
                && packet.sequenceNumber() <= base + windowSize - 1
                && packet.is(ACKDATA);
        }

        private void slideAndIncrementIfNeeded(final int index) {
            if (index >= packets.length) {
                log.info("index>=packets.length");
                return;
            }
            if (index == base && packets[base].is(TRSM)) {
                log.info("index=base={} && packets[{}].is(TRSM) as expected, incrementing base", base, base);
                base++;
                slideAndIncrementIfNeeded(base);
            } else if (index == base) {
                log.warn(
                    "can't slide because index=base={}, but packets[{}].state={}!=TRSM",
                    base,
                    index,
                    packets[index].state());
            }
        }

        private Packet read() throws IOException {
            final ByteBuffer buffer = ByteBuffer.allocate(Packet.MAX_LEN);
            channel.read(buffer);
            buffer.flip();
            final Packet packet = Packet.of(buffer);
            log.info("received {}", packet);
            return packet;
        }

        private void write(final Packet packet) throws IOException {
            final Packet writablePacket = packet.builder()
                .peerAddress(request.socketAddress())
                .routerAddress(request.routerAddress())
                .build();
            channel.write(writablePacket.buffer());
            log.info("sent {}", writablePacket);
        }

        private int wait(final int timeout) throws IOException {
            log.info("blocking up to {}ms for response", timeout);
            final int nReady = selector.select(timeout);
            final var keys = selector.selectedKeys();

            if (keys.isEmpty()) {
                log.warn("no response after timeout");
            }
            keys.clear();

            return nReady;
        }

        private int wait0() throws IOException {
            return wait(transportProtocol.packetTimeoutMs());
        }

        private Packet readRetry(int attempts) throws IOException {
            final int nReady = wait0();
            return nReady > 0 ? read() : (attempts-- > 0 ? readRetry(attempts) : null);
        }

        private void writeRetry(int attempts, final Packet packet) throws IOException {
            write(packet);
            if (attempts-- > 0) {
                writeRetry(attempts, packet);
            }
        }

        private Packet readWriteRetry(int attempts, final Packet out) throws IOException {
            writeRetry(0, out);
            final Packet in = readRetry(0);
            return in != null ? in : (attempts-- > 0 ? readWriteRetry(attempts, out) : null);
        }

        private boolean handshake() throws IOException {
            log.info("initiating");
            final Packet synPacket = Packet.of()
                .state(SYN)
                .sequenceNumber(0)
                .build();

            write(synPacket);
            log.info("syn.is(SYN)={}", synPacket.is(SYN));

            Packet synAckPacket = readRetry(0);
            synAckPacket = synAckPacket != null ? synAckPacket : readWriteRetry(3, synPacket);

            if (synAckPacket != null) {
                log.info("synack.is(SYNACK)={}", synAckPacket.is(SYNACK));

                final Packet ackPacket = synAckPacket.builder()
                    .state(ACK)
                    .sequenceNumber(synAckPacket.sequenceNumber() + 1)
                    .build();

                writeRetry(0, ackPacket);
                log.info("ack.is(ACK)={}", ackPacket.is(ACK));

                return true;
            } else {
                log.error("unable to handshake, synack never received after 3 tries");
                log.error("handshake");
                return false;
            }
        }

        private boolean pipeline(int remainingRetries) throws IOException {
            if (remainingRetries <= 0) {
                return false;
            }

            if (base >= packets.length) {
                log.info("base>=packets.length, returning true");
                return true;
            }

            log.info("base={}, packets.length={}", base, packets.length);
            for (var i = base; i <= base + windowSize - 1; i++) {
                if (i >= packets.length) {
                    log.info("i>=packets.length, returning true");
                    return true;
                }

                packets[i] = packets[i].builder()
                    .peerAddress(request.socketAddress())
                    .routerAddress(request.routerAddress())
                    .build();

                if (packets[i].is(BFRD)) {
                    write(packets[i]);
                } else {
                    log.info("outgoing packets[{}]={} is expected tp be BFRD", i, packets[i]);
                }
            }

            final var readAtLeastOnePacket = readWindow();
            if (readAtLeastOnePacket > 0) {
                log.info("readWindow()>0, keep going!");
            } else if (readAtLeastOnePacket == -1) {
                log.info("readWindow()=-1, reached end of packet range, returning true");
                return true;
            } else {
                log.info("readWindow()<=0, decrementing retries={}", remainingRetries);
                remainingRetries--;
            }

            return pipeline(remainingRetries);
        }

        private int readWindow() throws IOException {
            var readyTotal = 0;
            var readyLastIteration = wait0();
            readyTotal += readyLastIteration;

            while (readyLastIteration > 0) { // try read() to return null for fun
                final var packet = read();
                final int idx = index(packet);
                log.info(
                    "readWindow: processing {}, index(packet)={}, base={}",
                    packet,
                    idx,
                    base);

                if (inWindow(packet)) {
                    if (!packet.is(ACKDATA)) {
                        log.warn("expected {} within window to be ACKDATA", packet);
                    }

                    log.info("changing state of {} to {}", packet, TRSM);
                    packets[idx] = packet.builder()
                        .state(TRSM)
                        .build();

                    slideAndIncrementIfNeeded(idx);
                } else if (base >= packets.length) {
                    log.warn("base>=packets.length, returning -1 to indicate this");
                    return -1;
                } else {
                    log.warn("received {} outside window", packet);
                }

                readyLastIteration = wait0();
                readyTotal += readyLastIteration;
            }

            return readyTotal;
        }
    }
}
