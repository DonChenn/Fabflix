const urlParams = new URLSearchParams(window.location.search);
const movieId = urlParams.get("id");

function escapeHTML(str) {
    if (str === null || str === undefined) return 'N/A';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

document.addEventListener("DOMContentLoaded", function() {
    if (!movieId) {
        const container = document.getElementById("details");
        if (container) {
            container.innerHTML = `<p>Error: Movie ID is missing from the URL.</p>`;
        }
        const backLink = document.getElementById('back-to-list-link');
        if(backLink) backLink.href = 'movies.html';
        return;
    }
    fetch_single_movie();
    updateBackLink();
});

function updateBackLink() {
    fetch('api/session-data')
        .then(response => {
            if (!response.ok) {
                console.warn('Network response for session-data was not ok:', response.status);
                return { movieListUrl: null };
            }
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) {
                return response.json();
            } else {
                console.warn('Received non-JSON response for session-data');
                return { movieListUrl: null };
            }
        })
        .then(data => {
            const backLink = document.getElementById('back-to-list-link');
            if (backLink) {
                if (data && data.movieListUrl) {
                    backLink.href = data.movieListUrl;
                    console.log("Back link set to session URL:", data.movieListUrl);
                } else {
                    backLink.href = 'movies.html';
                    console.log("Back link set to default: movies.html");
                }
            }
        })
        .catch(error => {
            console.error('Error fetching or processing session data:', error);
            const backLink = document.getElementById('back-to-list-link');
            if (backLink) {
                backLink.href = 'movies.html';
                console.log("Back link set to default due to error: movies.html");
            }
        });
}

function addToCart(movieId) {
    console.log("Adding movie to cart:", movieId);
    jQuery.ajax({
        dataType: "json",
        method: "POST",
        url: "api/add-to-cart",
        data: {
            movieId: movieId
        },
        success: (resultData) => {
            console.log("Add to cart response:", resultData);
            if (resultData.status === "success") {
                const itemName = resultData.itemTitle ? resultData.itemTitle : `Movie ID ${resultData.itemId}`;
                alert("Added '" + escapeHTML(itemName) + "' to cart!");
            } else {
                alert("Failed to add item: " + (resultData.message || "Unknown error"));
            }
        },
        error: (jqXHR, textStatus, errorThrown) => {
            console.error("Add to cart error:", textStatus, errorThrown, jqXHR.responseText);
            let errorMsg = "Error adding item to cart.";
            if (jqXHR.responseJSON && jqXHR.responseJSON.message) {
                errorMsg += " " + jqXHR.responseJSON.message;
            } else if (jqXHR.status) {
                errorMsg += ` Status: ${jqXHR.status}`;
            }
            alert(errorMsg);
        }
    });
}


function fetch_single_movie() {
    if (!movieId) {
        console.error("Movie ID is missing for fetch_single_movie.");
        return;
    }

    fetch(`api/movie?id=${encodeURIComponent(movieId)}`)
        .then(response => {
            if (!response.ok) {
                return response.json().catch(() => null).then(errData => {
                    const errorDetail = errData?.message || errData?.error || response.statusText;
                    throw new Error(`HTTP error! Status: ${response.status} - ${errorDetail}`);
                });
            }
            return response.json();
        })
        .then(data => {
            const container = document.getElementById("details");
            if (!container) {
                console.error("Element with ID 'movie-details' not found.");
                return;
            }

            if (data.error) {
                container.innerHTML = `<p>Error loading movie: ${escapeHTML(data.error)}</p>`;
                return;
            }

            if (!data.movies || data.movies.length === 0) {
                container.innerHTML = `<p>No movie found with ID: ${escapeHTML(movieId)}</p>`;
                return;
            }

            const movie = data.movies[0];
            const actualMovieId = movie.id || movieId;

            let genresHTML = 'N/A';
            if (movie.genres && typeof movie.genres === 'string') {
                genresHTML = movie.genres.split(',')
                    .map(genre => {
                        const parts = genre.split(':');
                        let name = '';
                        if (parts.length >= 2) {
                            name = parts.slice(1).join(':').trim();
                        } else if (parts.length === 1) {
                            name = parts[0].trim();
                        }

                        if (name) {
                            const genreUrlParams = new URLSearchParams();
                            genreUrlParams.set('genre', name);
                            genreUrlParams.set('page', '1');
                            const genreUrl = `movies.html?${genreUrlParams.toString()}`;
                            return `<a href="${genreUrl}">${escapeHTML(name)}</a>`;
                        }
                        return null;
                    })
                    .filter(link => link !== null)
                    .join(', ');
                if (!genresHTML) genresHTML = 'N/A';
            }

            let starsHTML = 'N/A';
            if (movie.stars && typeof movie.stars === 'string') {
                starsHTML = movie.stars.split(',')
                    .map(star => {
                        const parts = star.split(':');
                        if (parts.length >= 2) {
                            const id = parts[0].trim();
                            const name = parts.slice(1).join(':').trim();
                            if (id && name) {
                                return `<a href="singlestar.html?id=${encodeURIComponent(id)}">${escapeHTML(name)}</a>`;
                            }
                        }
                        return null;
                    })
                    .filter(link => link !== null)
                    .join(', ');
                if (!starsHTML) starsHTML = 'N/A';
            }

            container.innerHTML = `
                <h2>${escapeHTML(movie.title)} (${escapeHTML(movie.year)})</h2>
                <p><span class="label">Director:</span> ${escapeHTML(movie.director)}</p>
                <p><span class="label">Rating:</span> ${movie.rating !== null && movie.rating !== undefined ? escapeHTML(movie.rating) : 'N/A'}</p>
                <p><span class="label">Genres:</span> ${genresHTML}</p>
                <p><span class="label">Stars:</span> ${starsHTML}</p>
                <div class="add-to-cart-section mt-3">
                    <button id="add-to-cart-button" class="btn btn-primary" data-movie-id="${escapeHTML(actualMovieId)}">
                        Add to Cart
                    </button>
                </div>
            `;

            const addToCartButton = container.querySelector('#add-to-cart-button');
            if (addToCartButton) {
                addToCartButton.addEventListener('click', function() {
                    const currentMovieId = this.dataset.movieId;
                    addToCart(currentMovieId);
                });
            }

        })
        .catch(error => {
            console.error("Error fetching movie:", error);
            const container = document.getElementById("details");
            if(container) {
                container.innerHTML = `<p>Error loading movie details: ${escapeHTML(error.message)}</p>`;
            }
        });
}