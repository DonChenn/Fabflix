package servlets;

import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet(name = "SearchServlet", urlPatterns = "/search")
public class SearchServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private DataSource dataSource;

    @Override
    public void init() throws ServletException {
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

        String title = request.getParameter("title");
        String yearParam = request.getParameter("year");
        String director = request.getParameter("director");
        String starName = request.getParameter("star_name");
        String ftQuery = request.getParameter("ft_query");

        try (Connection connection = dataSource.getConnection()) {

            StringBuilder queryBuilder = new StringBuilder(
                    "SELECT DISTINCT m.id, m.title, m.year, m.director " +
                            "FROM movies m "
            );

            if (starName != null && !starName.trim().isEmpty()) {
                queryBuilder.append("JOIN stars_in_movies sim ON m.id = sim.movieId JOIN stars s ON sim.starId = s.id ");
            }

            queryBuilder.append("WHERE 1=1 ");

            if (ftQuery != null && !ftQuery.trim().isEmpty()) {
                String booleanModeQuery = "";
                String[] terms = ftQuery.trim().split("\\s+");
                for (String term : terms) {
                    booleanModeQuery += "+" + term + "* ";
                }
                queryBuilder.append("AND MATCH(m.title) AGAINST(? IN BOOLEAN MODE) ");
            } else if (title != null && !title.trim().isEmpty()) {
                queryBuilder.append("AND m.title LIKE ? ");
            }

            if (yearParam != null && !yearParam.trim().isEmpty()) {
                queryBuilder.append("AND m.year = ? ");
            }
            if (director != null && !director.trim().isEmpty()) {
                queryBuilder.append("AND m.director LIKE ? ");
            }
            if (starName != null && !starName.trim().isEmpty()) {
                queryBuilder.append("AND s.name LIKE ? ");
            }

            try (PreparedStatement statement = connection.prepareStatement(queryBuilder.toString())) {

                int paramIndex = 1;
                if (ftQuery != null && !ftQuery.trim().isEmpty()) {
                    String booleanModeQuery = "";
                    String[] terms = ftQuery.trim().split("\\s+");
                    for (String term : terms) {
                        booleanModeQuery += "+" + term + "* ";
                    }
                    statement.setString(paramIndex++, booleanModeQuery.trim());
                } else if (title != null && !title.trim().isEmpty()) {
                    statement.setString(paramIndex++, "%" + title.trim() + "%");
                }

                if (yearParam != null && !yearParam.trim().isEmpty()) {
                    try {
                        statement.setInt(paramIndex++, Integer.parseInt(yearParam.trim()));
                    } catch (NumberFormatException e) {
                        request.getServletContext().log("Invalid year format: " + yearParam, e);
                    }
                }
                if (director != null && !director.trim().isEmpty()) {
                    statement.setString(paramIndex++, "%" + director.trim() + "%");
                }
                if (starName != null && !starName.trim().isEmpty()) {
                    statement.setString(paramIndex++, "%" + starName.trim() + "%");
                }

                try (ResultSet resultSet = statement.executeQuery()) {
                    JSONArray jsonArray = new JSONArray();
                    while (resultSet.next()) {
                        JSONObject movie = new JSONObject();
                        movie.put("id", resultSet.getString("id"));
                        movie.put("title", resultSet.getString("title"));
                        movie.put("year", resultSet.getInt("year"));
                        movie.put("director", resultSet.getString("director"));
                        jsonArray.put(movie);
                    }
                    out.write(jsonArray.toString());
                }
            }
        } catch (SQLException e) {
            request.getServletContext().log("Database error during search: ", e);
            JSONObject error = new JSONObject();
            error.put("status", "fail");
            error.put("message", "Database error: " + e.getMessage());
            out.write(error.toString());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            request.getServletContext().log("Search error: ", e);
            JSONObject error = new JSONObject();
            error.put("status", "fail");
            error.put("message", "Internal error: " + e.getMessage());
            out.write(error.toString());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}