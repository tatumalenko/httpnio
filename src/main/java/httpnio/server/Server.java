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
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static httpnio.common.Packet.State.*;

@SuppressWarnings("squid:S2189")
@Slf4j
public class Server {

    private final ExecutorService executorService;

    private volatile boolean isRunning = false;

    private final Server.Configuration configuration;

    public Server(final Server.Configuration configuration) {
        this.configuration = configuration;
        executorService = Executors.newFixedThreadPool(configuration.threadPoolSize());
    }

    public synchronized void run() {
        if (isRunning) {
            return;
        }

        log.info("starting server");
        log.info("using port {}", configuration.port());
        try {
            isRunning = true;
            new ServerThread().start();
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

        private DatagramChannel channel;

        private ServerSocketChannel tcpChannel;

        private ByteBuffer buffer;

        ServerThread() throws IOException {
            super("listener-thread");

            log.debug("starting server with listener thread");

            if (configuration.transportProtocolType == TransportProtocol.Type.TCP) {
                tcpChannel = ServerSocketChannel.open();
                tcpChannel.bind(new InetSocketAddress(configuration.port()));
            } else {
                final var selector = Selector.open();
                channel = DatagramChannel.open();
                channel.configureBlocking(false);
                channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                channel.bind(new InetSocketAddress(configuration.port()));
                buffer = transportProtocol().emptyBuffer();
            }
        }

        @Override
        public void run() {
            while (isRunning) {
                try {
                    if (configuration.transportProtocolType == TransportProtocol.Type.TCP) { // TCP
                        executorService.execute(handler(tcpChannel.socket().accept(), null, null, null));
                    } else { // UDP
                        buffer.clear();
                        final SocketAddress router = channel.receive(buffer);
                        buffer.flip();
                        if (router != null) {
                            final Packet packet = Packet.of(buffer);
                            final var client = packet.peerAddress();
                            log.debug("incoming packet={}", packet);
                            log.debug("client={}", client);

                            final var existingQueue = clientPacketQueueTable.putIfAbsent(client, new ArrayBlockingQueue<>(100));
                            final BlockingQueue<Packet> clientPacketQueue = Objects.requireNonNull(
                                clientPacketQueueTable.get(client),
                                "client incoming packet queue should have been present in client table");

                            clientPacketQueue.put(packet);
                            if (packet.is(SYN) && existingQueue == null) {
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

    private static class UDPHandler implements Runnable, UDPSRProtocol.Agent {

        DatagramChannel channel;

        Selector selector;

        InetSocketAddress client;

        BlockingQueue<Packet> queue;

        TransportProtocol transportProtocol;

        private final ApplicationProtocol.Response applicationProtocol;

        private final Configuration configuration;

        private final InetSocketAddress router;

        @SneakyThrows
        public UDPHandler(
            final DatagramChannel channel,
            final SocketAddress client,
            final BlockingQueue<Packet> queue,
            final Configuration configuration,
            final TransportProtocol transportProtocol,
            final ApplicationProtocol.Response applicationProtocol) {
            this.channel = channel;
            this.client = (InetSocketAddress) client;
            this.queue = queue;
            this.transportProtocol = transportProtocol;
            this.applicationProtocol = applicationProtocol;
            this.configuration = configuration;
            router = configuration.router();
        }

        @SneakyThrows
        @Override
        public void run() {
            try {
                configure();
                if (handshake()) {
                    final HTTPRequest request = transportProtocol.receive(this);
                    if (request != null) {
                        log.info("request received, preparing response");
                        final var response = applicationProtocol.response(request);
                        final var buffers = PacketUtil.split(response.toString().getBytes());
                        final var packets = new Packet[buffers.length];
                        for (int i = 0; i < buffers.length; i++) {
                            packets[i] = Packet.builder()
                                .state(BFRD)
                                .sequenceNumber(i)
                                .peerAddress(client)
                                .payload(buffers[i].array())
                                .build();
                        }
                        if (transportProtocol.send(this, packets)) {
                            log.info("response sent!");
                        } else {
                            log.error("unable to confirm response delivery after {} attempts", transportProtocol.maxConsecutiveRetries());
                        }
                        log.info("response=\n{}", response.toString());
                    }
                }
            } catch (final Exception e) {
                log.error("{}: {}", e.getClass().getSimpleName(), e.getMessage());
            } finally {
                log.debug("disconnecting");
                selector.close();
                log.debug("exiting thread");
            }
        }

        private void configure() throws IOException {
            selector = Selector.open();
            channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }

        private boolean handshake() throws InterruptedException, IOException {
            final Packet synPacket = Objects.requireNonNull(
                queue.poll(),
                "synPacket should have been in client queue but none were found");
            log.debug("received syn {}", synPacket);

            final Packet synAckPacket = synPacket.toBuilder()
                .state(SYNACK)
                .sequenceNumber(1)
                .peerAddress(client)
                .build();

            write(synAckPacket);
            log.debug("sent synack {}", synAckPacket);

            final Packet ackPacket = queue.poll(transportProtocol.packetTimeoutMs(), TimeUnit.MILLISECONDS);

            if (ackPacket == null) {
                log.error("did not receive ack after timeout, assuming was sent");
            } else if (!ackPacket.is(ACK)) {
                var packet = ackPacket;
                if (packet.is(SYN)) {
                    while (packet != null && packet.is(SYN)) {
                        log.warn("received again syn {}", packet);
                        write(packet.toBuilder().state(SYNACK).build());
                        log.debug("sent synack {}", synAckPacket);
                        packet = queue.poll(transportProtocol.packetTimeoutMs(), TimeUnit.MILLISECONDS);
                    }
                    if (packet == null) {
                        log.error("did not receive ack after timeout, assuming was sent");
                    } else {
                        log.error("received a packet but was not ack, will offer other packet back to queue");
                        final var offeredToQueue = queue.offer(ackPacket, transportProtocol.packetTimeoutMs(), TimeUnit.MILLISECONDS);
                        log.info("{} was successfully to queue", offeredToQueue);
                    }
                } else {
                    log.error("received a packet but was not ack, will offer other packet back to queue");
                    final var offeredToQueue = queue.offer(ackPacket, transportProtocol.packetTimeoutMs(), TimeUnit.MILLISECONDS);
                    log.info("{} was successfully to queue", offeredToQueue);
                }
            } else {
                log.debug("received ackPacket {}", ackPacket);
                log.debug("connection established");
            }

            return true;
        }

        private Packet read(final int timeout) throws InterruptedException {
            log.info("blocking up to {}ms for response", timeout);
            final Packet packet = queue.poll(timeout, TimeUnit.MILLISECONDS);
            log.info("polled {}", packet);
            return packet;
        }

        @Override
        public Packet read() throws InterruptedException {
            return read(transportProtocol.packetTimeoutMs());
        }

        @Override
        public void write(final Packet packet) throws IOException {
            log.info("sent {}", packet);
            channel.send(packet.buffer(), router);
        }

        @Override
        public <T> T make(final List<Packet> packets) {
            final var combinedPayload = packets.stream()
                .filter(Objects::nonNull)
                .map(Packet::payload)
                .collect(Collectors.joining(""));
            try {
                final var request = HTTPRequest.of(combinedPayload);
                if (request.isLeft()) {
                    log.info("request successfully created");
                    return (T) request.getLeft();
                } else {
                    log.error("request invalid: {}", request.get());
                    log.info("\nrequest: \n{}", combinedPayload);
                    return null;
                }
            } catch (final Exception e) {
                log.error("{}: {}", e.getClass().getSimpleName(), e.getMessage());
                log.debug("request: \n{}", combinedPayload);
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
                int contentLength = 0;

                while (keepAlive) {
                    HTTPRequest request = null;
                    final StringBuilder requestBuilder = new StringBuilder();
                    String line;

                    while ((line = in.readLine()) != null) {
                        requestBuilder.append(line);
                        requestBuilder.append(Const.CRLF);

                        if (line.toLowerCase().contains(Const.Headers.CONTENT_LENGTH.toLowerCase())) {
                            final var keyValue = line.split(":");
                            contentLength = Integer.parseInt(keyValue[1].trim());
                        }

                        log.debug("line: {}", line);
                        if (line.equalsIgnoreCase("")) {
                            for (var i = 0; i < contentLength; i++) {
                                requestBuilder.append((char) in.read());
                            }

                            break;
                        }
                    }

                    log.debug("builder: {}", requestBuilder.toString());
                    final var requestAttempt = HTTPRequest.of(requestBuilder.toString());

                    if (requestAttempt.isLeft()) {
                        request = requestAttempt.getLeft();
                    } else {
                        throw new HTTPRequest.RequestError(requestAttempt.get());
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

        public final InetSocketAddress router() throws UnknownHostException {
            return new InetSocketAddress(InetAddress.getByName(Const.DEFAULT_ROUTER_HOST), Const.DEFAULT_ROUTER_PORT);
        }

        public final int threadPoolSize() {
            return Const.DEFAULT_THREAD_POOL_SIZE;
        }
    }
}
