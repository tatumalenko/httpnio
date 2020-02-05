package httpsocketclient.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@AllArgsConstructor
@Getter
@Accessors(fluent = true)
@ToString
@Builder
public class Response {
    private final Request request;

    private final String acknowledgement;

    private final String output;
}
