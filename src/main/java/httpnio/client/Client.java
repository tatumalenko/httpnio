package httpnio.client;

import httpnio.Const;
import httpnio.common.Packet;
import httpnio.server.Response;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.*;

import static java.nio.channels.SelectionKey.OP_READ;

@Slf4j
public class Client {

    public Response request(final Request request) throws RequestError {
        final Callable<Response> task = () -> dispatch(request);
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Response> future = executorService.submit(task);
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

    private Response dispatch(final Request request) throws RequestError {
        try {
            switch (request.url().protocol()) {
                case "http":
                case "https":
                    return httpRequest(request);
                case "udp":
                    return udpRequest(request);
                default:
                    throw new RequestError("URL protocol invalid: expected 'https', 'http', or 'udp', but got '" + request.url()
                        .protocol() + "'\n");
            }
        } catch (final UnknownHostException e) {
            throw new RequestError("Server not found: " + e.getMessage() + "\n\nRequest: \n" + request.toString() + "\n");
        } catch (final IOException e) {
            throw new RequestError(e.getClass().getSimpleName() + ": " + e.getMessage() + "\n\nRequest: \n" + request.toString() + "\n");
        }
    }

    private Response httpRequest(final Request request) throws IOException {
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

            Response response = new Response(request, message.toString());

            if (response.statusCode() != null && response.statusCode().matches("3\\d+")) {
                final String location = response.headers().get("Location");
                final URL redirectURL = Try.of(() -> URL.fromSpec(location))
                    .getOrElse(() ->
                        Try.of(() -> request.url().toBuilder().path(location).build())
                            .getOrElse(() -> null));

                response = httpRequest(request.toBuilder().url(redirectURL).build());
            }

            return response;
        }
    }

    private Response udpRequest(final Request request) throws IOException {
        log.info("REQUEST: {}:", request.toString());
        try (final DatagramChannel channel = DatagramChannel.open()) {
            final String msg = "Hello World";
            final Packet p = new Packet.Builder()
                .setType(0)
                .setSequenceNumber(1L)
                .setPortNumber(request.url().port())
                .setPeerAddress(request.url().socketAddress())
                .setPayload(msg.getBytes())
                .create();
            log.info(p.toString());
            channel.send(p.toBuffer(), request.routerAddress());

            log.info("Sending \"{}\" to router at {}", msg, request.url());

            // Try to receive a packet within timeout.
            channel.configureBlocking(false);
            final Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            log.info("Waiting for the response");
            selector.select(5000);

            final Set<SelectionKey> keys = selector.selectedKeys();
            if (keys.isEmpty()) {
                log.error("No response after timeout");
            }

            // We just want a single response.
            final ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            final SocketAddress router = channel.receive(buf);
            buf.flip();
            final Packet resp = Packet.fromBuffer(buf);
            log.info("Packet: {}", resp);
            log.info("Router: {}", router);
            final String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            log.info("Payload: {}", payload);

            keys.clear();

            final Response response = new Response(request, payload.toString());

            return response;
        }
    }
}
