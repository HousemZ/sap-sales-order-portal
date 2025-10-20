// src/main/java/com/example/ApiClient.java
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

public class ApiClient {

    private final HttpClient httpClient;
    private final String baseUri;
    private final String authHeaderValue;
    private String csrfToken;
    private String lastETag;
    private static final Pattern SALES_ORDER_HEADER_URL_PATTERN = Pattern.compile(".*A_SalesOrder\\('\\d+'\\)$");

    public ApiClient(String baseUri, String username, String password) {
        Objects.requireNonNull(baseUri, "Base URI cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(password, "Password cannot be null");

        this.baseUri = baseUri;
        
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(cookieManager)
                .build();
        
        this.authHeaderValue = createAuthHeader(username, password);
        this.csrfToken = null;
        this.lastETag = null;
    }
    
    private String createAuthHeader(String username, String password) {
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes());
    }

    public String getSalesOrders(int top) throws IOException, InterruptedException {
        String filterQuery = "CreatedByUser eq 'HZIDANI'";
        String orderbyQuery = "SalesOrder desc"; // Separate the order-by value
        String encodedFilter = URLEncoder.encode(filterQuery, StandardCharsets.UTF_8);
        String encodedOrderby = URLEncoder.encode(orderbyQuery, StandardCharsets.UTF_8);
        String fullUri = String.format(
        		"%sA_SalesOrder?$filter=%s&$orderby=%s&$top=%d",
        		this.baseUri,
        		encodedFilter,
        		encodedOrderby,
        		top);
        System.out.println(fullUri);
        return executeGetRequest(fullUri);
    }

    public String getSalesOrderDetail(String orderId) throws IOException, InterruptedException {
        Objects.requireNonNull(orderId, "Sales Order ID cannot be null");
        String fullUri = String.format("%sA_SalesOrder('%s')", this.baseUri, orderId);
        return executeGetRequest(fullUri);
    }

    public String getSalesOrderItems(String orderId) throws IOException, InterruptedException {
        Objects.requireNonNull(orderId, "Sales Order ID cannot be null");
        String fullUri = String.format("%sA_SalesOrder('%s')/to_Item", this.baseUri, orderId);
        return executeGetRequest(fullUri);
    }

    private void fetchCsrfToken() throws IOException, InterruptedException {
        if (this.csrfToken != null) return;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.baseUri))
                .header("Authorization", this.authHeaderValue)
                .header("X-CSRF-Token", "Fetch")
                .GET()
                .build();
        System.out.println("Fetching CSRF token...");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        this.csrfToken = response.headers().firstValue("x-csrf-token").orElse(null);
        if (this.csrfToken == null) {
            throw new RuntimeException("Could not fetch CSRF token.");
        }
        System.out.println("Successfully fetched CSRF token.");
    }

   
    
    public String createSalesOrder(String jsonPayload) throws IOException, InterruptedException {
        fetchCsrfToken(); // CSRF token is required for creation

        String fullUri = this.baseUri + "A_SalesOrder";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUri))
                .header("Authorization", this.authHeaderValue)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-CSRF-Token", this.csrfToken)
                .POST(BodyPublishers.ofString(jsonPayload))
                .build();

        System.out.println("Executing POST request to: " + fullUri);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // A successful creation typically returns 201 Created
        handleResponseStatusCode(response.statusCode());
        return response.body();
    }
    
    
    public String updateSalesOrder(String orderId, String newPurchaseOrder, String etag) throws IOException, InterruptedException {
        fetchCsrfToken();

        if (etag == null || etag.isBlank()) {
            throw new IllegalArgumentException("ETag is missing or empty. Cannot perform update.");
        }

        String fullUri = String.format("%sA_SalesOrder('%s')", this.baseUri, orderId);
        String jsonPayload = String.format("{\"PurchaseOrderByCustomer\": \"%s\"}", newPurchaseOrder);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUri))
                .header("Authorization", this.authHeaderValue)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-CSRF-Token", this.csrfToken)
                .header("If-Match", etag)
                // THE FIX: Change the method from "PATCH" to "MERGE"
                .method("MERGE", BodyPublishers.ofString(jsonPayload))
                .build();

        System.out.println("Executing MERGE request with ETag: " + etag); // Changed log message
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204) {
            System.out.println("SUCCESS: Update was successful.");
            return "";
        }
        
        handleResponseStatusCode(response.statusCode());
        return response.body();
    }
    
    
    
    //***************************************
    public void deleteSalesOrderItem(String orderId, String itemId, String etag) throws IOException, InterruptedException {
        fetchCsrfToken(); // Ensure we have a valid CSRF token

        if (etag == null || etag.isBlank()) {
            throw new IllegalArgumentException("ETag is missing. Cannot perform delete.");
        }

        // The OData endpoint for deleting a line item is its own entity set
        // with a composite key: A_SalesOrderItem(SalesOrder='...', SalesOrderItem='...').
        String fullUri = String.format(
            "%sA_SalesOrderItem(SalesOrder='%s',SalesOrderItem='%s')",
            this.baseUri,
            orderId,
            itemId
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUri))
                .header("Authorization", this.authHeaderValue)
                .header("X-CSRF-Token", this.csrfToken)
                .header("If-Match", etag) // Use the parent order's ETag for the lock check
                .DELETE()
                .build();
        
        System.out.println("Executing DELETE request to: " + fullUri);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // A successful delete operation returns a 204 No Content status code.
        if (response.statusCode() == 204) {
            System.out.println("SUCCESS: Item deleted successfully.");
            return;
        }
        
        // If the status is not 204, handle it as an error.
        handleResponseStatusCode(response.statusCode());
    }
    
    //************************************
    
    
    public String getLastETag() {
        return this.lastETag;
    }
    
    
    private String executeGetRequest(String fullUri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUri))
                .header("Authorization", this.authHeaderValue)
                .header("Accept", "application/xml")
                .GET()
                .build();
        
        System.out.println("Executing GET request to: " + fullUri);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (SALES_ORDER_HEADER_URL_PATTERN.matcher(fullUri).matches()) {
            this.lastETag = response.headers().firstValue("etag").orElse(null);
            System.out.println("Captured Sales Order Header ETag: " + this.lastETag);
        }
        
        handleResponseStatusCode(response.statusCode());
        return response.body();
    }

    private void handleResponseStatusCode(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            // Success
        } else {
            String errorMessage = String.format("FAILURE: Request failed with status code %d. ", statusCode);
            switch (statusCode) {
                case 401: errorMessage += "(Unauthorized)"; break;
                case 403: errorMessage += "(Forbidden: CSRF/ETag/Cookie invalid)"; break;
                case 412: errorMessage += "(Precondition Failed: Data changed)"; break;
                case 404: errorMessage += "(Not Found)"; break;
                case 500: errorMessage += "(Internal Server Error)"; break;
                default: errorMessage += "(Unknown error)"; break;
            }
            throw new RuntimeException(errorMessage);
        }
    }
}