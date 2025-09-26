const form = document.getElementById("login-form");
const messageBox = document.getElementById("message");

if (form && messageBox) {
    form.addEventListener("submit", function (event) {
        event.preventDefault();

        messageBox.textContent = "";
        messageBox.style.color = '';

        const emailInput = document.getElementById("email");
        const passwordInput = document.getElementById("password");

        const email = emailInput ? emailInput.value.trim() : null;
        const password = passwordInput ? passwordInput.value : null;

        const recaptchaResponse = grecaptcha.getResponse();

        if (!email || !password) {
            messageBox.textContent = "Email and password are required.";
            messageBox.style.color = 'red';
            grecaptcha.reset();
            return;
        }

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

        fetch("_dashboard/login-action", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: formData.toString()
        })
            .then(response => {
                grecaptcha.reset();
                if (!response.ok) {
                    return response.json().catch(() => {
                        throw new Error(`Login failed with status: ${response.status}. Server returned non-JSON response.`);
                    }).then(errData => {
                        throw new Error(errData?.message || `Login failed with status: ${response.status}`);
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.status === "success") {
                    console.log("Employee login successful, redirecting to _dashboard.html");
                    window.location.replace("_dashboard.html");
                } else {
                    messageBox.textContent = data.message || "Login failed. Please check your credentials or reCAPTCHA.";
                    messageBox.style.color = 'red';
                }
            })
            .catch(error => {
                grecaptcha.reset();
                console.error("Employee login error:", error);
                messageBox.textContent = error.message || "An error occurred during login.";
                messageBox.style.color = 'red';
            });
    });
} else {
    console.error("Could not find employee login form or message box element.");
}