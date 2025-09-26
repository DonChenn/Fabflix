import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import utils.RecaptchaVerifyUtils;

@WebServlet(name = "FormReCaptcha", urlPatterns = "/form-recaptcha")
public class FormRecaptcha extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public String getServletInfo() {
        return "Servlet connects to MySQL database and displays result of a SELECT";
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter out = response.getWriter();

        String gRecaptchaResponse = request.getParameter("g-recaptcha-response");
        System.out.println("gRecaptchaResponse=" + gRecaptchaResponse);

        try {
            RecaptchaVerifyUtils.verify(gRecaptchaResponse);
        } catch (Exception e) {
            response.setContentType("text/html");
            out.println("<html><head><title>Error</title></head><body>");
            out.println("<p>recaptcha verification error</p>");
            out.println("<p>" + e.getMessage() + "</p>");
            out.println("</body></html>");
            out.close();
            return;
        }

        String loginUser = "mytestuser";
        String loginPasswd = "My6$Password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedbexample";
        String name = request.getParameter("name");

        response.setContentType("text/html");

        if (name == null) {
            name = "";
        }

        String query = "SELECT * from stars where name like ?";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            response.setContentType("text/html");
            out.println("<html><head><title>Error</title></head><body>");
            out.println("<p>Database driver error:</p><p>" + e.getMessage() + "</p>");
            out.println("</body></html>");
            out.close();
            return;
        }

        try (Connection dbCon = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);
             PreparedStatement statement = dbCon.prepareStatement(query)) {

            statement.setString(1, "%" + name + "%");

            try (ResultSet rs = statement.executeQuery()) {

                out.println("<html><head><title>MovieDB: Found Records</title></head>");
                out.println("<body><h1>MovieDB: Found Records</h1>");
                out.println("<table border>");
                out.println("<tr><td>ID</td><td>Name</td></tr>");
                while (rs.next()) {
                    String m_ID = rs.getString("ID");
                    String m_Name = rs.getString("name");
                    out.println(String.format("<tr><td>%s</td><td>%s</td></tr>", m_ID, m_Name));
                }
                out.println("</table>");
                out.println("</body></html>");
            }
        } catch (SQLException e) {
            response.setContentType("text/html");
            out.println("<html><head><title>Error</title></head><body>");
            out.println("<p>SQL error:</p><p>" + e.getMessage() + "</p>");
            out.println("</body></html>");
        } catch (Exception e) {
            response.setContentType("text/html");
            out.println("<html><head><title>Error</title></head><body>");
            out.println("<p>General error:</p><p>" + e.getMessage() + "</p>");
            out.println("</body></html>");
        } finally {
            if(out != null) {
                out.close();
            }
        }
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }
}