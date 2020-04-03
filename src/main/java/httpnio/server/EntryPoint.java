package httpnio.server;

import httpnio.Const;
import httpnio.cli.*;
import io.vavr.control.Either;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Command(name = Const.HTTPFS, description = "httpfs is a simple file server.")
public class EntryPoint {

    @Flag(
        name = "verbose",
        alias = {"--verbose", "-v"},
        required = false,
        description = "Prints debugging messages.")
    boolean verbose;

    @Option(
        name = "port",
        alias = {"--port", "-p"},
        argument = @Argument(
            name = "port",
            format = "number",
            regex = "(^\\d+$)",
            description = ""),
        description = "Specifies the port number that the server will listen and serve at (default is 8080).")
    int port;

    @Option(
        name = "directory",
        alias = {"--dir", "-d"},
        argument = @Argument(name = "directory",
            format = "/path/to/directory",
            regex = "(.*)",
            description = "Path to the directory"),
        description = "Specifies the directory that the server will use to read/write requested files (default is the current directory when launching the application).")
    String directory;

    public static void entryPoint(final String[] args) {
        final Parser<httpnio.server.EntryPoint> parser = new Parser<>(httpnio.server.EntryPoint.class);
        final Try<Either<String, httpnio.server.EntryPoint>> result = parser.parse(String.join(" ", args));

        result
            .onSuccess(success -> {
                if (success.isLeft()) {
                    System.out.println(success.getLeft());
                }

                if (success.isRight()) {
                    exec(success.get());
                }
            })
            .onFailure(failure -> {
                System.err.println(failure.getMessage());
                System.err.println(parser.help());
                System.exit(1);
            });
    }

    static void exec(final httpnio.server.EntryPoint ep) {
        try {
            final Server.Configuration configuration = new Server.Configuration(
                TransportLayerProtocol.of("UDP").apply("").get(),
                ep.port,
                ep.verbose);
            new Server(configuration).run(new FileServerProtocol(ep.directory));
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
