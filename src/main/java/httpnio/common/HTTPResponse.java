package httpnio.common;

import httpnio.Const;
import io.vavr.control.Either;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
final public class HTTPResponse {
    private static final String STATUS_LINE_REGEX = "^HTTP/\\d.\\d (\\d+) (.*)\r?$";

    private HTTPRequest request;

    private String statusCode;

    private String statusMessage;

    private Map<String, String> headers;

    private String body;

    public HTTPResponse(final HTTPRequest request, final String messageHeader, final String messageBody) {
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

    public static Either<HTTPResponse, String> of(final HTTPRequest request, final String spec) {
        try {
            final var lines = List.of(spec.split(Const.CRLF));
            final var lastLineOfHeaderIndex = indexOfBlankLine(spec);
            if (lastLineOfHeaderIndex == -1) {
                return Either.right("could not parse blank line in HTTP response");
            }
            final var messageHeader = lines.subList(0, lastLineOfHeaderIndex);
            final var messageBody = lines.subList(lastLineOfHeaderIndex + 1, lines.size());
            final var response = new HTTPResponse(request, String.join("\n", messageHeader), String.join("\n", messageBody));

            final var isValid = response.valid();
            return isValid.isLeft() ? Either.left(response) : Either.right(isValid.get());
        } catch (final Exception e) {
            return Either.right("received IOException: " + e.getMessage());
        }
    }

    private static int indexOfBlankLine(final String text) {
        final var lines = List.of(text.split("\n"));
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("") || lines.get(i).equals("\r")) {
                return i;
            }
        }
        return -1;
    }

    public Either<Boolean, String> valid() {
        if (statusCode == null) {
            return Either.right("statusCode was null");
        }

        if (statusMessage == null) {
            return Either.right("statusMessage was null");
        }

        if (headers == null) {
            return Either.right("headers were null");
        }

        return validBody();
    }

    private Either<Boolean, String> validBody() {
        if (headers != null) {
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
        var contentLengthAdded = false;

        sb.append(String.format("HTTP/1.1 %s %s%s", statusCode, statusMessage, Const.CRLF));

        for (final var header : headers.entrySet()) {
            sb.append(String.format("%s: %s%s", header.getKey(), header.getValue(), Const.CRLF));
            if (header.getKey().equalsIgnoreCase(Const.Headers.CONTENT_LENGTH)) {
                contentLengthAdded = true;
            }
        }

        if (body != null) {
            if (!contentLengthAdded) {
                sb.append(String.format("%s: %s%s", Const.Headers.CONTENT_LENGTH, body.length(), Const.CRLF));
            }
            sb.append(Const.CRLF);
            sb.append(body);
        } else {
            sb.append(Const.CRLF);
        }

        return sb.toString();
    }
}
