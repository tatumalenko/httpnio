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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static httpnio.common.Packet.State.*;
import static java.nio.channels.SelectionKey.OP_READ;

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
            if (transportProtocol instanceof UDPSRProtocol) {
                return new UDPHandler(request, transportProtocol);
            } else {
                return new TCPHandler(request, transportProtocol);
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
            log.info("client started in TCP mode");
            return callHelper(request);
        }

        private HTTPResponse callHelper(final HTTPRequest request) throws IOException {
            final StringBuilder message = new StringBuilder();

            try (final Socket socket = new Socket(request.host(), request.url().port());
                 final PrintWriter writer = new PrintWriter(socket.getOutputStream());
                 final Scanner reader = new Scanner(new InputStreamReader(socket.getInputStream()))) {

                log.debug(request.toString());
                writer.print(request.toString());
                writer.flush();

                while (reader.hasNextLine()) {
                    final String line = reader.nextLine();
                    message.append(String.format("%s%s", line, Const.CRLF));
                }

                final var responseAttempt = HTTPResponse.of(request, message.toString());
                HTTPResponse response = responseAttempt.isLeft() ? responseAttempt.getLeft() : null;

                if (response != null && response.statusCode() != null && response.statusCode().matches("3\\d+")) {
                    final String location = response.headers().get("Location");
                    final InetLocation redirectInetLocation = Try.of(() -> InetLocation.fromSpec(location))
                        .getOrElse(() ->
                            Try.of(() -> request.url().toBuilder().path(location).build())
                                .getOrElse(() -> null));

                    response = callHelper(request.toBuilder().inetLocation(redirectInetLocation).build());
                }

                if (responseAttempt.isRight()) {
                    throw new IOException(responseAttempt.get());
                } else {
                    return response;
                }
            }
        }
    }

    public static class UDPHandler implements Callable<HTTPResponse>, UDPSRProtocol.Agent {

        private final HTTPRequest request;

        private final TransportProtocol transportProtocol;

        private final InetSocketAddress router;

        private final InetSocketAddress server;

        private final DatagramChannel channel;

        private final Selector selector;

        public UDPHandler(final HTTPRequest request, final TransportProtocol transportProtocol) throws IOException {
            this.request = request;
            router = request.routerAddress();
            server = request.socketAddress();
            this.transportProtocol = transportProtocol;
            channel = DatagramChannel.open();
            selector = Selector.open();
            channel.configureBlocking(false);
            channel.register(selector, OP_READ);

            log.debug("client started in UDP mode");
        }

        @SneakyThrows
        @Override
        public HTTPResponse call() {
            try {
                if (handshake()) {
                    final var buffers = PacketUtil.split(request.toString().getBytes());
                    final Packet[] packets = new Packet[buffers.length];
                    for (int i = 0; i < buffers.length; i++) {
                        packets[i] = Packet.builder()
                            .state(BFRD)
                            .sequenceNumber(i)
                            .payload(buffers[i].array())
                            .build();
                    }
                    if (!transportProtocol.send(this, packets)) {
                        log.error(
                            "unable to successfully send request to server after {} attempts",
                            transportProtocol.maxConsecutiveRetries());
                        log.info("request={}", request.toString().length() > 0 ? "\n" + request.toString() : "");
                    } else {
                        log.info("request was successfully sent to server!");
                        log.info("request={}", request.toString().length() > 0 ? "\n" + request.toString() : "");
                        final HTTPResponse response = transportProtocol.receive(this);
                        if (response != null) {
                            log.info("response successfully received, terminating client");
                            log.info("response={}", response.toString().length() > 0 ? "\n" + response.toString() : "");
                            return response;
                        }
                    }
                } else {
                    log.error("unable to handshake");
                }
                return null;
            } catch (final Exception e) {
                log.error("{}", e.getMessage());
                e.printStackTrace();
                throw e;
            } finally {
                log.info("closing connection");
                channel.close();
                selector.close();
            }
        }

        @Override
        public Packet read() throws IOException {
            final ByteBuffer buffer = PacketUtil.emptyBuffer();
            final int nReady = wait0();
            channel.receive(buffer);
            buffer.flip();

            if (nReady > 0) {
                final Packet packet = Packet.of(buffer);
                log.info("received {}", packet);
                return packet;
            } else {
                return null;
            }
        }

        @Override
        public void write(final Packet packet) throws IOException {
            final Packet writablePacket = packet.toBuilder()
                .peerAddress(server)
                .build();
            channel.send(writablePacket.buffer(), router);

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
            final var packet = read();
            return packet == null && attempts-- > 0 ? readRetry(attempts) : packet;
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
            return in == null && attempts-- > 0 ? readWriteRetry(attempts, out) : in;
        }

        private boolean handshake() throws IOException {
            log.info("initiating handshake");
            final Packet synPacket = Packet.builder()
                .state(SYN)
                .sequenceNumber(0)
                .peerAddress(server)
                .build();

            write(synPacket);
            log.info("packet.is(SYN)={}", synPacket.is(SYN));

            Packet synAckPacket = readRetry(3);
            synAckPacket = synAckPacket != null ? synAckPacket : readWriteRetry(transportProtocol.maxConsecutiveRetries(), synPacket);

            if (synAckPacket != null) {
                log.info("packet.is(SYNACK)={}", synAckPacket.is(SYNACK));

                final Packet ackPacket = synAckPacket.toBuilder()
                    .state(ACK)
                    .sequenceNumber(synAckPacket.sequenceNumber() + 1)
                    .peerAddress(server)
                    .build();

                writeRetry(0, ackPacket);
                log.info("packet.is(ACK)={}", ackPacket.is(ACK));
                return true;
            } else {
                log.error("unable to handshake, synack never received after {} tries", transportProtocol.maxConsecutiveRetries());
                return false;
            }
        }

        @Override
        public <T> T make(final List<Packet> packets) {
            final var combinedPayload = packets.stream()
                .filter(Objects::nonNull)
                .map(Packet::payload)
                .collect(Collectors.joining(""));

            try {
                final var response = HTTPResponse.of(request, combinedPayload);
                if (response.isLeft()) {
                    log.info("response successfully created");
                    return (T) response.getLeft();
                } else {
                    log.info("response={}", combinedPayload.length() > 0 ? "\n" + combinedPayload : "");
                    log.error("response invalid: {}", response.get());
                    return null;
                }
            } catch (final Exception e) {
                log.error("{}: {}", e.getClass().getSimpleName(), e.getMessage());
                log.debug("response={}", combinedPayload.length() > 0 ? "\n" + combinedPayload : "");
                return null;
            }
        }
    }
}
