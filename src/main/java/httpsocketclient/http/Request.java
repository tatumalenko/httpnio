package httpsocketclient.http;

import httpsocketclient.Const;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Builder(toBuilder = true)
public class Request {
    private final HttpMethod method;

    private final URL url;

    private final Map<String, String> headers;

    private final String body;

    private final File in;

    private final File out;

    public HttpMethod method() {
        return method;
    }

    public URL url() {
        return url;
    }

    public String host() {
        return url.host();
    }

    public String path() {
        return url.path() + (url.query() == null ? "" : "?" + url.query());
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
        private HttpMethod method = null;
        private String url = null;
        private List<String> headers = null;
        private String body = null;
        private String in = null;
        private String out = null;

        public Builder method(final HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder url(final String url) {
            this.url = url;
            return this;
        }

        public Builder headers(final List<String> headers) {
            this.headers = headers;
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

        public Request build() throws RequestError, IOException {
            if (method == HttpMethod.POST && body != null && in != null) {
                throw new RequestError("Fields body and in cannot be both specified.");
            }
            if (method == HttpMethod.POST && body == null && in == null) {
                throw new RequestError("At least field body or in must be specified.");
            }

            if (in != null) {
                body = new String(Files.readAllBytes(Paths.get(in)));
            }

            final var mappedHeaders = new HashMap<String, String>();

            if (headers != null) {
                for (final var header : headers) {
                    final var matches = header.split(":");

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

            return new Request(method, new URL(url), mappedHeaders, body, in != null ? new File(in) : null, out != null ? new File(out) : null);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(String.format("%s %s HTTP/1.0%s", method().name(), path(), Const.NEWLINE));
        sb.append(String.format("Host: %s%s", host(), Const.NEWLINE));
        if (headers() != null) {
            for (final var entry : headers().entrySet()) {
                sb.append(String.format("%s: %s%s", entry.getKey(), entry.getValue(), Const.NEWLINE));
            }
        }

        addHeaderIfAbsent(sb, "Content-Type", "application/json");

        if (body() != null) {
            addHeaderIfAbsent(sb, "Content-Length", body().length());
        }

        if (body() != null) {
            sb.append(Const.NEWLINE);
            sb.append(String.format("%s", body()));
        }

        sb.append(Const.NEWLINE);

        return sb.toString();
    }

    private void addHeaderIfAbsent(final StringBuilder sb, final String headerKey, final String headerValue) {
        if (headers() != null && !headers.containsKey(headerKey)) {
            sb.append(String.format("%s: %s%s", headerKey, headerValue, Const.NEWLINE));
        }
    }

    private void addHeaderIfAbsent(final StringBuilder sb, final String headerKey, final int headerValue) {
        addHeaderIfAbsent(sb, headerKey, Integer.toString(headerValue));
    }
}
