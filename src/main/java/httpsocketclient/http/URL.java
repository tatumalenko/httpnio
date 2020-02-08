package httpsocketclient.http;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.net.MalformedURLException;

@Getter
@Accessors(fluent = true)
public final class URL {

    private final java.net.URL url;

    private final String protocol;

    private final String host;

    private final String path;

    private final String query;

    public URL(final String spec) throws MalformedURLException {
        url = new java.net.URL(spec);
        protocol = url.getProtocol();
        host = url.getHost();
        path = url.getPath();
        query = url.getQuery();
    }

    public URL(final String protocol, final String host, final String path, final String query) throws MalformedURLException {
        url = new java.net.URL(protocol + "://" + host + path + "?" + query);
        this.protocol = url.getProtocol();
        this.host = url.getHost();
        this.path = url.getPath();
        this.query = url.getQuery();
    }

    public URL(final String protocol, final String host, final String path) throws MalformedURLException {
        url = new java.net.URL(protocol + "://" + host + path);
        this.protocol = url.getProtocol();
        this.host = url.getHost();
        this.path = url.getPath();
        query = url.getQuery();
    }
}
