const urlParams = new URLSearchParams(window.location.search);
const starId = urlParams.get("id");

document.addEventListener("DOMContentLoaded", function() {
    fetch_single_star();
    updateBackLink();
});

function updateBackLink() {
    fetch('api/session-data')
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return response.json();
        })
        .then(data => {
            const backLink = document.getElementById('back-to-list-link');
            if (backLink && data.movieListUrl) {
                backLink.href = data.movieListUrl;
                console.log("Back link set to:", data.movieListUrl);
            } else if (backLink) {
                backLink.href = 'movies.html';
            }
        })
        .catch(error => {
            console.error('Error fetching session data:', error);
            const backLink = document.getElementById('back-to-list-link');
            if (backLink) {
                backLink.href = 'movies.html';
            }
        });
}

function fetch_single_star() {
    if (!starId) {
        document.getElementById("details").innerHTML = "<p>Error: No star ID provided</p>";
        return;
    }

    console.log(`Star ID: ${starId}`);

    fetch(`api/star?id=${starId}`)
        .then(response => {
            console.log("Response status:", response.status);
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
            return response.text();
        })
        .then(text => {
            console.log("Response text:", text);
            let data;
            try {
                data = JSON.parse(text);
            } catch (e) {
                console.error("Error parsing JSON:", e);
                document.getElementById("details").innerHTML = `<p>Error parsing server response. Check console for details.</p><pre>${text}</pre>`;
                return;
            }

            const container = document.getElementById("details");

            if (data.error) {
                container.innerHTML = `<p>Error from server: ${data.error}</p>`;
                return;
            }

            if (!data.starInfo) {
                container.innerHTML = `<p>Error: Star information structure not found in response.</p>`;
                console.error("Missing starInfo in data:", data);
                return;
            }

            let html = `
                <h2>${data.starInfo.starName || "Unknown Name"}</h2>
                <p>
                    <span class="label">Birth Year:</span> ${data.starInfo.birthYear === null ? "N/A" : data.starInfo.birthYear}<br>
                    <span class="label">Movies:</span>
                </p>
            `;

            if (!Array.isArray(data.starInfo.movies) || data.starInfo.movies.length === 0) {
                html += "<p style='margin-top: -10px; padding-left: 15px;'><em>No movies listed for this star.</em></p>";
            } else {
                html += "<ul>";
                data.starInfo.movies.forEach(movie => {
                    const movieId = movie.movieId || '#';
                    const movieTitle = movie.title || 'Unknown Title';
                    const movieYear = movie.year || 'N/A';
                    const movieDirector = movie.director || 'Unknown Director';

                    html += `<li>
                        <a href="singlemovie.html?id=${encodeURIComponent(movieId)}">
                            ${movieTitle} (${movieYear}) - Directed by ${movieDirector}
                        </a>
                    </li>`;
                });
                html += "</ul>";
            }

            container.innerHTML = html;

        })
        .catch(error => {
            console.error("Error fetching star:", error);
            document.getElementById("details").innerHTML = `<p>Error loading star details: ${error.message}</p>`;
        });
}