package servlets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import models.CartMovie;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "ShoppingCartServlet", urlPatterns = "/api/shopping-cart")
public class ShoppingCartServlet extends HttpServlet {

    private static final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        HttpSession session = request.getSession();

        @SuppressWarnings("unchecked")
        Map<String, CartMovie> cart = (Map<String, CartMovie>) session.getAttribute("cart");
        if (cart == null) {
            cart = new HashMap<>();
            session.setAttribute("cart", cart);
        }

        JsonArray cartJsonArray = new JsonArray();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (Map.Entry<String, CartMovie> entry : cart.entrySet()) {
            CartMovie item = entry.getValue();
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("movie_id", item.getMovieId());
            itemJson.addProperty("movie_title", item.getMovieTitle());
            itemJson.addProperty("quantity", item.getQuantity());

            BigDecimal itemPrice = item.getPrice();
            itemJson.addProperty("price", itemPrice != null ? itemPrice.doubleValue() : 0.0);

            if (itemPrice != null) {
                BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
                totalPrice = totalPrice.add(itemPrice.multiply(quantity));
            }
            cartJsonArray.add(itemJson);
        }

        JsonObject responseJsonObject = new JsonObject();
        responseJsonObject.add("cart_items", cartJsonArray);
        responseJsonObject.addProperty("total_price", totalPrice.doubleValue());

        out.write(gson.toJson(responseJsonObject));
        response.setStatus(HttpServletResponse.SC_OK);
        out.close();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        HttpSession session = request.getSession();

        @SuppressWarnings("unchecked")
        Map<String, CartMovie> cart = (Map<String, CartMovie>) session.getAttribute("cart");
        if (cart == null) {
            cart = new HashMap<>();
        }

        String movieId = request.getParameter("movie_id");
        String action = request.getParameter("action");
        JsonObject responseJsonObject = new JsonObject();

        if (movieId == null || action == null || !cart.containsKey(movieId)) {
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "Missing parameters or item not found in cart.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else {
            CartMovie item = cart.get(movieId);
            boolean updateSuccessful = false;

            switch (action) {
                case "increase":
                    item.incrementQuantity();
                    updateSuccessful = true;
                    break;
                case "decrease":
                    if (item.getQuantity() > 1) {
                        item.setQuantity(item.getQuantity() - 1);
                    } else {
                        cart.remove(movieId);
                    }
                    updateSuccessful = true;
                    break;
                case "remove":
                    cart.remove(movieId);
                    updateSuccessful = true;
                    break;
                default:
                    responseJsonObject.addProperty("message", "Invalid action specified.");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    break;
            }

            if (updateSuccessful) {
                session.setAttribute("cart", cart);
                responseJsonObject.addProperty("status", "success");
                responseJsonObject.addProperty("message", "Cart updated successfully.");
                response.setStatus(HttpServletResponse.SC_OK);
            } else if (!response.isCommitted()){
                responseJsonObject.addProperty("status", "fail");
                if (!responseJsonObject.has("message")) {
                    responseJsonObject.addProperty("message", "Failed to update cart.");
                }
                if (response.getStatus() == HttpServletResponse.SC_OK) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            }
        }
        out.write(gson.toJson(responseJsonObject));
        out.close();
    }
}