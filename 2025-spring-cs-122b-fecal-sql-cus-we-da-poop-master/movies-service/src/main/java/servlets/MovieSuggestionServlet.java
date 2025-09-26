package servlets;

import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet(name = "MovieSuggestionServlet", urlPatterns = "/api/movie-suggestion")
public class MovieSuggestionServlet extends HttpServlet {
    private static final long serialVersionUID = 2L;

    private DataSource dataSource;

    public void init() throws jakarta.servlet.ServletException {
        try {
            String dbHost = System.getenv("DB_SLAVE_HOST");
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

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        String query = request.getParameter("query");
        JSONObject jsonResponse = new JSONObject();

        if (query == null || query.trim().isEmpty() || query.trim().length() < 3) {
            jsonResponse.put("suggestions", new JSONArray());
            out.write(jsonResponse.toString());
            out.close();
            return;
        }

        String booleanModeQuery = "";
        String[] terms = query.trim().split("\\s+");
        for (String term : terms) {
            booleanModeQuery += "+" + term + "* ";
        }
        booleanModeQuery = booleanModeQuery.trim();


        try (Connection connection = dataSource.getConnection()) {
            String sqlQuery = "SELECT id, title FROM movies WHERE MATCH(title) AGAINST(? IN BOOLEAN MODE) LIMIT 10";

            try (PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
                statement.setString(1, booleanModeQuery);

                ResultSet resultSet = statement.executeQuery();
                JSONArray suggestionsArray = new JSONArray();

                while (resultSet.next()) {
                    JSONObject movieSuggestion = new JSONObject();
                    movieSuggestion.put("id", resultSet.getString("id"));
                    movieSuggestion.put("title", resultSet.getString("title"));
                    suggestionsArray.put(movieSuggestion);
                }
                jsonResponse.put("suggestions", suggestionsArray);
            }
        } catch (SQLException e) {
            request.getServletContext().log("MovieSuggestionServlet SQL Error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Database error: " + e.getMessage());
        } catch (Exception e) {
            request.getServletContext().log("MovieSuggestionServlet Error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Server error: " + e.getMessage());
        }

        out.write(jsonResponse.toString());
        out.close();
    }
}