
function handleCartData(resultDataJson) {
    console.log("handleCartData: received data", resultDataJson);

    let cartTableBodyElement = jQuery("#cart_table_body");
    let totalPriceElement = jQuery("#total_price");

    cartTableBodyElement.empty();

    let totalPrice = 0;
    const cartItems = resultDataJson.cart_items || [];

    if (cartItems.length === 0) {
        cartTableBodyElement.append("<tr><td colspan='5'>Your cart is empty.</td></tr>");
        totalPriceElement.text("0.00");
        return;
    }

    for (let i = 0; i < cartItems.length; i++) {
        let item = cartItems[i];
        let itemPrice = item.price != null ? Number(item.price) : 0;
        let itemQuantity = item.quantity != null ? Number(item.quantity) : 0;

        let subtotal = itemQuantity * itemPrice;
        totalPrice += subtotal;

        let rowHTML = "<tr>";
        rowHTML += "<td>" + (item.movie_title || 'N/A') + "</td>";
        rowHTML += "<td class='quantity-controls'>" +
            "<button class='btn btn-secondary btn-sm decrease-qty' data-movie-id='" + item.movie_id + "'>-</button> " +
            "<span class='item-qty'>" + itemQuantity + "</span> " +
            "<button class='btn btn-secondary btn-sm increase-qty' data-movie-id='" + item.movie_id + "'>+</button>" +
            "</td>";
        rowHTML += "<td>$" + itemPrice.toFixed(2) + "</td>";
        rowHTML += "<td>$" + subtotal.toFixed(2) + "</td>";
        rowHTML += "<td>" +
            "<button class='btn btn-danger btn-sm remove-item' data-movie-id='" + item.movie_id + "'>Remove</button>" +
            "</td>";
        rowHTML += "</tr>";
        cartTableBodyElement.append(rowHTML);
    }

    totalPriceElement.text(totalPrice.toFixed(2));
}

function updateQuantity(movieId, action) {
    console.log("updateQuantity: movieId=" + movieId + ", action=" + action);
    jQuery.ajax({
        dataType: "json",
        method: "POST",
        url: "api/shopping-cart",
        data: {
            'movie_id': movieId,
            'action': action
        },
        success: (response) => {
            console.log("Update successful", response);
            fetchCartData();
        },
        error: (jqXHR, textStatus, errorThrown) => {
            console.error("Update failed: " + textStatus, errorThrown, jqXHR.responseText);
            let errorMsg = "Failed to update cart. Please try again.";
            try {
                const errorJson = JSON.parse(jqXHR.responseText);
                if (errorJson && errorJson.message) {
                    errorMsg = errorJson.message;
                }
            } catch(e) {
                // Ignore parsing error
            }
            alert(errorMsg);
        }
    });
}

function fetchCartData() {
    jQuery.ajax({
        dataType: "json",
        method: "GET",
        url: "api/shopping-cart",
        success: (resultData) => handleCartData(resultData),
        error: (jqXHR, textStatus, errorThrown) => {
            console.error("Fetch cart data failed: " + textStatus, errorThrown, jqXHR.responseText);
            jQuery("#cart_table_body").html("<tr><td colspan='5' style='color:red; text-align:center;'>Error loading cart data. Please try again later.</td></tr>");
            jQuery("#total_price").text("Error");
        }
    });
}

jQuery(document).ready(function() {
    fetchCartData();

    let cartTableBodyElement = jQuery("#cart_table_body");

    cartTableBodyElement.on('click', '.increase-qty', function() {
        let movieId = jQuery(this).data("movie-id");
        if (movieId) {
            updateQuantity(movieId, 'increase');
        } else {
            console.error("Could not find movie ID for increase button.");
        }
    });

    cartTableBodyElement.on('click', '.decrease-qty', function() {
        let movieId = jQuery(this).data("movie-id");
        if (!movieId) {
            console.error("Could not find movie ID for decrease button.");
            return;
        }
        let quantitySpan = jQuery(this).closest('td').find('.item-qty');
        if (quantitySpan.length > 0) {
            let currentQuantity = parseInt(quantitySpan.text(), 10);

            if (!isNaN(currentQuantity)) {
                if (currentQuantity <= 1) {
                    if (confirm("Are you sure you want to remove this item?")) {
                        updateQuantity(movieId, 'remove');
                    }
                } else {
                    updateQuantity(movieId, 'decrease');
                }
            } else {
                console.error("Could not parse quantity for decrease button.");
            }
        } else {
            console.error("Could not find quantity span for decrease button.");
        }
    });

    cartTableBodyElement.on('click', '.remove-item', function() {
        let movieId = jQuery(this).data("movie-id");
        if (!movieId) {
            console.error("Could not find movie ID for remove button.");
            return;
        }
        if (confirm("Are you sure you want to remove this item?")) {
            updateQuantity(movieId, 'remove');
        }
    });
});