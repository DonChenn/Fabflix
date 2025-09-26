package servlets;

import com.google.gson.JsonObject;
import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jasypt.util.password.StrongPasswordEncryptor;
import utils.RecaptchaVerifyUtils;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet(name = "EmployeeLoginServlet", urlPatterns = "/_dashboard/login-action")
public class EmployeeLoginServlet extends HttpServlet {
    private static final long serialVersionUID = 2L;
    private DataSource dataSource;

    public void init(ServletConfig config) {
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

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject responseJsonObject = new JsonObject();

        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String recaptchaResponse = request.getParameter("g-recaptcha-response");

        if (recaptchaResponse == null || recaptchaResponse.isEmpty()) {
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "Missing reCAPTCHA response.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(responseJsonObject.toString());
            out.close();
            return;
        }

        try {
            RecaptchaVerifyUtils.verify(recaptchaResponse);
            System.out.println("EmployeeLoginServlet: reCAPTCHA verified successfully for " + email);
        } catch (Exception e) {
            request.getServletContext().log("EmployeeLoginServlet: reCAPTCHA verification failed for " + email, e);
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "reCAPTCHA verification failed: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.write(responseJsonObject.toString());
            out.close();
            return;
        }

        if (email == null || password == null || email.trim().isEmpty() || password.trim().isEmpty()) {
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "Email and password are required.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(responseJsonObject.toString());
            out.close();
            return;
        }

        email = email.trim();

        Connection conn = null;
        PreparedStatement statement = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            System.out.println("EmployeeLoginServlet: Acquired database connection for " + email);

            String query = "SELECT password, fullname FROM employees WHERE email = ?";
            statement = conn.prepareStatement(query);
            statement.setString(1, email);

            rs = statement.executeQuery();

            if (rs.next()) {
                String hashedPasswordFromDB = rs.getString("password");
                String fullName = rs.getString("fullname");

                StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();
                if (passwordEncryptor.checkPassword(password, hashedPasswordFromDB)) {
                    System.out.println("Employee login successful for: " + email);

                    HttpSession session = request.getSession(true);
                    session.setAttribute("employeeEmail", email);
                    session.setAttribute("employeeFullName", fullName);
                    session.setMaxInactiveInterval(30 * 60);

                    responseJsonObject.addProperty("status", "success");
                    responseJsonObject.addProperty("message", "Login successful!");
                    response.setStatus(HttpServletResponse.SC_OK);

                } else {
                    System.out.println("Employee login failed (incorrect password) for: " + email);
                    responseJsonObject.addProperty("status", "fail");
                    responseJsonObject.addProperty("message", "Incorrect email or password.");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                }
            } else {
                System.out.println("Employee login failed (email not found): " + email);
                responseJsonObject.addProperty("status", "fail");
                responseJsonObject.addProperty("message", "Incorrect email or password.");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }

        } catch (SQLException e) {
            request.getServletContext().log("EmployeeLoginServlet: Database error for " + email, e);
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "Database error occurred. Please try again later.");
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            request.getServletContext().log("EmployeeLoginServlet: Unexpected error for " + email, e);
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "An unexpected error occurred.");
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) { request.getServletContext().log("Error closing ResultSet", e); }
            try { if (statement != null) statement.close(); } catch (SQLException e) { request.getServletContext().log("Error closing PreparedStatement", e); }
            try { if (conn != null) conn.close(); } catch (SQLException e) { request.getServletContext().log("Error closing Connection", e); }
            System.out.println("EmployeeLoginServlet: Released database resources for " + email);

            out.write(responseJsonObject.toString());
            out.close();
        }
    }
}