package httpsocketclient.http;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
@Accessors(fluent = true)
@ToString
public class Response {
    private static final String STATUS_LINE_REGEX = "^HTTP/\\d.\\d (\\d+) .*$";

    private final Request request;

    private final String messageHeader;

    private final String messageBody;

    private final String statusLine;

    private final String statusCode;

    private final Map<String, String> headers;

    public Response(final Request request, final String messageHeader, final String messageBody) {
        this.request = request;
        this.messageHeader = messageHeader;
        this.messageBody = messageBody;
        statusLine = Arrays.stream(messageHeader.split("\n"))
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
        headers = Arrays.stream(messageHeader.split("\n"))
            .filter(e -> e.contains(":"))
            .map(e -> e.split(": "))
            .collect(Collectors.toMap(e -> e[0], e -> e[1], (value1, value2) -> {
                System.err.println("Duplicate key found. Discarding value: " + value1);
                return value2;
            }));

    }

}
