jQuery(document).ready(function() {
    fetchOrderConfirmationDetails();
});

function fetchOrderConfirmationDetails() {
    jQuery.ajax({
        dataType: "json",
        method: "GET",
        url: "api/order-confirmation-details",
        success: function(resultData) {
            if (resultData && resultData.status === "success" && resultData.data) {
                const orderData = resultData.data;

                const saleIdsDisplay = jQuery("#sale-ids-display");
                if (orderData.saleIds && orderData.saleIds.length > 0) {
                    saleIdsDisplay.text(orderData.saleIds.join(", "));
                } else {
                    saleIdsDisplay.text("N/A");
                }

                const orderItemsTbody = jQuery("#order-items-tbody");
                orderItemsTbody.empty();

                if (orderData.items && orderData.items.length > 0) {
                    orderData.items.forEach(function(item) {
                        let itemPrice = parseFloat(item.price || 0);
                        let quantity = parseInt(item.quantity || 0);
                        let subtotal = itemPrice * quantity;
                        let rowHTML = "<tr>" +
                            "<td>" + escapeHtml(item.movieTitle || 'N/A') + "</td>" +
                            "<td>" + quantity + "</td>" +
                            "<td>$" + itemPrice.toFixed(2) + "</td>" +
                            "<td>$" + subtotal.toFixed(2) + "</td>" +
                            "</tr>";
                        orderItemsTbody.append(rowHTML);
                    });
                } else {
                    orderItemsTbody.append("<tr><td colspan='4'>No items found in this order.</td></tr>");
                }

                const totalPriceDisplay = jQuery("#total-price-display");
                if (orderData.totalPrice !== null && orderData.totalPrice !== undefined) {
                    totalPriceDisplay.text(parseFloat(orderData.totalPrice).toFixed(2));
                } else {
                    totalPriceDisplay.text("N/A");
                }

            } else {
                jQuery("#sale-ids-display").text("Error loading details.");
                jQuery("#order-items-tbody").html("<tr><td colspan='4'>Error loading order items.</td></tr>");
                jQuery("#total-price-display").text("Error");
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
            jQuery("#sale-ids-display").text("Error loading details.");
            jQuery("#order-items-tbody").html("<tr><td colspan='4'>Error loading order items.</td></tr>");
            jQuery("#total-price-display").text("Error");
        }
    });
}

function escapeHtml(unsafe) {
    if (unsafe === null || typeof unsafe === 'undefined') return '';
    return String(unsafe)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}