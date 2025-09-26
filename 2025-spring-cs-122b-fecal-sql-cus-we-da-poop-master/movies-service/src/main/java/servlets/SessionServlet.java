package servlets;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import com.google.gson.JsonObject;

@WebServlet(name = "SessionServlet", urlPatterns = "/api/session-data")
public class SessionServlet extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        HttpSession session = request.getSession(false);
        String movieListUrl = "movies.html";

        if (session != null) {
            Object storedUrl = session.getAttribute("movieListUrl");
            if (storedUrl instanceof String) {
                movieListUrl = (String) storedUrl;
            }
        }

        jsonResponse.addProperty("movieListUrl", movieListUrl);
        out.write(jsonResponse.toString());
        out.close();
    }
}