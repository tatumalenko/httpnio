package httpsocketclient.client;

import httpsocketclient.Const;
import httpsocketclient.server.Response;
import io.vavr.control.Try;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.concurrent.*;

public class Client implements Gettable, Postable {

    public Response request(final Request request) throws RequestError {
        final Callable<Response> task = () -> dispatch(request);
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<Response> future = executorService.submit(task);
        executorService.shutdownNow();

        try {
            return future.get(Const.TIMEOUT_LIMIT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException ie) {
            throw new RequestError("InterruptedException: The task was interrupted. Please ensure the request is valid.\n" + (ie.getMessage() != null ? ie.getMessage() : ""));
        } catch (final ExecutionException ee) {
            throw new RequestError("ExecutionException: An error occurred during the execution of the task. Please ensure the request is valid.\n" + (ee.getMessage() != null ? ee.getMessage() : ""));
        } catch (final TimeoutException te) {
            throw new RequestError("TimeoutException: The request was not received after " + Const.TIMEOUT_LIMIT_SECONDS + " seconds. Please ensure the request is valid or retrieve a smaller payload.\n" + (te.getMessage() != null ? te.getMessage() : ""));
        } catch (final Exception e) {
            throw new RequestError("Exception: An unknown error occurred. Please ensure the request is valid.\n" + (e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    @Override
    public Response get(final Request request) throws RequestError {
        return doRequest(request);
    }

    @Override
    public Response post(final Request request) throws RequestError {
        return get(request);
    }

    private Response dispatch(final Request request) throws RequestError {
        switch (request.method()) {
            case GET:
                return get(request);
            case POST:
                return post(request);
            default:
                throw new IllegalArgumentException("Invalid http method specified: " + request.method());
        }
    }

    private Response doRequest(final Request request) throws RequestError {
        try (final Socket socket = new Socket(request.host(), request.url().port());
             final PrintWriter writer = new PrintWriter(socket.getOutputStream());
             final Scanner reader = new Scanner(new InputStreamReader(socket.getInputStream()))) {
            var isMessageHeader = true;
            final StringBuilder messageHeader = new StringBuilder();
            final StringBuilder messageBody = new StringBuilder();

            writer.print(request.toString());
            writer.flush();

            while (reader.hasNextLine()) {
                final String line = reader.nextLine();

                if (line.equals("")) {
                    isMessageHeader = false;
                }

                if (isMessageHeader) {
                    messageHeader.append(String.format("%n%s", line));
                } else {
                    messageBody.append(String.format("%n%s", line));
                }
            }

            Response response = new Response(request, messageHeader.toString(), messageBody.toString());

            if (response.statusCode() != null && response.statusCode().matches("3\\d+")) {
                final String location = response.headers().get("Location");
                final URL redirectURL = Try.of(() -> new URL(location))
                    .getOrElse(() ->
                        Try.of(() -> new URL(request.url().protocol(), request.url().host(), location, request.url().query()))
                            .getOrElse(() -> null));

                response = doRequest(request.toBuilder().url(redirectURL).build());
            }

            return response;
        } catch (final UnknownHostException e) {
            throw new RequestError("Server not found: " + e.getMessage());
        } catch (final IOException e) {
            throw new RequestError("I/O error: " + e.getMessage());
        } catch (final Exception e) {
            throw new RequestError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
