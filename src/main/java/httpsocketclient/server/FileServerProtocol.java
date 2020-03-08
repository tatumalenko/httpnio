package httpsocketclient.server;

import httpsocketclient.client.Request;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FileServerProtocol implements Protocol {

    final Path path;

    String pathAsString;

    public FileServerProtocol() throws IOException {
        this(Paths.get("").toAbsolutePath().toString());
    }

    public FileServerProtocol(final String directory) throws IOException {
        path = Paths.get(directory);

        if (!path.toFile().exists() || !path.toFile().isDirectory()) {
            throw new IllegalStateException("The directory specified does not exist: " + directory);
        } else {
            pathAsString = path.toFile().getCanonicalPath();
        }
    }

    @Override
    public Response response(final Request request) throws IOException {
        return dispatchResponse(request);
    }

    @Override
    public Protocol copy() throws IOException {
        return new FileServerProtocol(pathAsString);
    }

    private Response dispatchResponse(final Request request) {
        switch (request.method()) {
            case GET:
                return get(request);
            case POST:
                return post(request);
            default:
                throw new IllegalArgumentException("Invalid http method specified: " + request.method());
        }
    }

    private Response get(final Request request) {
        if (request.path().equalsIgnoreCase("/")) {
            return Response.builder()
                .statusCode("200")
                .statusMessage("OK")
                .headers(Map.of(
                    "Accept", "*/*"
                ))
                .body(String.join("\n", fileNames(pathAsString)))
                .build();
        } else {
            try {
                final String fileContent = read(request.path());

                if (fileContent == null) {
                    return Response.builder()
                        .statusCode("404")
                        .statusMessage("NOT FOUND")
                        .headers(Map.of(
                            "Accept", "*/*"
                        ))
                        .body("Could not find the specified file: " + request.path())
                        .build();
                }

                return Response.builder()
                    .statusCode("200")
                    .statusMessage("OK")
                    .headers(Map.of(
                        "Accept", "*/*"
                    ))
                    .body(fileContent)
                    .build();
            } catch (final MalformedInputException e) {
                return Response.builder()
                    .statusCode("500")
                    .statusMessage("INTERNAL SERVER ERROR")
                    .headers(Map.of(
                        "Accept", "*/*"
                    ))
                    .body("Contents of file contain non UTF-8 character encodings: " + request.path() + "\n" + e.getMessage())
                    .build();
            } catch (final IOException e) {
                return Response.builder()
                    .statusCode("500")
                    .statusMessage("INTERNAL SERVER ERROR")
                    .headers(Map.of(
                        "Accept", "*/*"
                    ))
                    .body("The specified file could not be read: " + request.path() + "\n" + e.getMessage())
                    .build();
            } catch (final FileServerProtocolError e) {
                return Response.builder()
                    .statusCode("401")
                    .statusMessage("UNAUTHORIZED ACCESS")
                    .headers(Map.of(
                        "Accept", "*/*"
                    ))
                    .body(e.getMessage())
                    .build();
            }
        }
    }

    private Response post(final Request request) {
        try {
            write(request.path(), request.body());

            return Response.builder()
                .statusCode("200")
                .statusMessage("OK")
                .headers(Map.of(
                    "Accept", "*/*"
                ))
                .body("File contents successfully written to: " + request.path() + "\n" + read(request.path()))
                .build();
        } catch (final IOException e) {
            return Response.builder()
                .statusCode("500")
                .statusMessage("INTERNAL SERVER ERROR")
                .headers(Map.of(
                    "Accept", "*/*"
                ))
                .body("Could not write to file: " + request.path() + "\n" + e.getMessage())
                .build();
        } catch (final FileServerProtocolError e) {
            return Response.builder()
                .statusCode("401")
                .statusMessage("UNAUTHORIZED ACCESS")
                .headers(Map.of(
                    "Accept", "*/*"
                ))
                .body(e.getMessage())
                .build();
        }
    }

    private List<File> files() {
        return ls(pathAsString);
    }

    private File file(final String relativeFilePath) {
        return files().stream().filter(e -> e.getAbsolutePath().equals(pathAsString + relativeFilePath)).findFirst().orElse(null);
    }

    private String read(final String relativeFilePath) throws IOException, FileServerProtocolError {
        final Path pathToFile = Paths.get(pathAsString + relativeFilePath);

        if (isUnauthorizedPathAccess(pathToFile)) {
            throw new FileServerProtocolError("Unauthorized access to path outside root working directory: " + pathAsString);
        }

        if (Files.isDirectory(pathToFile)) {
            return readDirectory(relativeFilePath);
        } else {
            return readFile(relativeFilePath);
        }
    }

    private String readFile(final String relativeFilePath) throws IOException {
        final File file = file(relativeFilePath);

        if (file == null) {
            return null;
        }

        return String.join("\n", Files.readAllLines(file.toPath()));
    }

    private String readDirectory(final String relativeDirectoryPath) {
        return ls(pathAsString + relativeDirectoryPath).stream()
            .map(e -> e.getAbsolutePath().replace(pathAsString, ""))
            .collect(Collectors.joining("\n"));
    }

    private void write(final String relativeFilePath, final String content) throws IOException, FileServerProtocolError {
        final Path pathToFile = Paths.get(pathAsString + relativeFilePath);

        if (isUnauthorizedPathAccess(pathToFile)) {
            throw new FileServerProtocolError("Unauthorized access to path outside root working directory: " + pathAsString);
        }

        if (!Files.exists(pathToFile.getParent())) {
            Files.createDirectory(pathToFile.getParent());
        }
        final File file = new File(pathToFile.toAbsolutePath().toString());
        try (final FileWriter fw = new FileWriter(file, false)) {
            fw.write(content);
        }
    }

    private List<String> fileNames(final String directoryName) {
        final var files = ls(directoryName);
        return files.stream()
            .map(File::getAbsolutePath)
            .map(e -> e.replace(pathAsString, ""))
            .collect(Collectors.toList());
    }


    private List<File> ls(final String directoryName) {
        return lsRec(directoryName, new ArrayList<>());
    }

    private List<File> lsRec(final String directoryName, final List<File> files) {
        final File[] directoryFiles = new File(directoryName).listFiles();
        if (directoryFiles != null) {
            for (final File file : directoryFiles) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    lsRec(file.getAbsolutePath(), files);
                }
            }
        }

        return files;
    }

    private boolean isUnauthorizedPathAccess(final Path pathRequested) throws IOException {
        return !(pathRequested.toFile().getCanonicalPath().startsWith(pathAsString));
    }
}
