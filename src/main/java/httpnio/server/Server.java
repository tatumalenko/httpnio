package httpnio.server;

import httpnio.Const;
import httpnio.client.Request;
import httpnio.client.RequestError;
import httpnio.common.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.UTF_8;

@SuppressWarnings("squid:S2189")
public class Server {

    private final ReentrantLock lock = new ReentrantLock();

    private final Configuration configuration;

    private final Logger log;

    public Server(final Configuration configuration) {
        this.configuration = configuration;
        log = new Logger(configuration.verbose(), "main-server-thread");
    }

    public void run(final ApplicationLayerProtocol protocol) {
        log.debug("Starting server");
        final var poolSize = 2;
        final var pool = Executors.newFixedThreadPool(poolSize);
        final var port =
            configuration.port() == 0 || configuration.port() == -1
                ? Const.DEFAULT_SERVER_PORT
                : configuration.port();
        log.debug("Using port " + port);

        for (; ; ) {
            pool.execute(new UDPHandler(lock, configuration, protocol));

            try {
                pool.invokeAny(List.of(
                    () -> new UDPHandler(lock, configuration, protocol),
                    () -> new UDPHandler(lock, configuration, protocol)
                ));
            } catch (final InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private static class UDPHandler implements Runnable {

        private final ReentrantLock lock;

        private final ApplicationLayerProtocol protocol;

        private final Configuration configuration;

        private Logger log;

        public UDPHandler(final ReentrantLock lock, final Configuration configuration, final ApplicationLayerProtocol protocol) {
            this.lock = lock;
            this.protocol = protocol;
            this.configuration = configuration;
        }

        @Override
        public void run() {
            lock.lock();
            log = new Server.Logger(configuration.verbose(), Thread.currentThread().getName());
            try (final DatagramChannel channel = DatagramChannel.open()) {

                channel.configureBlocking(true);
                channel.bind(new InetSocketAddress(configuration.port()));
                log.debug("EchoServer is listening at " + channel.getLocalAddress());
                final ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

                buf.clear();
                final SocketAddress router = channel.receive(buf);

                lock.unlock();

                // Parse a packet from the received raw data.
                buf.flip();
                final Packet packet = Packet.fromBuffer(buf);
                buf.flip();

                final String payload = new String(packet.getPayload(), UTF_8);
                log.debug("Packet: " + packet);
                log.debug("Payload: " + payload);
                log.debug("Router: " + router);

                // Send the response to the router not the client.
                // The peer address of the packet is the address of the client already.
                // We can use toBuilder to copy properties of the current packet.
                // This demonstrate how to create a new packet from an existing packet.
                final Packet resp = packet.toBuilder()
                    .setPayload(payload.getBytes())
                    .create();
                channel.send(resp.toBuffer(), router);
            } catch (final IOException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    @AllArgsConstructor
    @Getter
    @Accessors(fluent = true)
    public static final class Configuration {
        private final TransportLayerProtocol protocol;

        private final int port;

        private final boolean verbose;
    }

    @AllArgsConstructor
    public static final class Logger {

        private final boolean standardOutput;

        private final String id;

        public void debug(final String text) {
            if (standardOutput) {
                System.out.println();
                Arrays.stream(text.split("\n"))
                    .forEach(line -> System.out.println(id + ": " + line));
            }
        }

        public void error(final String text) {
            if (standardOutput) {
                System.out.println();
                System.err.println(id + ": " + text);
            }
        }
    }

    private static class TCPHandler implements Runnable {

        private final ReentrantLock lock;

        private final ApplicationLayerProtocol protocol;

        private final Server.Configuration configuration;

        private Server.Logger log;

        public TCPHandler(final ReentrantLock lock, final ApplicationLayerProtocol protocol, final Server.Configuration configuration) {
            this.lock = lock;
            this.protocol = protocol;
            this.configuration = configuration;
        }

        @Override
        public void run() {
            lock.lock();
            log = new Server.Logger(configuration.verbose(), Thread.currentThread().getName());
            try (
                final Socket socket = new ServerSocket(configuration.port()).accept();
                final PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                lock.unlock();
                log.debug("Connection accepted");
                boolean keepAlive = true;

                while (keepAlive) {
                    Request request = null;
                    final StringBuilder requestBuilder = new StringBuilder();
                    String line;

                    while ((line = in.readLine()) != null) {
                        requestBuilder.append(line);
                        requestBuilder.append(Const.CRLF);

                        if (line.equalsIgnoreCase("")) {
                            request = Request.of(requestBuilder.toString());

                            if (request.headers().containsKey(Const.Headers.CONTENT_LENGTH)) {
                                final StringBuilder body = new StringBuilder();
                                final int contentLength = Integer.parseInt(request.headers().get(Const.Headers.CONTENT_LENGTH));
                                for (var i = 0; i < contentLength; i++) {
                                    body.append((char) in.read());
                                }
                                request = Request.builder()
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
                        final String responseText = protocol.response(request).toString();
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
            } catch (final IOException | RequestError e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }
}
