package servlets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

@WebServlet(name = "OrderDetailsServlet", urlPatterns = "/api/order-confirmation-details")
public class OrderDetailsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        JsonObject responseJsonObject = new JsonObject();
        HttpSession session = request.getSession(false);

        if (session == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "No active session.");
            out.write(responseJsonObject.toString());
            out.close();
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> orderDetails = (Map<String, Object>) session.getAttribute("lastOrderConfirmationDetails");

        if (orderDetails != null) {
            responseJsonObject.addProperty("status", "success");
            Gson gson = new Gson();
            responseJsonObject.add("data", gson.toJsonTree(orderDetails));
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "No order details found in session. This could be due to a new session or details already retrieved.");
        }

        out.write(responseJsonObject.toString());
        out.close();
    }
}