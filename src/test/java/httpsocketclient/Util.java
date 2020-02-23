package httpsocketclient;

import httpsocketclient.client.EntryPointTest;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface Util {
    static String absolutePathFromRelativePath(final String relativePath) {
        final var resource = EntryPointTest.class.getClassLoader().getResource(relativePath);
        final var file = resource != null ? new File(resource.getFile()) : null;
        return file != null ? file.getAbsolutePath() : null;
    }

    static String messageBodyWithoutNonIdempotentHeaders(final String messageBody) {
        final var nonIdempotentHeaders = List.of("X-Amzn-Trace-Id");
        final Function<String, Boolean> containsNonIdempotentHeader = line -> nonIdempotentHeaders.stream().anyMatch(e -> line.contains(e));
        return Arrays.stream(messageBody.split("\n")).filter(e -> !containsNonIdempotentHeader.apply(e)).collect(Collectors.joining("\n"));
    }
}
