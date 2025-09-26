const form = document.getElementById("login-form");
const messageBox = document.getElementById("message");

form.addEventListener("submit", function (event) {
    event.preventDefault();
    messageBox.textContent = "";
    messageBox.style.color = '';

    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    const recaptchaResponse = grecaptcha.getResponse();

    if (recaptchaResponse.length === 0) {
        messageBox.textContent = "Please complete the reCAPTCHA.";
        messageBox.style.color = 'red';
        return;
    }

    messageBox.textContent = "Logging in...";
    messageBox.style.color = 'blue';

    const formData = new URLSearchParams();
    formData.append("email", email);
    formData.append("password", password);
    formData.append("g-recaptcha-response", recaptchaResponse);

    fetch("api/login", {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body: formData.toString()
    })
        .then(response => {
            grecaptcha.reset();

            if (!response.ok) {
                return response.json().catch(() => null).then(errData => {
                    throw new Error(errData?.message || `Login failed with status: ${response.status}`);
                });
            }
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.indexOf("application/json") !== -1) {
                return response.json();
            } else {
                throw new Error("Received an unexpected response format from the server.");
            }
        })
        .then(data => {
            if (data.status === "success") {
                // CORRECTED REDIRECTION
                window.location.href = "/movies/movies.html";
            } else {
                messageBox.textContent = data.message || "Login failed. Please check your credentials.";
                messageBox.style.color = 'red';
            }
        })
        .catch(error => {
            grecaptcha.reset();
            console.error("Login error:", error);
            messageBox.textContent = error.message || "An unexpected error occurred during login.";
            messageBox.style.color = 'red';
        });
});