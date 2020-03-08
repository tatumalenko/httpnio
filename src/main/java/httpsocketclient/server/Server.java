package httpsocketclient.server;

import httpsocketclient.Const;
import httpsocketclient.client.Request;
import httpsocketclient.client.RequestError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;

@SuppressWarnings("squid:S2189")
public class Server {

    private final ServerConfiguration serverConfiguration;

    private final ServerLogger log;

    public Server(final ServerConfiguration serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
        log = new ServerLogger(serverConfiguration.verbose(), "main-server-thread");
    }

    public void run(final Protocol protocol) {
        log.debug("Starting server");
        final var poolSize = 10;
        final var pool = Executors.newFixedThreadPool(poolSize);
        final var port =
            serverConfiguration.port() == 0 || serverConfiguration.port() == -1
                ? Const.DEFAULT_SERVER_PORT
                : serverConfiguration.port();
        log.debug("Using port " + port);

        for (; ; ) {
            try (
                final ServerSocket serverSocket = new ServerSocket(port)
            ) {
                pool.execute(new SocketHandler(serverSocket.accept(), protocol.copy(), serverConfiguration.verbose()));

            } catch (final IOException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private static class SocketHandler implements Runnable {

        final Socket socket;

        final Protocol protocol;

        final boolean verbose;

        ServerLogger log;

        public SocketHandler(final Socket socket, final Protocol protocol, final boolean verbose) {
            this.socket = socket;
            this.protocol = protocol;
            this.verbose = verbose;
        }

        @Override
        public void run() {
            log = new ServerLogger(verbose, Thread.currentThread().getName());
            try (
                final PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
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
            }
        }
    }

}
