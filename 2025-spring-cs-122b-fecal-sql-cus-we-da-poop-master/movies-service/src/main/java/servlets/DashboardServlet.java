package servlets;

import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.CallableStatement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DashboardServlet", urlPatterns = {"/_dashboard", "/api/dashboard/*"})
public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private DataSource dataSource;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
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

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestURI.substring(contextPath.length());

        HttpSession session = request.getSession(false);

        if (path.startsWith("/api/dashboard/")) {
            if (session == null || session.getAttribute("employeeEmail") == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"success\": false, \"message\": \"Authentication required. Please login.\"}");
                return;
            }
        }

        if (path.equals("/_dashboard")) {
            serveDashboardPage(request, response, session);
        } else if (path.equals("/api/dashboard/metadata")) {
            handleGetMetadata(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "The requested resource was not found.");
        }
    }

    private void serveDashboardPage(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws ServletException, IOException {
        String targetPage;
        if (session != null && session.getAttribute("employeeEmail") != null) {
            targetPage = "/_dashboard.html";
        } else {
            targetPage = "/_dashboard_login.html";
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(targetPage);
        dispatcher.forward(request, response);
    }


    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = requestURI.substring(contextPath.length());

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("employeeEmail") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\": false, \"message\": \"Authentication required. Please login.\"}");
            return;
        }

        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String requestBody = sb.toString();


        if (path.equals("/api/dashboard/add-star")) {
            handleAddStar(request, response, requestBody);
        } else if (path.equals("/api/dashboard/add-movie")) {
            handleAddMovie(request, response, requestBody);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "The requested API endpoint was not found.");
        }
    }

    private void handleAddStar(HttpServletRequest request, HttpServletResponse response, String requestBody) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        String jsonResponse;

        String starName = null;
        Integer birthYear = null;
        try {
            String tempBody = requestBody.replace("{", "").replace("}", "").trim();
            String[] pairs = tempBody.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                String key = keyValue[0].replace("\"", "").trim();
                String value = keyValue[1].replace("\"", "").trim();
                if ("star_name".equals(key)) {
                    starName = value;
                } else if ("birth_year".equals(key) && !value.equals("null") && !value.isEmpty()) {
                    try {
                        birthYear = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                    }
                }
            }
        } catch (Exception e) {
            jsonResponse = "{\"success\": false, \"message\": \"Error parsing request: Invalid JSON format.\"}";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(jsonResponse);
            out.flush();
            return;
        }

        if (starName == null || starName.trim().isEmpty()) {
            jsonResponse = "{\"success\": false, \"message\": \"Star name is required.\"}";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(jsonResponse);
            out.flush();
            return;
        }

        String sql = "INSERT INTO stars (id, name, birthYear) VALUES (?, ?, ?)";
        String checkSql = "SELECT id FROM stars WHERE name = ?";
        String maxIdSql = "SELECT MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)) as max_id FROM stars WHERE id LIKE 'nm%'";


        try (Connection conn = dataSource.getConnection()) {
            String nextStarId = null;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, starName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        jsonResponse = "{\"success\": false, \"message\": \"Star with this name already exists.\"}";
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        out.write(jsonResponse);
                        out.flush();
                        return;
                    }
                }
            }

            try(PreparedStatement maxIdStmt = conn.prepareStatement(maxIdSql);
                ResultSet rsMaxId = maxIdStmt.executeQuery()){
                int maxNumericId = 0;
                if(rsMaxId.next()){
                    maxNumericId = rsMaxId.getInt("max_id");
                }
                nextStarId = String.format("nm%07d", maxNumericId + 1);
            }


            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nextStarId);
                pstmt.setString(2, starName);
                if (birthYear != null) {
                    pstmt.setInt(3, birthYear);
                } else {
                    pstmt.setNull(3, java.sql.Types.INTEGER);
                }

                int affectedRows = pstmt.executeUpdate();

                if (affectedRows > 0) {
                    jsonResponse = "{\"success\": true, \"message\": \"Star '" + starName + "' added successfully with ID " + nextStarId + ".\", \"starId\": \"" + nextStarId + "\"}";
                } else {
                    jsonResponse = "{\"success\": false, \"message\": \"Failed to add star. No rows affected.\"}";
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            }
        } catch (SQLException e) {
            jsonResponse = "{\"success\": false, \"message\": \"Database error while adding star: " + e.getMessage() + "\"}";
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            jsonResponse = "{\"success\": false, \"message\": \"An unexpected error occurred: " + e.getMessage() + "\"}";
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        out.write(jsonResponse);
        out.flush();
    }

    private void handleAddMovie(HttpServletRequest request, HttpServletResponse response, String requestBody) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        String jsonResponse;

        String title = null;
        Integer year = null;
        String director = null;
        String starName = null;
        String genreName = null;

        try {
            String tempBody = requestBody.replace("{", "").replace("}", "").trim();
            String[] pairs = tempBody.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].replace("\"", "").trim();
                    String value = keyValue[1].replace("\"", "").trim();

                    switch (key) {
                        case "title": title = value; break;
                        case "year": year = Integer.parseInt(value); break;
                        case "director": director = value; break;
                        case "star_name": starName = value; break;
                        case "genre_name": genreName = value; break;
                    }
                }
            }
        } catch (Exception e) {
            jsonResponse = "{\"success\": false, \"message\": \"Error parsing request: Invalid JSON format.\"}";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(jsonResponse);
            out.flush();
            return;
        }

        if (title == null || title.trim().isEmpty() ||
                year == null ||
                director == null || director.trim().isEmpty() ||
                starName == null || starName.trim().isEmpty() ||
                genreName == null || genreName.trim().isEmpty()) {
            jsonResponse = "{\"success\": false, \"message\": \"All movie fields (title, year, director, star name, genre name) are required.\"}";
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(jsonResponse);
            out.flush();
            return;
        }

        String callProcedureSql = "{CALL add_movie(?, ?, ?, ?, ?, ?, ?)}";

        try (Connection conn = dataSource.getConnection();
             CallableStatement cstmt = conn.prepareCall(callProcedureSql)) {

            cstmt.setString(1, title);
            cstmt.setInt(2, year);
            cstmt.setString(3, director);
            cstmt.setString(4, starName);
            cstmt.setString(5, genreName);

            cstmt.registerOutParameter(6, java.sql.Types.VARCHAR);
            cstmt.registerOutParameter(7, java.sql.Types.VARCHAR);

            cstmt.execute();

            String newMovieId = cstmt.getString(6);
            String procedureMessage = cstmt.getString(7);

            if (newMovieId != null && !newMovieId.trim().isEmpty()) {
                jsonResponse = "{\"success\": true, \"message\": \"" + escapeJsonString(procedureMessage != null ? procedureMessage : "Movie added successfully!") + "\", \"movieId\": \"" + escapeJsonString(newMovieId) + "\"}";
            } else {
                jsonResponse = "{\"success\": false, \"message\": \"" + escapeJsonString(procedureMessage != null ? procedureMessage : "Failed to add movie.") + "\"}";
                if (procedureMessage != null && procedureMessage.toLowerCase().contains("duplicate")) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            }

        } catch (SQLException e) {
            jsonResponse = "{\"success\": false, \"message\": \"Database error while adding movie: " + e.getMessage() + "\"}";
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            jsonResponse = "{\"success\": false, \"message\": \"An unexpected error occurred: " + e.getMessage() + "\"}";
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        out.write(jsonResponse);
        out.flush();
    }

    private void handleGetMetadata(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        Map<String, Object> jsonOutput = new HashMap<>();
        Map<String, List<Map<String, String>>> metadataMap = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData dbMetaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet tablesResultSet = dbMetaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (tablesResultSet.next()) {
                    String tableName = tablesResultSet.getString("TABLE_NAME");
                    List<Map<String, String>> attributes = new ArrayList<>();

                    try (ResultSet columnsResultSet = dbMetaData.getColumns(catalog, null, tableName, "%")) {
                        while (columnsResultSet.next()) {
                            Map<String, String> attributeDetails = new HashMap<>();
                            attributeDetails.put("attributeName", columnsResultSet.getString("COLUMN_NAME"));
                            attributeDetails.put("type", columnsResultSet.getString("TYPE_NAME"));
                            attributes.add(attributeDetails);
                        }
                    }
                    metadataMap.put(tableName, attributes);
                }
            }
            jsonOutput.put("success", true);
            jsonOutput.put("data", metadataMap);

        } catch (SQLException e) {
            jsonOutput.put("success", false);
            jsonOutput.put("message", "Database error while fetching metadata: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            jsonOutput.put("success", false);
            jsonOutput.put("message", "An unexpected error occurred: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        StringBuilder jsonResponseBuilder = new StringBuilder("{");
        jsonResponseBuilder.append("\"success\":").append(jsonOutput.get("success")).append(",");
        if (jsonOutput.containsKey("message")) {
            jsonResponseBuilder.append("\"message\":\"").append(escapeJsonString(jsonOutput.get("message").toString())).append("\",");
        }
        jsonResponseBuilder.append("\"data\":{");
        if (jsonOutput.get("success").equals(true) && metadataMap != null) {
            boolean firstTable = true;
            for (Map.Entry<String, List<Map<String, String>>> tableEntry : metadataMap.entrySet()) {
                if (!firstTable) jsonResponseBuilder.append(",");
                jsonResponseBuilder.append("\"").append(escapeJsonString(tableEntry.getKey())).append("\":[");
                boolean firstAttr = true;
                for (Map<String, String> attr : tableEntry.getValue()) {
                    if (!firstAttr) jsonResponseBuilder.append(",");
                    jsonResponseBuilder.append("{");
                    jsonResponseBuilder.append("\"attributeName\":\"").append(escapeJsonString(attr.get("attributeName"))).append("\",");
                    jsonResponseBuilder.append("\"type\":\"").append(escapeJsonString(attr.get("type"))).append("\"");
                    jsonResponseBuilder.append("}");
                    firstAttr = false;
                }
                jsonResponseBuilder.append("]");
                firstTable = false;
            }
        }
        jsonResponseBuilder.append("}}");

        out.write(jsonResponseBuilder.toString());
        out.flush();
    }

    private String escapeJsonString(String str) {
        if (str == null) return null;
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void destroy() {
    }
}