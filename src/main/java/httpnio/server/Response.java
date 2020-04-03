package httpnio.server;

import httpnio.Const;
import httpnio.client.Request;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public class Response {
    private static final String STATUS_LINE_REGEX = "^HTTP/\\d.\\d (\\d+) (.*)$";

    private Request request;

    private String statusCode;

    private String statusMessage;

    private Map<String, String> headers;

    private String body;

    public Response(final Request request, final String messageHeader, final String messageBody) {
        this.request = request;
        body = messageBody;
        final var statusLine = Arrays.stream(messageHeader.split("\n"))
            .filter(e -> e.matches(STATUS_LINE_REGEX))
            .map(e -> Pattern.compile(STATUS_LINE_REGEX).matcher(e).results().map(ee -> ee.group(0)).findFirst().orElse(null))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        statusCode = Pattern.compile(STATUS_LINE_REGEX)
            .matcher(Objects.requireNonNullElse(statusLine, ""))
            .results()
            .map(e -> e.group(1))
            .map(String::trim)
            .findFirst()
            .orElse(null);
        statusMessage = Pattern.compile(STATUS_LINE_REGEX)
            .matcher(Objects.requireNonNullElse(statusLine, ""))
            .results()
            .map(e -> e.group(2))
            .map(String::trim)
            .findFirst()
            .orElse(null);
        headers = Arrays.stream(messageHeader.split("\n"))
            .filter(e -> e.contains(":"))
            .map(e -> e.split(": "))
            .collect(Collectors.toMap(e -> e[0], e -> e[1], (value1, value2) -> {
                System.err.println("Duplicate key found. Discarding value: " + value1);
                return value2;
            }));

    }

    public Response(final Request request, final String response) {
        this(request, messageHeaderAndBody(response)._1(), messageHeaderAndBody(response)._2());
    }

    private static Tuple2<String, String> messageHeaderAndBody(final String response) {
        try {
            final var lines = List.of(response.split("\n"));
            final var lastLineOfHeaderIndex = response.indexOf("\n");
            final var messageHeader = lines.subList(0, lastLineOfHeaderIndex);
            final var messageBody = lines.subList(lastLineOfHeaderIndex + 1, lines.size());
            return Tuple.of(String.join("\n", messageHeader), String.join("\n", messageBody));
        } catch (final Exception e) {
            return Tuple.of("", response);
        }
    }

    public String statusLine() {
        return "HTTP/1.0 " + statusCode + " " + statusMessage;
    }

    public String messageHeader() {
        if (headers == null) {
            return "";
        }
        return statusLine() + Const.CRLF + headers.entrySet()
            .stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder();

        sb.append(String.format("HTTP/1.1 %s %s%s", statusCode, statusMessage, Const.CRLF));

        for (final var header : headers.entrySet()) {
            sb.append(String.format("%s: %s%s", header.getKey(), header.getValue(), Const.CRLF));
        }

        if (body != null) {
            sb.append(String.format("%s: %s%s", Const.Headers.CONTENT_LENGTH, body.length(), Const.CRLF));
            sb.append(Const.CRLF);
            sb.append(body);
        } else {
            sb.append(Const.CRLF);
        }

        return sb.toString();
    }
}
