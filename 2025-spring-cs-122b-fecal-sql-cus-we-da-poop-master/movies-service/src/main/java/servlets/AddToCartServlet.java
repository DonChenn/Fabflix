package servlets;

import com.google.gson.JsonObject;
import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import models.CartMovie;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "AddToCartServlet", urlPatterns = "/api/add-to-cart")
public class AddToCartServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private DataSource dataSource;

    public void init(ServletConfig config) {
        try {
            String dbHost = System.getenv("DB_MASTER_HOST");
            String dbPort = System.getenv("DB_PORT");
            String dbName = System.getenv("DB_NAME");
            String dbUser = System.getenv("DB_USER");
            String dbPassword = System.getenv("DB_PASSWORD");

            if (dbHost == null || dbName == null || dbUser == null || dbPassword == null) {
                System.out.println("WARNING: Database environment variables not fully set. Falling back to JNDI lookup for local development if configured.");
                try {
                    this.dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedb");
                    System.out.println("Successfully looked up DataSource via JNDI.");
                    return;
                } catch (NamingException e) {
                    System.err.println("JNDI lookup failed after environment variable check: " + e.getMessage());
                    throw new RuntimeException("Database configuration not found (checked Env Vars and JNDI).", e);
                }
            }

            if (dbPort == null || dbPort.isEmpty()){
                dbPort = "3306";
            }

            String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?allowPublicKeyRetrieval=true&autoReconnect=true&useSSL=false&cachePrepStmts=true&serverTimezone=UTC",
                    dbHost, dbPort, dbName);

            System.out.println("Initializing DataSource with JDBC URL: " + jdbcUrl.replace(dbPassword, "****"));

            MysqlDataSource mysqlDataSource = new MysqlDataSource();
            mysqlDataSource.setURL(jdbcUrl);
            mysqlDataSource.setUser(dbUser);
            mysqlDataSource.setPassword(dbPassword);

            this.dataSource = mysqlDataSource;
            System.out.println("DataSource initialized programmatically using environment variables.");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize LoginServlet DataSource: " + e.getMessage(), e);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject responseJsonObject = new JsonObject();

        String movieId = request.getParameter("movieId");

        if (movieId == null || movieId.trim().isEmpty()) {
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "Movie ID is required.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(responseJsonObject.toString());
            out.close();
            return;
        }

        HttpSession session = request.getSession();

        @SuppressWarnings("unchecked")
        Map<String, CartMovie> cart = (Map<String, CartMovie>) session.getAttribute("cart");
        if (cart == null) {
            cart = new HashMap<>();
            session.setAttribute("cart", cart);
        }

        try (Connection conn = dataSource.getConnection()) {
            if (cart.containsKey(movieId)) {
                CartMovie existingItem = cart.get(movieId);
                existingItem.incrementQuantity();
                responseJsonObject.addProperty("status", "success");
                responseJsonObject.addProperty("message", "Increased quantity for item: " + movieId);
                responseJsonObject.addProperty("itemId", movieId);
                responseJsonObject.addProperty("itemTitle", existingItem.getMovieTitle());
            } else {
                String query = "SELECT title FROM movies WHERE id = ? LIMIT 1";
                try (PreparedStatement statement = conn.prepareStatement(query)) {
                    statement.setString(1, movieId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            String movieTitle = rs.getString("title");
                            BigDecimal price = new BigDecimal("5.00");
                            CartMovie newItem = new CartMovie(movieId, movieTitle, price);
                            cart.put(movieId, newItem);
                            responseJsonObject.addProperty("status", "success");
                            responseJsonObject.addProperty("message", "Item added to cart: " + movieTitle);
                            responseJsonObject.addProperty("itemId", movieId);
                            responseJsonObject.addProperty("itemTitle", movieTitle);
                        } else {
                            responseJsonObject.addProperty("status", "fail");
                            responseJsonObject.addProperty("message", "Movie not found with ID: " + movieId);
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        }
                    }
                }
            }
            session.setAttribute("cart", cart);
            if (!response.isCommitted() && response.getStatus() != HttpServletResponse.SC_NOT_FOUND && response.getStatus() != HttpServletResponse.SC_BAD_REQUEST) {
                response.setStatus(HttpServletResponse.SC_OK);
            }

        } catch (Exception e) {
            request.getServletContext().log("Error in AddToCartServlet: ", e);
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "Error processing cart request.");
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } finally {
            if (!response.isCommitted()) {
                out.write(responseJsonObject.toString());
            }
            out.close();
        }
    }
}