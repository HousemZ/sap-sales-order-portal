import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
public class WebServer {

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // This context serves your main HTML page
     //   server.createContext("/", new RootHandler());
        server.createContext("/", new StaticHandler());
        
        // This context will use your ApiClient to get SAP data
    //    server.createContext("/api/sales-orders", new ApiHandler());
        server.createContext("/api/", new ApiHandler());
        server.setExecutor(null); // Use the default executor
        server.start();
        
        System.out.println("Server started on port " + port);
        System.out.println("Open your browser and go to http://localhost:" + port);

	}

}
