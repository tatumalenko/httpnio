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

    final String directory;

    final Path path;

    public FileServerProtocol() throws IllegalAccessException {
        this("");
    }

    public FileServerProtocol(final String directory) throws IllegalAccessException {
        final Path currentDirectory = Paths.get("").toAbsolutePath();

        if (directory == null || directory.isEmpty()) {
            path = currentDirectory;
            this.directory = path.toString();
        } else {
            path = Paths.get(directory);
            this.directory = path.toAbsolutePath().toString();

            if (this.directory != null && !this.directory.isEmpty()) {
                if (!path.startsWith(currentDirectory)) {
                    throw new IllegalAccessException("Unauthorized file system access. Directory should be a child of the current working directory: " + currentDirectory.toAbsolutePath().toString());
                }
            } else {
                throw new IllegalStateException("The directory specified does not exist: " + directory);
            }
        }
    }

    @Override
    public Response response(final Request request) throws IOException {
        return dispatchResponse(request);
    }

    @Override
    public Protocol copy() throws IllegalAccessException {
        return new FileServerProtocol(directory);
    }

    private Response dispatchResponse(final Request request) throws IOException {
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
                .body(String.join("\n", fileNames(directory)))
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
        }

    }

    private List<File> files() {
        return ls(directory);
    }

    private File file(final String relativeFilePath) {
        return files().stream().filter(e -> e.getAbsolutePath().equals(directory + relativeFilePath)).findFirst().orElse(null);
    }

    private String read(final String relativeFilePath) throws IOException {
        final File file = file(relativeFilePath);

        if (file == null) {
            return null;
        }

        return String.join("\n", Files.readAllLines(file.toPath()));
    }

    private void write(final String relativeFilePath, final String content) throws IOException {
        final File file = new File(directory + relativeFilePath);
        try (final FileWriter fw = new FileWriter(file, false)) {
            fw.write(content);
        }
    }

    private List<String> fileNames(final String directoryName) {
        final var files = ls(directoryName);
        return files.stream()
            .map(File::getAbsolutePath)
            .map(e -> e.replace(directory, ""))
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
}
