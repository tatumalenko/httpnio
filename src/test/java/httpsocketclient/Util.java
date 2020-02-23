package httpsocketclient;

import httpsocketclient.client.EntryPointTest;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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

    static void doIfPresent(final String consumable, final Consumer<String> consumer) {
        if (consumable != null && !consumable.isEmpty()) {
            consumer.accept(consumable);
        }
    }

    static void doIfAbsent(final String maybeAbsent, final Runnable runner) {
        if (maybeAbsent == null || maybeAbsent.isEmpty()) {
            runner.run();
        }
    }

    static <T> void doIfTrue(final T value, final Predicate<T> predicate, final Consumer<T> consumer) {
        if (predicate.test(value)) {
            consumer.accept(value);
        }
    }
}
