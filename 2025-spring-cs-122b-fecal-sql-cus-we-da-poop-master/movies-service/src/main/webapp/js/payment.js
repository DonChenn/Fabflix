

function fetchTotalPrice() {
    jQuery.ajax({
        dataType: "json",
        method: "GET",
        url: "api/shopping-cart",
        success: (resultData) => {
            if (resultData && resultData.total_price !== undefined) {
                jQuery("#total_price").text(resultData.total_price.toFixed(2));
            } else {
                jQuery("#total_price").text("Error");
                console.error("Could not get total price from cart API response:", resultData);
            }
        },
        error: (jqXHR, textStatus, errorThrown) => {
            console.error("Fetch total price failed: " + textStatus, errorThrown);
            jQuery("#total_price").text("Error");
        }
    });
}

jQuery("#payment_form").submit(function(event) {
    event.preventDefault();

    const paymentMessageDiv = jQuery("#payment_message");
    paymentMessageDiv.text("");

    const firstName = jQuery("#first_name").val().trim();
    const lastName = jQuery("#last_name").val().trim();
    const ccNumber = jQuery("#cc_number").val().trim();
    const ccExpiry = jQuery("#cc_expiry").val().trim();

    if (!firstName || !lastName || !ccNumber || !ccExpiry) {
        paymentMessageDiv.text("Please fill out all payment fields.");
        return;
    }

    const formData = {
        first_name: firstName,
        last_name: lastName,
        cc_number: ccNumber,
        cc_expiry: ccExpiry
    };

    console.log("Submitting payment data:", formData);

    jQuery.ajax({
        dataType: "json",
        method: "POST",
        url: "api/place-order",
        data: formData,
        success: (resultData) => {
            console.log("Payment response:", resultData);
            if (resultData.status === "success") {
                const baseUrl = window.location.href;
                const targetUrl = new URL('confirmation.html', baseUrl);
                window.location.replace(targetUrl.href);
            } else {
                paymentMessageDiv.text(resultData.message || "Payment failed. Please check your details.");
            }
        },
        error: (jqXHR, textStatus, errorThrown) => {
            console.error("Payment submission error:", textStatus, errorThrown, jqXHR.responseText);
            let errorMsg = "An error occurred while processing your payment. Please try again.";
            try {
                const errorJson = JSON.parse(jqXHR.responseText);
                if (errorJson && errorJson.message) {
                    errorMsg = errorJson.message;
                }
            } catch (e) {

            }
            paymentMessageDiv.text(errorMsg);
        }
    });
});

jQuery(document).ready(function() {
    fetchTotalPrice();
});