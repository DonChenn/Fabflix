package servlets;

import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.naming.Context;


@WebServlet(name = "MoviesServlet", urlPatterns = "/api/movies")
public class MoviesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final List<String> ALLOWED_SORT_FIELDS = Arrays.asList("title", "rating");
    private static final List<String> ALLOWED_ORDERS = Arrays.asList("asc", "desc");
    private static final List<Integer> ALLOWED_LIMITS = Arrays.asList(10, 25, 50, 100);
    private static final int DEFAULT_LIMIT = 25;
    private static final int DEFAULT_PAGE = 1;

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


    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String genreFilter = request.getParameter("genre");
        String title = request.getParameter("title");
        String yearParam = request.getParameter("year");
        String director = request.getParameter("director");
        String starName = request.getParameter("star_name");
        String titleInitial = request.getParameter("titleInitial");
        String fulltextQueryParam = request.getParameter("ft_query");


        String sort1 = request.getParameter("sort1");
        String order1 = request.getParameter("order1");
        String sort2 = request.getParameter("sort2");
        String order2 = request.getParameter("order2");

        int limit = DEFAULT_LIMIT;
        int page = DEFAULT_PAGE;

        try {
            String limitParam = request.getParameter("limit");
            if (limitParam != null) {
                int requestedLimit = Integer.parseInt(limitParam);
                if (ALLOWED_LIMITS.contains(requestedLimit)) {
                    limit = requestedLimit;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid limit parameter format: " + request.getParameter("limit"));
        }

        try {
            String pageParam = request.getParameter("page");
            if (pageParam != null) {
                page = Integer.parseInt(pageParam);
                if (page < 1) {
                    page = DEFAULT_PAGE;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid page parameter format: " + request.getParameter("page"));
        }

        int offset = (page - 1) * limit;

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Gson gson = new Gson();
        JsonObject jsonResponse = new JsonObject();
        PrintWriter out = response.getWriter();

        String queryString = request.getQueryString();
        String currentUrl = "movies.html" + (queryString != null ? "?" + queryString : "");

        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        List<Object> parameters = new ArrayList<>();
        StringBuilder queryBuilder = new StringBuilder();

        try {
            connection = dataSource.getConnection();

            String starMovieCountsCTE = "WITH StarMovieCounts AS ( " +
                    "    SELECT starId, COUNT(DISTINCT movieId) AS movieCount " +
                    "    FROM stars_in_movies " +
                    "    GROUP BY starId " +
                    "), ";
            String rankedStarsCTE = "RankedStars AS ( " +
                    "    SELECT " +
                    "        sm.movieId, " +
                    "        s.id AS starId, " +
                    "        s.name AS starName, " +
                    "        ROW_NUMBER() OVER(PARTITION BY sm.movieId ORDER BY COALESCE(smc.movieCount, 0) DESC, s.name ASC) as rn " +
                    "    FROM stars_in_movies sm " +
                    "    JOIN stars s ON sm.starId = s.id " +
                    "    LEFT JOIN StarMovieCounts smc ON s.id = smc.starId " +
                    "), ";
            String topStarsPerMovieCTE = "TopStarsPerMovie AS ( " +
                    "    SELECT " +
                    "        movieId, " +
                    "        GROUP_CONCAT(CONCAT(starId, ':', starName) ORDER BY rn SEPARATOR ', ') AS topStars " +
                    "    FROM RankedStars " +
                    "    WHERE rn <= 3 " +
                    "    GROUP BY movieId " +
                    ") ";

            queryBuilder.append(starMovieCountsCTE);
            queryBuilder.append(rankedStarsCTE);
            queryBuilder.append(topStarsPerMovieCTE);

            queryBuilder.append("SELECT m.id, m.title, m.year, m.director, COALESCE(r.rating, 0.0) AS rating, ");
            queryBuilder.append("       GROUP_CONCAT(DISTINCT g_main.name ORDER BY g_main.name SEPARATOR ', ') AS genres, ");
            queryBuilder.append("       tspm.topStars AS stars ");
            queryBuilder.append("FROM movies m ");
            queryBuilder.append("LEFT JOIN ratings r ON m.id = r.movieId ");
            queryBuilder.append("LEFT JOIN genres_in_movies gm_main ON m.id = gm_main.movieId ");
            queryBuilder.append("LEFT JOIN genres g_main ON gm_main.genreId = g_main.id ");
            queryBuilder.append("LEFT JOIN TopStarsPerMovie tspm ON m.id = tspm.movieId ");

            StringBuilder whereClause = new StringBuilder();

            if (fulltextQueryParam != null && !fulltextQueryParam.trim().isEmpty()) {
                String[] keywords = fulltextQueryParam.trim().split("\\s+");
                StringBuilder ftsBooleanQuery = new StringBuilder();
                for (String keyword : keywords) {
                    if (!keyword.isEmpty()) {
                        ftsBooleanQuery.append("+").append(keyword).append("* ");
                    }
                }
                if (ftsBooleanQuery.length() > 0) {
                    if (whereClause.length() > 0) whereClause.append(" AND ");
                    whereClause.append("MATCH(m.title) AGAINST(? IN BOOLEAN MODE)");
                    parameters.add(ftsBooleanQuery.toString().trim());
                }
            }


            if (genreFilter != null && !genreFilter.trim().isEmpty()) {
                if (whereClause.length() > 0) whereClause.append(" AND ");
                whereClause.append("EXISTS (SELECT 1 FROM genres_in_movies gim_check JOIN genres g_check ON gim_check.genreId = g_check.id WHERE gim_check.movieId = m.id AND g_check.name = ?)");
                parameters.add(genreFilter.trim());
            }

            if (yearParam != null && !yearParam.trim().isEmpty()) {
                try {
                    int year = Integer.parseInt(yearParam.trim());
                    if (whereClause.length() > 0) whereClause.append(" AND ");
                    whereClause.append("m.year = ?");
                    parameters.add(year);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid year format: " + yearParam);
                }
            }
            if (director != null && !director.trim().isEmpty()) {
                if (whereClause.length() > 0) whereClause.append(" AND ");
                whereClause.append("m.director LIKE ?");
                parameters.add("%" + director.trim() + "%");
            }
            if (starName != null && !starName.trim().isEmpty()) {
                if (whereClause.length() > 0) whereClause.append(" AND ");
                whereClause.append("EXISTS (SELECT 1 FROM stars_in_movies sim_check JOIN stars s_check ON sim_check.starId = s_check.id WHERE sim_check.movieId = m.id AND s_check.name LIKE ?)");
                parameters.add("%" + starName.trim() + "%");
            }
            if (titleInitial != null && !titleInitial.trim().isEmpty()) {
                if (whereClause.length() > 0) whereClause.append(" AND ");
                if (titleInitial.equals("*")) {
                    whereClause.append("m.title REGEXP '^[^a-zA-Z0-9]'");
                } else {
                    whereClause.append("m.title LIKE ?");
                    parameters.add(titleInitial.trim() + "%");
                }
            }

            if (whereClause.length() > 0) {
                queryBuilder.append("WHERE ").append(whereClause);
            }

            queryBuilder.append(" GROUP BY m.id, m.title, m.year, m.director, COALESCE(r.rating, 0.0), tspm.topStars ");

            StringBuilder orderByBuilder = new StringBuilder("ORDER BY ");
            boolean firstSortParam = true;
            if (isValidSortParam(sort1, order1)) {
                orderByBuilder.append(getColumnForSortField(sort1)).append(" ").append(order1.toLowerCase());
                firstSortParam = false;
            }
            if (isValidSortParam(sort2, order2)) {
                String sortCol1 = getColumnForSortField(sort1);
                String sortCol2 = getColumnForSortField(sort2);
                if (!firstSortParam) orderByBuilder.append(", ");

                if (!sortCol1.equalsIgnoreCase(sortCol2) || firstSortParam) {
                    orderByBuilder.append(sortCol2).append(" ").append(order2.toLowerCase());
                    firstSortParam = false;
                }
            }
            if (firstSortParam) {
                orderByBuilder.append(getColumnForSortField("rating")).append(" DESC, ").append(getColumnForSortField("title")).append(" ASC");
            }
            queryBuilder.append(orderByBuilder.toString()).append(" ");


            queryBuilder.append("LIMIT ? OFFSET ?");
            parameters.add(limit);
            parameters.add(offset);

            String finalQuery = queryBuilder.toString();
            System.out.println("Preparing Query: " + finalQuery);
            System.out.println("Parameters: " + parameters);

            statement = connection.prepareStatement(finalQuery);

            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }

            resultSet = statement.executeQuery();
            JsonArray moviesArray = new JsonArray();
            int resultsCount = 0;
            while (resultSet.next()) {
                resultsCount++;
                JsonObject movieJson = new JsonObject();
                movieJson.addProperty("id", resultSet.getString("id"));
                movieJson.addProperty("title", resultSet.getString("title"));
                movieJson.addProperty("year", resultSet.getInt("year"));
                movieJson.addProperty("director", resultSet.getString("director"));

                double rating = resultSet.getDouble("rating");
                if (resultSet.wasNull()) {
                    movieJson.add("rating", null);
                } else {
                    double roundedRating = Math.round(rating * 10.0) / 10.0;
                    movieJson.addProperty("rating", roundedRating);
                }


                movieJson.addProperty("genres", resultSet.getString("genres"));
                movieJson.addProperty("stars", resultSet.getString("stars"));

                moviesArray.add(movieJson);
            }


            jsonResponse.add("movies", moviesArray);
            jsonResponse.addProperty("currentPage", page);
            jsonResponse.addProperty("limit", limit);
            jsonResponse.addProperty("hasMoreResults", resultsCount == limit);

            out.write(gson.toJson(jsonResponse));

        } catch (SQLException e) {
            request.getServletContext().log("SQL Error fetching movies: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Database error occurred while fetching movies.");
            errorResponse.addProperty("detail", e.getMessage());
            if (!response.isCommitted()) {
                out.write(gson.toJson(errorResponse));
            }
        } catch (Exception e) {
            request.getServletContext().log("Error fetching movies: ", e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("error", "An internal error occurred while fetching movies.");
                errorResponse.addProperty("detail", e.getMessage());
                out.write(gson.toJson(errorResponse));
            }
        } finally {
            try { if (resultSet != null) resultSet.close(); } catch (SQLException e) { request.getServletContext().log("Error closing ResultSet", e); }
            try { if (statement != null) statement.close(); } catch (SQLException e) { request.getServletContext().log("Error closing Statement", e); }
            try { if (connection != null) connection.close(); } catch (SQLException e) { request.getServletContext().log("Error closing Connection", e); }
            if (out != null && !response.isCommitted()) {
            }
        }
    }

    private boolean isValidSortParam(String field, String order) {
        return field != null && !field.trim().isEmpty() && !field.equalsIgnoreCase("none") &&
                order != null && !order.trim().isEmpty() &&
                ALLOWED_SORT_FIELDS.contains(field.toLowerCase()) &&
                ALLOWED_ORDERS.contains(order.toLowerCase());
    }

    private String getColumnForSortField(String field) {
        if (field == null) return "COALESCE(r.rating, 0.0)";
        String lowerField = field.toLowerCase();
        if ("title".equals(lowerField)) {
            return "m.title";
        } else if ("rating".equals(lowerField)) {
            return "COALESCE(r.rating, 0.0)";
        }
        return "COALESCE(r.rating, 0.0)";
    }

}