package servlets;

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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import models.JwtUtil;
import org.jasypt.util.password.StrongPasswordEncryptor;

import com.mysql.cj.jdbc.MysqlDataSource;

@WebServlet(name = "LoginServlet", urlPatterns = "/login")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String RECAPTCHA_SECRET_KEY = "6LfllDYrAAAAAF6OqDjuvceH-OQYq_XJnAFht-Rw";
    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

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

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String recaptchaResponse = request.getParameter("g-recaptcha-response");

        if (recaptchaResponse == null || recaptchaResponse.isEmpty()) {
            out.write("{\"status\":\"fail\", \"message\":\"Missing reCAPTCHA response.\"}");
            out.close();
            return;
        }

        try {
            if (!verifyRecaptcha(recaptchaResponse)) {
                out.write("{\"status\":\"fail\", \"message\":\"reCAPTCHA verification failed.\"}");
                out.close();
                return;
            }
        } catch (Exception e) {
            getServletContext().log("reCAPTCHA verification error: ", e); // Corrected: Use getServletContext()
            out.write("{\"status\":\"fail\", \"message\":\"Error during reCAPTCHA verification.\"}");
            out.close();
            return;
        }

        if (email == null || password == null || email.trim().isEmpty() || password.trim().isEmpty()) {
            response.getWriter().write("{\"status\":\"fail\", \"message\":\"Missing email or password\"}");
            out.close();
            return;
        }

        email = email.trim();

        try (Connection connection = dataSource.getConnection()) {

            String query = "SELECT password FROM customers WHERE email = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, email);
                try (ResultSet resultSet = statement.executeQuery()) {

                    if (!resultSet.next()) {
                        out.write("{\"status\":\"fail\", \"message\":\"Email not found.\"}");
                    } else {
                        String encryptedPasswordFromDB = resultSet.getString("password");
                        StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();

                        boolean passwordMatch = passwordEncryptor.checkPassword(password, encryptedPasswordFromDB);

                        if (!passwordMatch) {
                            out.write("{\"status\":\"fail\", \"message\":\"Incorrect password.\"}");
                        } else {
                            String token = JwtUtil.generateToken(email, null);
                            JwtUtil.updateJwtCookie(response, token);
                            out.write("{\"status\":\"success\"}");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            getServletContext().log("Database error during login: ", e); // Corrected: Use getServletContext()
            out.write("{\"status\":\"fail\", \"message\":\"Database error during login.\"}");
        } catch (Exception e) {
            getServletContext().log("Login error: ", e); // Corrected: Use getServletContext()
            out.write("{\"status\":\"fail\", \"message\":\"An unexpected internal error occurred.\"}");
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private boolean verifyRecaptcha(String gRecaptchaResponse) throws IOException {
        if (gRecaptchaResponse == null || gRecaptchaResponse.isEmpty()) {
            return false;
        }

        URL verifyUrl = new URL(RECAPTCHA_VERIFY_URL);
        HttpURLConnection conn = (HttpURLConnection) verifyUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setDoOutput(true);

        String postParams = "secret=" + RECAPTCHA_SECRET_KEY + "&response=" + gRecaptchaResponse;

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = postParams.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        } catch (IOException e) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            getServletContext().log("reCAPTCHA verify HTTP error response: " + response.toString()); // Corrected
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return response.toString().contains("\"success\": true");
    }
}