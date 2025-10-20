import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;

public class StaticHandler implements HttpHandler {

    // A map to hold common MIME types for different file extensions
    private static final Map<String, String> MIME_TYPES = Map.of(
        "html", "text/html; charset=utf-8",
        "css", "text/css; charset=utf-8",
        "js", "application/javascript; charset=utf-8",
        "png", "image/png",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "gif", "image/gif"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI requestedUri = exchange.getRequestURI();
        String path = requestedUri.getPath();

        // If the path is just "/", default to serving index.html
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        // The path from the browser will start with a "/", so we remove it
        // to find the file in our resources folder.
        String resourcePath = path.substring(1);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                // If the file is not found, send a 404 error
                sendErrorResponse(exchange, 404, "404 - File Not Found");
                return;
            }
            
            // Get the correct MIME type for the file extension
            String mimeType = getMimeType(path);

            // Read the file's content into a byte array
            byte[] fileBytes = is.readAllBytes();
            
            // Send the successful response
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }

        } catch (Exception e) {
            // Handle any other errors that might occur
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "500 - Internal Server Error");
        }
    }

    private String getMimeType(String path) {
        // Find the last dot to get the file extension
        int lastDotIndex = path.lastIndexOf('.');
        if (lastDotIndex > 0) {
            String extension = path.substring(lastDotIndex + 1);
            // Look up the MIME type in our map, defaulting to a generic type if not found
            return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        exchange.sendResponseHeaders(statusCode, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }
}