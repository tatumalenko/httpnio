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
                handshake();
                final boolean requestWasSent = pipeline(10);
                if (!requestWasSent) {
                    log.error("Request was not successfully sent to server");
                } else {
                    log.info("Request was successfully sent to server!");
                }

                return null; //new HTTPResponse(request, request.toString());
            } catch (final Exception e) {
                log.error("{}", e.getMessage());
                e.printStackTrace();
                throw e;
            } finally {
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
                log.warn("Packet was inside window but non-ACKDATA state was {}", packet.state());
            }
            if (packet.sequenceNumber() >= base
                && packet.sequenceNumber() <= base + windowSize - 1
                && packet.is(ACKDATA)) {
                log.info("Packet #{} in inside window and is ACKDATA", packet.sequenceNumber());
            } else {
                log.info("Packet #{} in outside window and is {}", packet.sequenceNumber(), packet.state());
            }
            return packet.sequenceNumber() >= base
                && packet.sequenceNumber() <= base + windowSize - 1
                && packet.is(ACKDATA);
        }

        private void slideAndIncrementIfNeeded(final int index) {
            if (index >= packets.length) {
                log.info("slideAndIncrementIfNeeded: index >= packets.length, returning");
                return;
            }
            if (index == base && packets[base].is(TRSM)) {
                log.info("slideAndIncrementIfNeeded: index == base == {} && packets[base].is(TRSM), incrementing base", base);
                base++;
                slideAndIncrementIfNeeded(base);
            }
        }

        private Packet read() throws IOException {
            final ByteBuffer buffer = ByteBuffer.allocate(Packet.MAX_LEN);
            channel.read(buffer);
            buffer.flip();
            final Packet packet = Packet.of(buffer);
            //log.info("Received packet: {}", packet);
            log.info("Received packet: #{}, state: {}, size: {}", packet.sequenceNumber(), packet.state(), packet.payload().length());
            return packet;
        }

        private void write(final Packet packet) throws IOException {
            final Packet writablePacket = packet.builder()
                .peerAddress(request.socketAddress())
                .routerAddress(request.routerAddress())
                .build();
            channel.write(writablePacket.buffer());
            log.info(
                "Sent packet: #{}, state: {}, size: {}",
                writablePacket.sequenceNumber(),
                writablePacket.state(),
                writablePacket.payload().length());
            //log.info("Sent packet: {}", writablePacket);
        }

        private int wait(final int timeout) throws IOException {
            log.info("Waiting for the response");
            final int nReady = selector.select(timeout);
            final var keys = selector.selectedKeys();

            if (keys.isEmpty()) {
                log.error("No response after timeout");
            }
            keys.clear();

            return nReady;
        }

        private int wait0() throws IOException {
            return wait(transportProtocol.packetTimeoutMs());
        }

        private void handshake() throws IOException {
            final Packet synPacket = Packet.of()
                .state(SYN)
                .sequenceNumber(0)
                .build();

            write(synPacket);
            log.info("synPacket.is(SYN): {}", synPacket.is(SYN));

            wait0();

            final Packet synAckPacket = read();
            log.info("synAckPacket.is(SYNACK): {}", synAckPacket.is(SYNACK));

            final Packet ackPacket = synAckPacket.builder()
                .state(ACK)
                .sequenceNumber(synAckPacket.sequenceNumber() + 1)
                .build();

            write(ackPacket);
            log.info("ackPacket.is(ACK): {}", ackPacket.is(ACK));
            //log.info("buffersToString() == request.toString(): {}", Packet.of(request).buffersToString().equals(request.toString()));
        }

        private boolean pipeline(int remainingRetries) throws IOException {
            if (remainingRetries <= 0) {
                return false;
            }

            if (base >= packets.length) {
                log.info("pipeline: base >= packets.length, returning true");
                return true;
            }

            log.info("base: {}, packets.length: {}", base, packets.length);
            for (var i = base; i <= base + windowSize - 1; i++) {
                if (i >= packets.length) {
                    log.info("pipeline: i >= packets.length, returning true");
                    return true;
                }

                packets[i] = packets[i].builder()
                    .peerAddress(request.socketAddress())
                    .routerAddress(request.routerAddress())
                    .build();

                if (packets[i].is(BFRD)) {
                    write(packets[i]);
                } else {
                    log.info("pipeline: packets[i] != BFRD but instead: " + packets[i].state());
                    log.info("pipeline: packets[i] = " + packets[i]);
                }
            }

            final var readAtLeastOnePacket = readWindow();
            if (readAtLeastOnePacket > 0) {
                log.info("pipeline: readWindow() > 0, keep going!");
            } else if (readAtLeastOnePacket == -1) {
                log.info("pipeline: readWindow() == -1, reached end of packet range! Returning true.");
                return true;
            } else {
                log.info("pipeline: readWindow() was not > 0 or == -1, decrementing retries.");
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

                if (inWindow(packet)) {
                    if (!packet.is(ACKDATA)) {
                        log.warn("Expected read packet to be ACKDATA but was {}", packet.state());
                    }

                    log.info("Changing state of packet #{} from {} to {}", packet.sequenceNumber(), packet.state(), TRSM);
                    packets[idx] = packet.builder()
                        .state(TRSM)
                        .build();

                    slideAndIncrementIfNeeded(idx);
                } else if (base >= packets.length) {
                    log.warn("Base index greater than packets.length, all packets sent and acknowledged!");
                    return -1;
                } else {
                    log.warn("Received packet #{} outside window", packet.sequenceNumber());
                }

                readyLastIteration = wait0();
                readyTotal += readyLastIteration;
            }

            return readyTotal;
        }
    }
}
