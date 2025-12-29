package com.hotmock4j.http.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;


public class StaticResourceHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }

        try (InputStream is = getClass().getResourceAsStream("/hotmock4j-ui" + path)) {
            if (is == null) {
                sendNotFound(exchange, path);
                return;
            }

            String contentType = getContentType(path);
            byte[] data = is.readAllBytes();
            
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
            exchange.sendResponseHeaders(200, data.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }


    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        else if (path.endsWith(".js")) return "application/javascript";
        else if (path.endsWith(".css")) return "text/css";
        else return "text/plain";
    }


    private void sendNotFound(HttpExchange exchange, String path) throws IOException {
        String notFound = "404 Not Found: " + path;
        exchange.sendResponseHeaders(404, notFound.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(notFound.getBytes(StandardCharsets.UTF_8));
        }
    }
}
