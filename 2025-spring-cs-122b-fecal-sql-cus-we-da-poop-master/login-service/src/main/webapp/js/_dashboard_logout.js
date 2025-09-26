const logoutLink = document.getElementById("logout-link");

if (logoutLink) {
    logoutLink.addEventListener("click", function(event) {
        event.preventDefault();
        console.log("Logout link clicked");

        fetch("logout", {
            method: "GET",
            credentials: "same-origin"
        })
            .then(response => {
                console.log("Logout fetch response status:", response.status);
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const contentType = response.headers.get("content-type");
                if (contentType && contentType.indexOf("application/json") !== -1) {
                    return response.json();
                } else {
                    console.warn("Received non-JSON response from logout endpoint. Assuming success based on status.");
                    if (response.ok) {
                        return { status: "success" };
                    } else {
                        throw new Error(`Received non-JSON response with error status: ${response.status}`);
                    }
                }
            })
            .then(data => {
                console.log("Processed logout response data:", data);
                if (data && data.status === "success") {
                    console.log("Logout reported success, attempting redirect...");
                    try {
                        const baseUrl = window.location.href;
                        const targetUrl = new URL('_dashboard_login.html', baseUrl);
                        console.log("Redirecting to:", targetUrl.href);
                        window.location.replace(targetUrl.href);
                    } catch (e) {
                        console.error("Error creating redirect URL:", e);
                        alert("Logout succeeded but redirect failed. Please navigate to login manually.");
                    }
                } else {
                    console.warn("Logout endpoint did not report success:", data ? data.message : "No status message.");
                    alert("Logout failed or server response was unexpected.");
                }
            })
            .catch(error => {
                console.error("Logout fetch/processing error:", error);
                alert("An error occurred during logout: " + error.message);
            });

        return false;
    });
} else {
    console.error("Logout link element with ID 'logout-link' not found.");
}