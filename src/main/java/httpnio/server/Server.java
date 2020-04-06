package httpnio.server;

import httpnio.Const;
import httpnio.common.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static httpnio.common.Packet.State.*;

@SuppressWarnings("squid:S2189")
public class Server {

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private volatile boolean isRunning = false;

    private final Server.Configuration configuration;

    private static Server.Logger log;

    public Server(final Server.Configuration configuration) {
        this.configuration = configuration;
        if (configuration.verbose()) {
            log = new Logger("main-server-thread");
        }
    }

    public synchronized void run() {
        if (isRunning) {
            return;
        }

        log.debug("Starting server");
        log.debug("Using port " + configuration.port());
        try {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(true);
            channel.socket().bind(new InetSocketAddress(configuration.port()));
            isRunning = true;
            new ServerThread(channel).start();
            log.debug("Server started");
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
                                "Client incoming packet queue should have been present in client table");
                            clientPacketQueue.put(packet);

                            if (packet.is(SYN)) {
                                log.debug(client + " wishes to establish connection");
                                executorService.execute(handler(null, channel, client, clientPacketQueue));
                            } else {
                                log.debug("Received packet was added to their respective queue");
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

        private Logger log;

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
            log = new Logger(Server.log, Thread.currentThread().getName());
            packets = new ArrayList<>(Collections.nCopies(windowSize * 100, null));
            try {
                configure();
                final boolean successfulHandshake = handshake();

                if (successfulHandshake) {
                    final var request = readRetry();

                    if (request != null) {
                        log.info("");
                    }
                }
            } catch (final Exception e) {
                e.printStackTrace(log.err);
                log.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                log.debug("Disconnecting");
                if (out.isConnected()) {
                    out.disconnect();
                }
                out.close();
                selector.close();
                log.debug("Exiting thread");
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
            log.debug("Received synPacket: " + synPacket);

            final Packet synAckPacket = synPacket.builder()
                .state(SYNACK)
                .sequenceNumber(synPacket.sequenceNumber() + 1)
                .build();

            out.write(synAckPacket.buffer());
            log.debug("Sent synAckPacket: " + synAckPacket);

            Packet ackPacket = queue.poll(1, TimeUnit.SECONDS);
            final boolean badAckPacket = true;

            while (badAckPacket) {
                if (ackPacket == null) {
                    log.error("Did not receive a ackPacket after timeout");
                } else if (!ackPacket.is(ACK)) {
                    log.error("Received a packet but was not an ACK packet, will offer back to queue");
                    final var offeredToQueue = queue.offer(ackPacket, 1, TimeUnit.SECONDS);
                    log.info("Packet was successfully to queue: " + offeredToQueue);
                } else {
                    log.debug("Received ackPacket: " + ackPacket);
                    log.debug("Connection established");
                    break;
                }
                ackPacket = queue.poll(1, TimeUnit.SECONDS);
            }

            final var successfulHandshake = ackPacket != null && ackPacket.is(ACK);
            if (successfulHandshake) {
                log.info("Handshake successful!");
            } else {
                log.error("Handshake was unsuccessful. Thread will exit.");
            }

            return successfulHandshake;
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
                    log.info("WARNING: Received packet should be in BFRD state, but was " + packet.state());
                    log.info("Non BFRD packet: " + packet);
                }
                packet = packet.builder().state(BFRD).build();
                final var index = index(packet);
                final var bufferedPacket = packets.get(index);
                if (bufferedPacket == null) {
                    packets.set(index, packet);
                    log.info("Received packet was properly buffered: " + packet);
                } else {
                    log.info("WARNING: Attempting to buffer packet but another packet was found at this index = " + index + "with state = " + bufferedPacket
                        .state());
                    log.info("Buffered packet: " + bufferedPacket);
                    log.info("Received packet: " + packet);
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
                    log.info("WARNING: base packet was non-null and not in BFRD state, but was instead in " + packet.state());
                    log.info("Non-null and non-BFRD packet: " + packet);
                    log.info("WARNING: Incrementing base relunctantly to index = " + base + 1);
                    base++;
                    slideIfNeeded();
                }
            } else {
                log.info("Packet at base (" + base + ") is still null, can't slide yet");
            }
        }

