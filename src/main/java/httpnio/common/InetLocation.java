package httpnio.common;

import httpnio.Const;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Optional;

@Slf4j
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class InetLocation {

    private final java.net.URL url;

    private final InetSocketAddress socketAddress;

    private final String protocol;

    private final String host;

    private final String path;

    private final String query;

    private final int port;

    public static InetLocation fromSpec(final String spec) throws MalformedURLException, UnknownHostException {
        final InetLocationBuilder[] url = new InetLocationBuilder[1];
        tryUrlOrSocketAddress(spec).peekLeft(javaNetUrl -> {
            url[0] = InetLocation.builder()
                .protocol(javaNetUrl.getProtocol())
                .port(javaNetUrl.getPort() != -1 ? javaNetUrl.getPort() : Const.DEFAULT_PORT)
                .host(javaNetUrl.getHost())
                .path(javaNetUrl.getPath())
                .query(javaNetUrl.getQuery())
                .url(javaNetUrl);
        }).peek(socketAddress -> {
            url[0] = InetLocation.builder()
                .protocol("udp")
                .port(socketAddress.getPort() != -1 ? socketAddress.getPort() : Const.DEFAULT_PORT)
                .host(socketAddress.getHostName())
                .path("")
                .query(null)
                .url(null);
        });
        final var tempUrl = url[0].build();
        return tempUrl.toBuilder().socketAddress(new InetSocketAddress(InetAddress.getByName(tempUrl.host()), tempUrl.port()))
            .build(); // Shitty hack, boo Java
    }

    public Optional<InetSocketAddress> address() {
        return Optional.of(socketAddress);
    }

    @Override
    public String toString() {
        return "http://" + host + (port != -1 ? ":" + port : "") + path;
    }

    private static Either<java.net.URL, InetSocketAddress> tryUrlOrSocketAddress(final String spec) throws MalformedURLException {
        final Optional<Tuple2<String, String>> addressAndPortMaybe =
            Optional.ofNullable(spec)
                .map(s -> Tuple.of(
                    s.replaceAll(":\\d+", ""),
                    s.replaceAll("^((\\d+\\.)+\\d[.:]|(localhost:)|(\\S+:))", "")));
        final Optional<Either<java.net.URL, InetSocketAddress>> urlOrSocketAddressMaybe = addressAndPortMaybe
            .map(addressAndPort -> Try.of(() -> Either.<java.net.URL, InetSocketAddress>left(new java.net.URL(spec)))
                .getOrElseTry(() -> Try.of(() -> Either.<java.net.URL, InetSocketAddress>right(new InetSocketAddress(
                    InetAddress.getByName(addressAndPort._1()).getHostAddress(),
                    Integer.parseInt(addressAndPort._2())
                )))
                    .getOrElseThrow(() -> new MalformedURLException("Provided URL is neither a standard http or socket host address"))));
        return urlOrSocketAddressMaybe.orElseThrow(() -> new MalformedURLException("Provided URL was null"));
    }
}
