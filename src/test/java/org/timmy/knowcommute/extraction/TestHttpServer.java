package org.timmy.knowcommute.extraction;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A real HTTP server on loopback. The JDK's own server avoids pulling in a mock-server
 * dependency, and the fetcher's redirect and status handling only mean anything against a
 * socket that actually speaks HTTP.
 */
class TestHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile String lastAcceptHeader = "";

    private TestHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static TestHttpServer start() {
        return new TestHttpServer();
    }

    TestHttpServer respond(String path, int status, String body) {
        return respond(path, status, "text/html; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    TestHttpServer respond(String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> {
            requestCount.incrementAndGet();
            lastAcceptHeader = String.valueOf(exchange.getRequestHeaders().getFirst("Accept"));
            if (contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return this;
    }

    TestHttpServer redirect(String path, String location) {
        server.createContext(path, exchange -> {
            requestCount.incrementAndGet();
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        return this;
    }

    URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    int requestCount() {
        return requestCount.get();
    }

    String lastAcceptHeader() {
        return lastAcceptHeader;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