        private Packet read(final int timeout) throws InterruptedException {
            final Packet packet = queue.poll(timeout, TimeUnit.MILLISECONDS);
            log.info("Polled packet: " + packet);
            return packet;
        }

        private Packet read() throws InterruptedException {
            return read(transportProtocol.packetTimeoutMs());
        }

        private Packet readNow() {
            final Packet packet = queue.poll();
            log.info("Polled packet instantly: " + packet);
            return packet;
        }

        private void write(final Packet packet) throws IOException {
            out.write(packet.buffer());
            log.debug("Sent packet: " + packet);
        }

        private HTTPRequest readRetry(int remainingRetries) throws IOException, InterruptedException {
            log.info("readRetry: remainingRetries = " + remainingRetries);

            if (remainingRetries <= 0) {
                log.info("readRetry: remainingRetries = 0. Returning null.");
                return null;
            }

            var packet = read();

            if (packet == null) {
                log.info(
                    "readRetry: next packet was null, decrementing remainingRetries and trying to make request with current packets buffered");
                remainingRetries--;

                final var request = makeRequest();
                if (request != null) {
                    log.info("readRetry: request returned is non-null, returning found request");
                    return request;
                }
            }

            while (packet != null) {
                log.info("readRetry: read a packet");
                if (shouldAcknowledge(packet)) {
                    log.info("readRetry: attempting to acknowledge packet");
                    write(packet.builder().state(ACKDATA).build()); // Want to send ACKDATA but store BFRD
                    log.info("readRetry: packet sent");
                }
                log.info("readRetry: bufferIdNeeded");
                bufferIfNeeded(packet);
                log.info("readRetry: slideIfNeeded");
                slideIfNeeded();
                log.info("readRetry: reading next packet");
                packet = read();
            }

            return readRetry(remainingRetries);
        }

        private HTTPRequest readRetry() throws IOException, InterruptedException {
            return readRetry(10);
        }

        private HTTPRequest makeRequest() {
            final var combinedPayload = packets.stream().filter(Objects::nonNull).map(Packet::payload).collect(Collectors.joining(""));
            try {
                final var request = HTTPRequest.of(combinedPayload);
                if (request != null) {
                    log.info("Request successfully created: " + request.toString().substring(0, Math.min(20, request.toString().length())));
                } else {
                    log.error("ERROR: Request returned null. Thread will exit.");
                }
                return request;
            } catch (final Exception e) {
                log.error("ERROR: Making request from combined payload. Returning null.");
                log.error("Combined payload: " + combinedPayload);
                log.error(e.getClass().getSimpleName() + ": " + e.getMessage());
                return null;
            }
        }
    }

    private static class TCPHandler implements Runnable {

        private final Socket socket;

        private final TransportProtocol transportProtocol;

        private final ApplicationProtocol.Response applicationProtocol;

        private final Server.Configuration configuration;

        private Server.Logger log;

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
            log = new Logger(Server.log, Thread.currentThread().getName());
            try (final PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                log.debug("Connection accepted");
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
                        log.debug("Request:");
                        log.debug(request.toString());
                        final String responseText = applicationProtocol.response(request).toString();
                        log.debug("Response:");
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
                        log.debug("Connection terminated");
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

    public static final class Logger {

        private PrintStream out = System.out;

        private PrintStream err = System.err;

        private final String id;

        public Logger(final PrintStream out, final PrintStream err, final String id) {
            this.out = out;
            this.err = err;
            this.id = id;
        }

        public Logger(final String id) {
            this.id = id;
        }

        public Logger(final Logger logger, final String id) {
            this(logger.out, logger.err, id);
        }

        public void debug(final String text) {
            if (out != null) {
                out.println();
                Arrays.stream(text.split("\n"))
                    .forEach(line -> out.println(id + ": " + line));
            }
        }

        public void info(final String text) {
            debug(text);
        }

        public void error(final String text) {
            if (err != null) {
                err.println();
                err.println(id + ": " + text);
            }
        }
    }

}
