import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class ApiHandler implements HttpHandler {

    private final ApiClient apiClient;
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("/api/sales-order/(\\d+)");
    //****
    private static final Pattern ORDER_ITEM_PATTERN = Pattern.compile("/api/sales-order/(\\d+)/item/(\\d+)");


    public ApiHandler() {
        String serviceUrl = "http://srvappwa1.wynsys.local:8800/sap/opu/odata/sap/API_SALES_ORDER_SRV/";
        String username = "HZIDANI";
        String password = "@Azerty123";
        this.apiClient = new ApiClient(serviceUrl, username, password);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        
       
            try {
                if ("GET".equalsIgnoreCase(requestMethod)) {
                    handleGetRequest(exchange);
                } else if ("POST".equalsIgnoreCase(requestMethod)) {
                    handlePostRequest(exchange);
                } else if ("PATCH".equalsIgnoreCase(requestMethod) || "MERGE".equalsIgnoreCase(requestMethod)) {
                	handlePatchRequest(exchange);
                } else if ("DELETE".equalsIgnoreCase(requestMethod)) {
                    handleDeleteRequest(exchange); // NEW: Route DELETE requests.
                } else {
                    sendErrorResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        
    }

    private void handleGetRequest(HttpExchange exchange) throws IOException, InterruptedException {
        String path = exchange.getRequestURI().getPath();
        String responseBody;
        Matcher matcher = ORDER_ID_PATTERN.matcher(path);

        if (path.equals("/api/sales-orders")) {
            responseBody = apiClient.getSalesOrders(20);
        } else if (matcher.find()) {
            String orderId = matcher.group(1);
            if (path.endsWith("/items")) {
                responseBody = apiClient.getSalesOrderItems(orderId);
            } else {
                responseBody = apiClient.getSalesOrderDetail(orderId);
            }
        } else {
            sendErrorResponse(exchange, 404, "Not Found: API endpoint does not exist.");
            return;
        }
        sendSuccessResponse(exchange, responseBody, "application/xml", 200);
    }

    private void handlePatchRequest(HttpExchange exchange) throws IOException, InterruptedException {
        String path = exchange.getRequestURI().getPath();
        Matcher matcher = ORDER_ID_PATTERN.matcher(path);

        if (matcher.find()) {
            String orderId = matcher.group(1);
            
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(requestBody);
            
            String newPurchaseOrder = json.getString("PurchaseOrderByCustomer");
            String etag = json.getString("etag");
            System.out.println(newPurchaseOrder);
            apiClient.updateSalesOrder(orderId, newPurchaseOrder, etag);

            exchange.sendResponseHeaders(204, -1);
        } else {
            sendErrorResponse(exchange, 404, "Not Found: The resource to update does not exist.");
        }
    }

    //*****************************
    private void handlePostRequest(HttpExchange exchange) throws IOException, InterruptedException {
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/api/sales-orders")) {
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            String responseBody = apiClient.createSalesOrder(requestBody);

            // Respond with 201 Created and the new entity
            sendSuccessResponse(exchange, responseBody, "application/json", 201);
        } else {
            sendErrorResponse(exchange, 404, "Not Found: This endpoint does not support POST.");
        }
    }
    
    //**************************************
    
    private void handleDeleteRequest(HttpExchange exchange) throws IOException, InterruptedException {
        String path = exchange.getRequestURI().getPath();
        Matcher matcher = ORDER_ITEM_PATTERN.matcher(path);

        if (matcher.find()) {
            String orderId = matcher.group(1);
            String itemId = matcher.group(2);
            // The ETag is sent in the 'If-Match' header by the frontend.
            String etag = exchange.getRequestHeaders().getFirst("If-Match");

            apiClient.deleteSalesOrderItem(orderId, itemId, etag);

            // Respond with 204 No Content on successful deletion.
            exchange.sendResponseHeaders(204, -1);
        } else {
            sendErrorResponse(exchange, 404, "Not Found: The item to delete does not exist or the URL is malformed.");
        }
    }
    
    //*****************************
    
    private void sendSuccessResponse(HttpExchange exchange, String body, String contentType, int statusCode) throws IOException {
        byte[] responseBytes = body.getBytes("UTF-8");

        // FIX #1: Add the "customs declaration" to expose the ETag header to the browser.
        exchange.getResponseHeaders().set("Access-Control-Expose-Headers", "ETag");

        // FIX #2: If the ApiClient captured an ETag, add it to the response headers for the browser.
        if (apiClient.getLastETag() != null) {
            exchange.getResponseHeaders().set("ETag", apiClient.getLastETag());
        }
        
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        
        // FIX #3: Use the provided statusCode instead of hardcoding 200.
        exchange.sendResponseHeaders(statusCode, responseBytes.length > 0 ? responseBytes.length : -1);
        
        if (responseBytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
    
    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] responseBytes = message.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}