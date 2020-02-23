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

    public static void run(final Protocol protocol, final int port) {
        System.out.println("Starting doResponse");
        final var pool = Executors.newFixedThreadPool(10);

        for (; ; ) {
            try (
                final ServerSocket serverSocket = new ServerSocket(port)
            ) {
                pool.execute(new SocketHandler(serverSocket.accept(), protocol.copy()));

            } catch (final IOException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

    }

    private static class SocketHandler implements Runnable {

        Socket socket;

        Protocol protocol;

        public SocketHandler(final Socket socket, final Protocol protocol) {
            this.socket = socket;
            this.protocol = protocol;
        }

        @Override
        public void run() {
            try (
                final PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                System.out.println("Connection accepted");

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

                out.print(protocol.response(request).toString());
                out.flush();

            } catch (final IOException | RequestError e) {
                e.printStackTrace();
            }
        }
    }

}
