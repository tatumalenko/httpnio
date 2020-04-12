package httpnio.common;

import httpnio.Const;
import io.vavr.control.Either;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Builder(toBuilder = true)
public class HTTPRequest {
    private final HTTPMethod method;

    private final InetLocation inetLocation;

    private final InetLocation routerAddress;

    private final Map<String, String> headers;

    private final String body;

    private final File in;

    private final File out;

    private final String path;

    public HTTPMethod method() {
        return method;
    }

    public InetLocation url() {
        return inetLocation;
    }

    public InetSocketAddress socketAddress() {
        return inetLocation.socketAddress();
    }

    public InetSocketAddress routerAddress() {
        return routerAddress.socketAddress();
    }

    public String host() {
        return inetLocation.host();
    }

    public String path() {
        if (path == null) {
            return inetLocation.path() + (inetLocation.query() == null ? "" : "?" + inetLocation.query());
        } else {
            final var tryPath = inetLocation.path() + (inetLocation.query() == null ? "" : "?" + inetLocation.query());
            if (!tryPath.equals("")) {
                return inetLocation.path() + (inetLocation.query() == null ? "" : "?" + inetLocation.query());
            } else {
                return path;
            }
        }
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String body() {
        return body;
    }

    public File in() {
        return in;
    }

    public File out() {
        return out;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private HTTPMethod method = null;
        private String url = null;
        private String routerAddress = null;
        private List<String> headers = null;
        private String body = null;
        private String in = null;
        private String out = null;
        private String spec = null;
        private String path = null;

        public Builder path(final String path) {
            this.path = path;
            return this;
        }

        public Builder spec(final String spec) {
            this.spec = spec;
            return this;
        }

        public Builder method(final HTTPMethod method) {
            this.method = method;
            return this;
        }

        public Builder url(final String url) {
            this.url = url;
            return this;
        }

        public Builder routerAddress(final String address) {
            routerAddress = address;
            return this;
        }

        public Builder headers(final List<String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder headers(final Map<String, String> headers) {
            this.headers = headers.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.toList());
            return this;
        }

        public Builder body(final String body) {
            this.body = body;
            return this;
        }

        public Builder in(final String in) {
            this.in = in;
            return this;
        }

        public Builder out(final String out) {
            this.out = out;
            return this;
        }

        public HTTPRequest build() throws RequestError, IOException {
            if (method == HTTPMethod.POST && body != null && in != null) {
                throw new RequestError("Fields body and in cannot be both specified.");
            }

            if (in != null) {
                body = new String(Files.readAllBytes(Paths.get(in)));
            }

            final var mappedHeaders = new HashMap<String, String>();

            if (headers != null) {
                for (final var header : headers) {
                    final var matches = header.split(":", 2);

                    if (matches.length != 2) {
                        throw new RequestError("Error occurred while parsing the header: " + header + ". Should of been delimited by a `:` such as `key:value`, but was not.");
                    }

                    final var key = matches[0].trim();
                    final var value = matches[1].trim();

                    if (mappedHeaders.containsKey(key)) {
                        throw new RequestError("Error occurred while parsing the header: " + header + ". A duplicate key was found: " + key + ".");
                    }

                    mappedHeaders.put(key, value);
                }
            }

            return new HTTPRequest(
                method,
                InetLocation.fromSpec(url),
                InetLocation.fromSpec(Const.DEFAULT_ROUTER_ADDRESS),
                mappedHeaders,
                body,
                in != null ? new File(in) : null,
                out != null ? new File(out) : null,
                path);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(String.format("%s %s HTTP/1.0%s", method().name(), path().equals("") ? "/" : path(), Const.CRLF));
        sb.append(String.format("Host: %s%s", host(), Const.CRLF));
        if (headers() != null) {
            for (final var entry : headers().entrySet()) {
                sb.append(String.format("%s: %s%s", entry.getKey(), entry.getValue(), Const.CRLF));
            }
        }

        addHeaderIfAbsent(sb, Const.Headers.CONTENT_TYPE, Const.Headers.APPLICATION_JSON);

        if (body() != null) {
            addHeaderIfAbsent(sb, Const.Headers.CONTENT_LENGTH, body().length());
        }

        if (body() != null) {
            sb.append(Const.CRLF);
            sb.append(String.format("%s", body()));
        }

        sb.append(Const.CRLF);

        return sb.toString();
    }

    public static Either<HTTPRequest, String> of(final String spec) throws RequestError {
        final Builder requestBuilder = HTTPRequest.builder();
        requestBuilder.spec(spec);

        int lineBreakCount = 0;
        int lineCount = 1;

        String host;
        String path = "";
        final List<String> headers = new ArrayList<>();

        for (final var line : spec.split(Const.CRLF)) {
            if (lineCount == 1) {
                final String[] lexemes = line.split("\\s+");
                if (lexemes.length != 3) {
                    return Either.right("First line of HTTP request did not contain 3 space delimited lexemes: " + line);
                }
                requestBuilder.method(HTTPMethod.of(lexemes[0]));
                path = lexemes[1];
            } else if (lineCount == 2) {
                if (!line.contains("Host: ")) {
                    return Either.right("Second line of HTTP request did not contain 'Host: ..' information");
                }
                host = Pattern.compile("Host: (\\S+)").matcher(line).results().map(ee -> ee.group(1)).findFirst().orElse(null);
                requestBuilder.url("http://" + host + path);
            } else if (!line.trim().equalsIgnoreCase("") && lineBreakCount == 0) {
                headers.add(line.trim());
            }

            if (line.equalsIgnoreCase("")) {
                lineBreakCount += 1;
            } else if (lineBreakCount == 1) {
                requestBuilder.body(line);
            }

            lineCount += 1;
        }

        if (!headers.isEmpty()) {
            requestBuilder.headers(headers);
        }

        try {
            final var request = requestBuilder.build();
            final var isValid = request.valid();
            return isValid.isLeft() ? Either.left(request) : Either.right(isValid.get());
        } catch (final IOException e) {
            return Either.right("received IOException: " + e.getMessage());
        }
    }

    public Either<Boolean, String> valid() {
        if (url().path() == null) {
            return Either.right("url.path was null");
        }

        if (method == null) {
            return Either.right("method was null");
        }

        if (headers == null) {
            return Either.right("headers were null");
        }

        return validBody();
    }

    private Either<Boolean, String> validBody() {
        if (method() == HTTPMethod.POST && headers != null) {
            final var contentLength = headers.getOrDefault("Content-Length", "0");
            final var bodyLength = String.valueOf(body != null ? body.length() : 0);
            if (!contentLength.equals(bodyLength)) {
                return Either.right(String.format(
                    "Content-Length header value (%s) did not match body's length parsed (%s)",
                    contentLength,
                    bodyLength));
            }
        }
        return Either.left(true);
    }

    private void addHeaderIfAbsent(final StringBuilder sb, final String headerKey, final String headerValue) {
        if (headers() != null && !headers.containsKey(headerKey)) {
            sb.append(String.format("%s: %s%s", headerKey, headerValue, Const.CRLF));
        }
    }

    private void addHeaderIfAbsent(final StringBuilder sb, final String headerKey, final int headerValue) {
        addHeaderIfAbsent(sb, headerKey, Integer.toString(headerValue));
    }

    public static class RequestError extends Exception {
        public RequestError(final String message) {
            super(message);
        }
    }
}
