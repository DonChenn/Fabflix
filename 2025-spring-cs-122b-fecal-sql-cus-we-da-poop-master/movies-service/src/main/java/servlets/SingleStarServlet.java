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

@WebServlet(name = "SingleStarServlet", urlPatterns = "/api/star")
public class SingleStarServlet extends HttpServlet {
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

        String starId = request.getParameter("id");

        if (starId == null || starId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\": \"Missing star id\"}");
            out.close();
            return;
        }

        Connection connection = null;
        PreparedStatement checkStarStatement = null;
        ResultSet checkStarResult = null;
        PreparedStatement moviesStatement = null;
        ResultSet moviesResultSet = null;

        try {
            connection = dataSource.getConnection();

            String checkStarQuery = "SELECT id, name, birthYear FROM stars WHERE id = ?";
            checkStarStatement = connection.prepareStatement(checkStarQuery);
            checkStarStatement.setString(1, starId);
            checkStarResult = checkStarStatement.executeQuery();

            if (!checkStarResult.next()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\": \"Star not found\"}");
                return;
            }

            String starIdVal = escapeJson(checkStarResult.getString("id"));
            String starNameVal = escapeJson(checkStarResult.getString("name"));
            String birthYearVal = "null";
            if (checkStarResult.getObject("birthYear") != null) {
                birthYearVal = String.valueOf(checkStarResult.getInt("birthYear"));
            }

            String moviesQuery =
                    "SELECT m.id AS movieId, m.title AS movieTitle, m.year, m.director " +
                            "FROM stars_in_movies sm " +
                            "JOIN movies m ON sm.movieId = m.id " +
                            "WHERE sm.starId = ? " +
                            "ORDER BY m.year DESC, m.title ASC";

            moviesStatement = connection.prepareStatement(moviesQuery);
            moviesStatement.setString(1, starId);
            moviesResultSet = moviesStatement.executeQuery();

            StringBuilder movieArrayBuilder = new StringBuilder();
            movieArrayBuilder.append("[");
            boolean firstMovie = true;
            while (moviesResultSet.next()) {
                if (!firstMovie) {
                    movieArrayBuilder.append(",");
                }
                firstMovie = false;
                movieArrayBuilder.append("{")
                        .append("\"movieId\":\"").append(escapeJson(moviesResultSet.getString("movieId"))).append("\",")
                        .append("\"title\":\"").append(escapeJson(moviesResultSet.getString("movieTitle"))).append("\",")
                        .append("\"year\":").append(moviesResultSet.getInt("year")).append(",")
                        .append("\"director\":\"").append(escapeJson(moviesResultSet.getString("director"))).append("\"")
                        .append("}");
            }
            movieArrayBuilder.append("]");

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{")
                    .append("\"starInfo\":{")
                    .append("\"starId\":\"").append(starIdVal).append("\",")
                    .append("\"starName\":\"").append(starNameVal).append("\",")
                    .append("\"birthYear\":").append(birthYearVal).append(",")
                    .append("\"movies\":").append(movieArrayBuilder.toString())
                    .append("}")
                    .append("}");

            out.write(jsonBuilder.toString());

        } catch (SQLException e) {
            request.getServletContext().log("SQL Error in SingleStarServlet for star ID: " + starId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"Database error occurred. " + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            request.getServletContext().log("Error in SingleStarServlet for star ID: " + starId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"An unexpected error occurred. " + escapeJson(e.getMessage()) + "\"}");
        }
        finally {
            try {
                if (checkStarResult != null) checkStarResult.close();
            } catch (SQLException e) {
                request.getServletContext().log("Error closing checkStarResult", e);
            }
            try {
                if (checkStarStatement != null) checkStarStatement.close();
            } catch (SQLException e) {
                request.getServletContext().log("Error closing checkStarStatement", e);
            }
            try {
                if (moviesResultSet != null) moviesResultSet.close();
            } catch (SQLException e) {
                request.getServletContext().log("Error closing moviesResultSet", e);
            }
            try {
                if (moviesStatement != null) moviesStatement.close();
            } catch (SQLException e) {
                request.getServletContext().log("Error closing moviesStatement", e);
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
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}