package httpnio.common;

import java.io.IOException;

public interface ApplicationProtocol {
    enum Type {
        FILESERVER;

        public static Type of(final String name) {
            switch (name) {
                case "FILESERVER":
                case "fileserver":
                case "FS":
                case "fs":
                default:
                    return FILESERVER;
            }
        }
    }

    interface Response extends ApplicationProtocol {
        HTTPResponse response(HTTPRequest request) throws IOException;


    }

    ApplicationProtocol copy() throws IllegalAccessException, IOException;
}
