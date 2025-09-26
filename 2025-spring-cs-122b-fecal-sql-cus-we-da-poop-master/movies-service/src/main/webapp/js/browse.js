document.addEventListener("DOMContentLoaded", function() {
    fetchInitials();
    fetchGenres();
});

function fetchInitials() {
    fetch("api/title-initials")
        .then(response => response.json())
        .then(data => {
            const initialList = document.getElementById("initial-list");
            initialList.innerHTML = "";

            data.initials.forEach(initial => {
                const link = document.createElement("a");
                link.href = `movies.html?titleInitial=${encodeURIComponent(initial)}`;
                link.textContent = initial;
                initialList.appendChild(link);
            });
        })
        .catch(error => {
            console.error("Error fetching initials:", error);
            document.getElementById("initial-list").textContent = "Failed to load initials.";
        });
}

function fetchGenres() {
    fetch("api/genres")
        .then(response => response.json())
        .then(data => {
            const genreList = document.getElementById("genre-list");
            genreList.innerHTML = "";

            data.genres.forEach(genre => {
                const link = document.createElement("a");
                link.href = `movies.html?genre=${encodeURIComponent(genre)}`;
                link.textContent = genre;
                genreList.appendChild(link);
            });
        })
        .catch(error => {
            console.error("Error fetching genres:", error);
            document.getElementById("genre-list").textContent = "Failed to load genres.";
        });
}