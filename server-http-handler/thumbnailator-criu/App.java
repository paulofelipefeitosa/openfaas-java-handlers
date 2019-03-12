import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;

public class App {

    public static void main(String[] args) throws Exception {
        int port = 9000;

        IHandler handler = new Handler();

        InetSocketAddress addr = new InetSocketAddress(port);
        HttpServer server = HttpServer.create(addr, 0);
        InvokeHandler invokeHandler = new InvokeHandler(handler);

        server.createContext("/", invokeHandler);
        server.createContext("/ping").setHandler(App::handlePing);
        server.createContext("/gc").setHandler(App::handleGC);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        invokeHandler.handle(new HttpExchange() {
            private Headers headers = new Headers();
            
            @Override
            public void setStreams(InputStream arg0, OutputStream arg1) {}
            @Override
            public void setAttribute(String arg0, Object arg1) {}
            @Override
            public void sendResponseHeaders(int arg0, long arg1) throws IOException {}
            @Override
            public Headers getResponseHeaders() {
                return new Headers();
            }
            @Override
            public int getResponseCode() {
                return 200;
            }
            @Override
            public OutputStream getResponseBody() {
                return new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {}
                };
            }
            @Override
            public URI getRequestURI() {
                try {
                    return new URI("http://localhost:9000/");
                } catch (URISyntaxException e) {
                    return null;
                }
            }
            @Override
            public String getRequestMethod() {
                return "GET";
            }
            @Override
            public Headers getRequestHeaders() {
                headers.add(Handler.WARM_REQUEST_HEADER_KEY, "true");
                return headers;
            }
            @Override
            public InputStream getRequestBody() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        return -1;
                    }
                };
            }
            @Override
            public InetSocketAddress getRemoteAddress() {
                return null;
            }
            @Override
            public String getProtocol() {
                return "http";
            }
            @Override
            public HttpPrincipal getPrincipal() {
                return null;
            }
            @Override
            public InetSocketAddress getLocalAddress() {
                return null;
            }
            @Override
            public HttpContext getHttpContext() {
                return null;
            }
            @Override
            public Object getAttribute(String arg0) {
                return null;
            }
            @Override
            public void close() {}
        });
        
        /*
        Map<String, String> reqHeaders = new HashMap<String, String>();
        reqHeaders.put();
        handler.Handle(new Request("", reqHeaders, "", ""));
        */
    }

    private static void handleGC(HttpExchange exchange) throws IOException {
        try {
            GC.force();
            exchange.sendResponseHeaders(200, 0);
        } finally {
            exchange.close();
        }
    }

    private static void handlePing(HttpExchange exchange) throws IOException {
        try {
            exchange.sendResponseHeaders(200, 0);
        } finally {
            exchange.close();
        }
    }

    static class InvokeHandler implements HttpHandler {
        IHandler handler;

        private InvokeHandler(IHandler handler) {
            this.handler = handler;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            String requestBody = "";
            String method = t.getRequestMethod();

            if (method.equalsIgnoreCase("POST")) {
                InputStream inputStream = t.getRequestBody();
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }
                inputStream.close();
                // StandardCharsets.UTF_8.name() > JDK 7
                requestBody = result.toString("UTF-8");
            }

            Headers reqHeaders = t.getRequestHeaders();
            Map<String, String> reqHeadersMap = new HashMap<String, String>();

            for (Map.Entry<String, java.util.List<String>> header : reqHeaders.entrySet()) {
                java.util.List<String> headerValues = header.getValue();
                if (headerValues.size() > 0) {
                    reqHeadersMap.put(header.getKey(), headerValues.get(0));
                }
            }

            IRequest req = new Request(requestBody, reqHeadersMap, t.getRequestURI().getRawQuery(),
                    t.getRequestURI().getPath());

            IResponse res = this.handler.Handle(req);

            String response = res.getBody();
            byte[] bytesOut = response.getBytes("UTF-8");

            Headers responseHeaders = t.getResponseHeaders();
            String contentType = res.getContentType();
            if (contentType.length() > 0) {
                responseHeaders.set("Content-Type", contentType);
            }

            for (Map.Entry<String, String> entry : res.getHeaders().entrySet()) {
                responseHeaders.set(entry.getKey(), entry.getValue());
            }

            t.sendResponseHeaders(res.getStatusCode(), bytesOut.length);

            OutputStream os = t.getResponseBody();
            os.write(bytesOut);
            os.close();

            t.close();
        }
    }

}
