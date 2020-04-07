package httpnio.server;

import httpnio.Const;
import httpnio.common.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static httpnio.common.Packet.State.*;

@SuppressWarnings("squid:S2189")
@Slf4j
public class Server {

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private volatile boolean isRunning = false;

    private final Server.Configuration configuration;

    public Server(final Server.Configuration configuration) {
        this.configuration = configuration;
    }

    public synchronized void run() {
        if (isRunning) {
            return;
        }

        log.info("starting server");
        log.info("using port {}", configuration.port());
        try {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(true);
            channel.socket().bind(new InetSocketAddress(configuration.port()));
            isRunning = true;
            new ServerThread(channel).start();
            log.debug("server started");
        } catch (final IOException e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        isRunning = false;
    }

    class ServerThread extends Thread {

        private final ConcurrentHashMap<SocketAddress, BlockingQueue<Packet>> clientPacketQueueTable = new ConcurrentHashMap<>();

        private final DatagramChannel channel;

        private final ByteBuffer buffer;

        ServerThread(final DatagramChannel channel) {
            super("connection-listener-thread");
            this.channel = channel;
            buffer = transportProtocol() != null ?
                ByteBuffer.allocateDirect(transportProtocol().maxPacketSize())
                    .order(ByteOrder.BIG_ENDIAN) : null;
        }

        @Override
        public void run() {
            while (isRunning) {
                try {
                    if (buffer == null) { // TCP
                        final Socket socket = new ServerSocket(configuration.port()).accept();
                        executorService.execute(handler(socket, null, null, null));
                    } else { // UDP
                        buffer.clear();
                        final SocketAddress client = channel.receive(buffer);
                        buffer.flip();
                        if (client != null) {
                            final Packet packet = Packet.of(buffer);
                            clientPacketQueueTable.putIfAbsent(client, new ArrayBlockingQueue<>(100));
                            final BlockingQueue<Packet> clientPacketQueue = Objects.requireNonNull(
                                clientPacketQueueTable.get(client),
                                "client incoming packet queue should have been present in client table");
                            clientPacketQueue.put(packet);
                            if (packet.is(SYN)) {
                                log.debug("{} wishes to establish connection", client);
                                executorService.execute(handler(null, channel, client, clientPacketQueue));
                            } else {
                                log.debug("received {} added to queue {}", packet, clientPacketQueue);
                            }
                        }
                    }
                } catch (final IOException | InterruptedException e) {
                    log.error(e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private Runnable handler(
        final Socket socket,
        final DatagramChannel channel,
        final SocketAddress client,
        final BlockingQueue<Packet> queue) throws IOException {
        switch (configuration.transportProtocolType()) {
            case UDP:
                return new UDPHandler(channel, client, queue, configuration, transportProtocol(), applicationProtocol());
            case TCP:
                return new TCPHandler(socket, configuration, transportProtocol(), applicationProtocol());
            default:
                throw ErrorFactory.invalidTransportProtocol(configuration.transportProtocolType().name());
        }
    }

    private TransportProtocol transportProtocol() {
        switch (configuration.transportProtocolType()) {
            case UDP:
                return new UDPSRProtocol();
            case TCP:
                return null;
            default:
                throw ErrorFactory.invalidTransportProtocol(configuration.transportProtocolType().name());
        }
    }

    private ApplicationProtocol.Response applicationProtocol() throws IOException {
        switch (configuration.applicationProtocolType()) {
            case FILESERVER:
                return new FileServerProtocol(configuration.directory());
            default:
                throw ErrorFactory.invalidApplicationProtocol(configuration.applicationProtocolType().name());
        }
    }

    private static class UDPHandler implements Runnable {

        DatagramChannel channel;

        DatagramChannel out;

        Selector selector;

        SocketAddress client;

        BlockingQueue<Packet> queue;

        TransportProtocol transportProtocol;

        private final ApplicationProtocol.Response applicationProtocol;

        private final Configuration configuration;

        private int base;

        private final int windowSize;

        private List<Packet> packets;

        public UDPHandler(
            final DatagramChannel channel,
            final SocketAddress client,
            final BlockingQueue<Packet> queue,
            final Configuration configuration,
            final TransportProtocol transportProtocol,
            final ApplicationProtocol.Response applicationProtocol) {
            this.channel = channel;
            this.client = client;
            this.queue = queue;
            this.transportProtocol = transportProtocol;
            this.applicationProtocol = applicationProtocol;
            this.configuration = configuration;
            base = 0;
            windowSize = transportProtocol.windowSize();
        }

        @SneakyThrows
        @Override
        public void run() {
            packets = new ArrayList<>(Collections.nCopies(windowSize * 100, null));
            try {
                configure();
                final boolean successfulHandshake = handshake();

                if (successfulHandshake) {
                    final var request = readRetry();
                    if (request != null) {
                        log.info("request received, preparing response");
                        final var response = applicationProtocol.response(request);
                        log.info("response=\n{}", response.toString());
                    }
                }
            } catch (final Exception e) {
                log.error("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            } finally {
                log.debug("disconnecting");
                if (out.isConnected()) {
                    out.disconnect();
                }
                out.close();
                selector.close();
                log.debug("exiting thread");
            }
        }

        private void configure() throws IOException {
            selector = Selector.open();
            out = DatagramChannel.open();
            out.configureBlocking(false);
            out.connect(client);
            out.register(selector, SelectionKey.OP_READ);
            out.socket().setSoTimeout(1000);
        }

        private boolean handshake() throws InterruptedException, IOException {
            final Packet synPacket = Objects.requireNonNull(
                queue.poll(),
                "synPacket should have been in client queue but none were found");
            log.debug("received syn {}", synPacket);

            final Packet synAckPacket = synPacket.builder()
                .state(SYNACK)
                .sequenceNumber(synPacket.sequenceNumber() + 1)
                .build();

            out.write(synAckPacket.buffer());
            log.debug("sent synack {}", synAckPacket);

            final Packet ackPacket = queue.poll(500, TimeUnit.MILLISECONDS);

            if (ackPacket == null) {
                log.error("did not receive ack after timeout, assuming was sent");
            } else if (!ackPacket.is(ACK)) {
                log.error("received a packet but was not ack, will offer other packet back to queue");
                final var offeredToQueue = queue.offer(ackPacket, 500, TimeUnit.MILLISECONDS);
                log.info("{} was successfully to queue", offeredToQueue);
            } else {
                log.debug("received ackPacket {}", ackPacket);
                log.debug("connection established");
            }

            return true;
        }

        private int index(final Packet packet) {
            // Should wrap around seqNum space
            return packet == null ? -1 : (int) packet.sequenceNumber();
        }

        private boolean inWindow(final Packet packet) {
            final var index = index(packet);
            return index >= base && index < base + windowSize - 1;
        }

        private boolean alreadyAcknowledged(final Packet packet) {
            final var index = index(packet);
            return index >= base - windowSize && index <= base - 1;
        }

        private boolean shouldAcknowledge(final Packet packet) {
            return alreadyAcknowledged(packet) || inWindow(packet);
        }

        private boolean shouldBuffer(final Packet packet) {
            return inWindow(packet);
        }

        private void bufferIfNeeded(Packet packet) {
            if (shouldBuffer(packet)) {
                if (!packet.is(BFRD)) {
                    log.info("received {} should have been in BFRD state, but was not", packet);
                    log.info("non-BFRD {}", packet);
                }
                packet = packet.builder().state(BFRD).build();
                final var index = index(packet);
                final var bufferedPacket = packets.get(index);
                if (bufferedPacket == null) {
                    packets.set(index, packet);
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
                packets.set(index, packet);
            }
        }

        private void slideIfNeeded() {
            final var packet = packets.get(base);
            if (packet != null) {
                if (packet.is(BFRD)) {
                    base++;
                    slideIfNeeded();
                } else {
                    log.info("{} is non-null but non-BFRD", packet);
                    log.info("incrementing base (relunctantly) to index={}+1", base + 1);
                    base++;
                    slideIfNeeded();
                }
            } else {
                log.info("packets[{}=base]=null, can't slide yet", base);
            }
        }

        private Packet read(final int timeout) throws InterruptedException {
            final Packet packet = queue.poll(timeout, TimeUnit.MILLISECONDS);
            log.info("polled {}", packet);
            return packet;
        }

        private Packet read() throws InterruptedException {
            return read(transportProtocol.packetTimeoutMs());
        }

        private void write(final Packet packet) throws IOException {
            out.write(packet.buffer());
            log.info("sent {}", packet);
        }

        private HTTPRequest readRetry(int retries) throws IOException, InterruptedException {
            log.info("retries={}", retries);

            if (retries <= 0) {
                log.info("retries=0, returning null");
                return null;
            }

            var packet = read();

            if (packet == null) {
                log.info(
                    "next packet was null, decrementing retries={}, and trying to make request with current packets buffered", retries);
                retries--;

                final var request = makeRequest();
                if (request != null) {
                    log.info("request returned is non-null, returning found request");
                    return request;
                }
            }

            while (packet != null) {
                log.info("reading {}", packet);
                if (shouldAcknowledge(packet)) {
                    log.info("attempting to acknowledge {}", packet);
                    write(packet.builder().state(ACKDATA).build()); // Want to send ACKDATA but store BFRD
                    log.info("sent ACKDATA version of {}", packet);
                }
                log.info("bufferIdNeeded");
                bufferIfNeeded(packet);
                log.info("slideIfNeeded");
                slideIfNeeded();
                log.info("reading next packet");
                packet = read();
            }

            return readRetry(retries);
        }

        private HTTPRequest readRetry() throws IOException, InterruptedException {
            return readRetry(10);
        }

        private HTTPRequest makeRequest() {
            final var combinedPayload = packets.stream().filter(Objects::nonNull).map(Packet::payload).collect(Collectors.joining(""));
            try {
                final var request = HTTPRequest.of(combinedPayload);
                if (request != null) {
                    log.info("request successfully created");
                } else {
                    log.error("request returned null, tread will exit");
                }
                return request;
            } catch (final Exception e) {
                log.error("{}: {}", e.getClass().getSimpleName(), e.getMessage());
                return null;
            }
        }
    }

    private static class TCPHandler implements Runnable {

        private final Socket socket;

        private final TransportProtocol transportProtocol;

        private final ApplicationProtocol.Response applicationProtocol;

        private final Server.Configuration configuration;

        public TCPHandler(
            final Socket socket,
            final Configuration configuration,
            final TransportProtocol transportProtocol,
            final ApplicationProtocol.Response applicationProtocol) {
            this.socket = socket;
            this.transportProtocol = transportProtocol;
            this.applicationProtocol = applicationProtocol;
            this.configuration = configuration;
        }

        @SneakyThrows
        @Override
        public void run() {
            try (final PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                log.debug("connection accepted");
                boolean keepAlive = true;

                while (keepAlive) {
                    HTTPRequest request = null;
                    final StringBuilder requestBuilder = new StringBuilder();
                    String line;

                    while ((line = in.readLine()) != null) {
                        requestBuilder.append(line);
                        requestBuilder.append(Const.CRLF);

                        if (line.equalsIgnoreCase("")) {
                            request = HTTPRequest.of(requestBuilder.toString());

                            if (request.headers().containsKey(Const.Headers.CONTENT_LENGTH)) {
                                final StringBuilder body = new StringBuilder();
                                final int contentLength = Integer.parseInt(request.headers().get(Const.Headers.CONTENT_LENGTH));
                                for (var i = 0; i < contentLength; i++) {
                                    body.append((char) in.read());
                                }
                                request = HTTPRequest.builder()
                                    .url(request.url().toString())
                                    .method(request.method())
                                    .headers(request.headers())
                                    .body(body.toString())
                                    .build();
                            }

                            break;
                        }
                    }
                    if (request != null) {
                        log.debug("request:");
                        log.debug(request.toString());
                        final String responseText = applicationProtocol.response(request).toString();
                        log.debug("response:");
                        log.debug(responseText);
                        out.print(responseText);
                        out.flush();

                        if (request.headers().containsKey(Const.Headers.CONNECTION)) {
                            final String connection = request.headers().get(Const.Headers.CONNECTION);
                            keepAlive = connection.equalsIgnoreCase("keep-alive");
                        } else {
                            keepAlive = false;
                        }
                    } else {
                        log.debug("connection terminated");
                        keepAlive = false;
                    }
                }
            } catch (final IOException | HTTPRequest.RequestError e) {
                e.printStackTrace();
            } finally {
                if (!socket.isClosed()) {
                    socket.close();
                }
            }
        }
    }

    @AllArgsConstructor
    @Getter
    @Accessors(fluent = true)
    static final class Configuration {
        private final TransportProtocol.Type transportProtocolType;

        private final ApplicationProtocol.Type applicationProtocolType;

        private final int port;

        private final boolean verbose;

        private final String directory;

        public final int port() {
            return port == 0 || port == -1
                ? Const.DEFAULT_SERVER_PORT
                : port;
        }

        public final int threadPoolSize() {
            return Const.DEFAULT_THREAD_POOL_SIZE;
        }
    }
}
