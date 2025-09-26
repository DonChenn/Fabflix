package servlets;

import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.naming.Context;

@WebServlet(name = "SingleMovieServlet", urlPatterns = "/api/movie")
public class SingleMovieServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private DataSource dataSource;

    @Override
    public void init() {
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
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String movieId = request.getParameter("id");
        if (movieId == null || movieId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\": \"Missing movie id\"}");
            out.close();
            return;
        }

        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            connection = dataSource.getConnection();

            String query =
                    "WITH StarMovieCounts AS ( " +
                            "    SELECT starId, COUNT(DISTINCT movieId) AS movieCount " +
                            "    FROM stars_in_movies " +
                            "    GROUP BY starId " +
                            ") " +
                            "SELECT " +
                            "    m.id, m.title, m.year, m.director, r.rating, " +
                            "    GROUP_CONCAT(DISTINCT CONCAT(g.id, ':', g.name) ORDER BY g.name SEPARATOR ',') AS genres, " +
                            "    GROUP_CONCAT( " +
                            "        DISTINCT CONCAT(s.id, ':', s.name) " +
                            "        ORDER BY COALESCE(smc.movieCount, 0) DESC, s.name ASC " +
                            "        SEPARATOR ',' " +
                            "    ) AS stars " +
                            "FROM movies m " +
                            "LEFT JOIN ratings r ON m.id = r.movieId " +
                            "LEFT JOIN genres_in_movies gm ON m.id = gm.movieId " +
                            "LEFT JOIN genres g ON gm.genreId = g.id " +
                            "LEFT JOIN stars_in_movies sm ON m.id = sm.movieId " +
                            "LEFT JOIN stars s ON sm.starId = s.id " +
                            "LEFT JOIN StarMovieCounts smc ON s.id = smc.starId " +
                            "WHERE m.id = ? " +
                            "GROUP BY m.id, m.title, m.year, m.director, r.rating;";

            statement = connection.prepareStatement(query);
            statement.setString(1, movieId);
            resultSet = statement.executeQuery();

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"movies\":[");

            if (resultSet.next()) {
                String genres = escapeJson(resultSet.getString("genres"));
                String stars = escapeJson(resultSet.getString("stars"));

                jsonBuilder.append("{")
                        .append("\"id\":\"").append(escapeJson(resultSet.getString("id"))).append("\",")
                        .append("\"title\":\"").append(escapeJson(resultSet.getString("title"))).append("\",")
                        .append("\"year\":").append(resultSet.getInt("year")).append(",")
                        .append("\"director\":\"").append(escapeJson(resultSet.getString("director"))).append("\",");

                double rating = resultSet.getDouble("rating");
                if (resultSet.wasNull()) {
                    jsonBuilder.append("\"rating\":null,");
                } else {
                    jsonBuilder.append("\"rating\":").append(rating).append(",");
                }
                jsonBuilder.append("\"genres\":\"").append(genres != null ? genres : "").append("\",")
                        .append("\"stars\":\"").append(stars != null ? stars : "").append("\"")
                        .append("}");
            }

            jsonBuilder.append("]}");
            out.write(jsonBuilder.toString());

        } catch (SQLException e) {
            request.getServletContext().log("SQL Error in SingleMovieServlet for movie ID: " + movieId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"Database error occurred. " + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            request.getServletContext().log("Error in SingleMovieServlet for movie ID: " + movieId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"An unexpected error occurred. " + escapeJson(e.getMessage()) + "\"}");
        }
        finally {
            try {
                if (resultSet != null) resultSet.close();
            } catch (SQLException e) {
                request.getServletContext().log("Error closing ResultSet", e);
            }
            try {
                if (statement != null) statement.close();
            } catch (SQLException e) {
                request.getServletContext().log("Error closing Statement", e);
            }
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                request.getServletContext().log("Error closing Connection", e);
            }
            if (out != null) out.close();
        }
    }

    private String escapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}