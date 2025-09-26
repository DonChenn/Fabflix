package models;

import java.math.BigDecimal;

public class CartMovie {
    private final String movieId;
    private final String movieTitle;
    private final BigDecimal price;
    private int quantity;

    public CartMovie(String movieId, String movieTitle, BigDecimal price) {
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.price = price;
        this.quantity = 1;
    }

    public String getMovieId() {
        return movieId;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void incrementQuantity() {
        this.quantity++;
    }

    public BigDecimal getSubtotal() {
        return price.multiply(new BigDecimal(this.quantity));
    }
}