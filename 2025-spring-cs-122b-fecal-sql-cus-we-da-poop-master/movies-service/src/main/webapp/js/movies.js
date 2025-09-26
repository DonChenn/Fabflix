const searchForm = document.getElementById("search-form");
const resetButton = document.getElementById("reset-button");
const applySortButton = document.getElementById("apply-sort-button");

const sort1Field = document.getElementById("sort1-field");
const sort1Order = document.getElementById("sort1-order");
const sort2Field = document.getElementById("sort2-field");
const sort2Order = document.getElementById("sort2-order");

const resultsPerPageSelect = document.getElementById("results-per-page");
const prevButton = document.getElementById("prev-button");
const nextButton = document.getElementById("next-button");
const pageInfoSpan = document.getElementById("page-info");

const titleInput = document.getElementById("title");
const autocompleteSuggestionsDiv = document.getElementById("autocomplete-suggestions");

let debounceTimeout;
const DEBOUNCE_DELAY = 300;
const MIN_CHARS_FOR_AUTOCOMPLETE = 3;
let currentSuggestions = [];
let selectedSuggestionIndex = -1;
const autocompleteCache = {};

const toggleBrowseButton = document.getElementById("toggle-browse-button");
const browseSection = document.getElementById("browse-section");

if (toggleBrowseButton && browseSection) {
    toggleBrowseButton.addEventListener("click", function() {
        if (browseSection.style.display === "none" || browseSection.style.display === "") {
            browseSection.style.display = "block";
        } else {
            browseSection.style.display = "none";
        }
    });
}

function escapeHTML(str) {
    if (str === null || str === undefined) return 'N/A';
    return String(str).replace(/[&<>"'`=\/]/g, function (s) {
        return {
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
            '/': '&#x2F;', '`': '&#x60;', '=': '&#x3D;'
        }[s];
    });
}

function getUrlParams() {
    return new URLSearchParams(window.location.search);
}

function buildUpdatedUrl(newParams) {
    const urlParams = getUrlParams();
    const preservedParams = ['title', 'year', 'director', 'star_name', 'genre', 'titleInitial', 'ft_query'];
    const currentFilters = {};
    preservedParams.forEach(param => {
        if (urlParams.has(param)) {
            currentFilters[param] = urlParams.get(param);
        }
    });

    const updatedUrlParams = new URLSearchParams();
    for (const key in currentFilters) {
        updatedUrlParams.set(key, currentFilters[key]);
    }

    for (const key in newParams) {
        if (newParams[key] !== null && newParams[key] !== undefined && newParams[key] !== '') {
            updatedUrlParams.set(key, newParams[key]);
        } else {
            updatedUrlParams.delete(key);
        }
    }

    if (!updatedUrlParams.has('page')) {
        updatedUrlParams.set('page', '1');
    }
    if (!updatedUrlParams.has('limit')) {
        const currentPagination = getCurrentPaginationParams(getUrlParams());
        updatedUrlParams.set('limit', currentPagination.limit);
    }

    const currentSort = getCurrentSortParams(getUrlParams());
    if (!updatedUrlParams.has('sort1') && currentSort.sort1) {
        updatedUrlParams.set('sort1', currentSort.sort1);
    }
    if (!updatedUrlParams.has('order1') && currentSort.order1) {
        updatedUrlParams.set('order1', currentSort.order1);
    }
    if (!updatedUrlParams.has('sort2') && currentSort.sort2 && currentSort.sort2 !== 'none') {
        updatedUrlParams.set('sort2', currentSort.sort2);
    }
    if (!updatedUrlParams.has('order2') && currentSort.order2 && currentSort.sort2 && currentSort.sort2 !== 'none') {
        updatedUrlParams.set('order2', currentSort.order2);
    }


    return updatedUrlParams.toString();
}


function getCurrentSortParams(urlParams) {
    return {
        sort1: urlParams.get('sort1') || 'rating',
        order1: urlParams.get('order1') || 'desc',
        sort2: urlParams.get('sort2') || 'title',
        order2: urlParams.get('order2') || 'asc'
    };
}

function getCurrentPaginationParams(urlParams) {
    const defaultLimit = 25;
    const limit = parseInt(urlParams.get('limit'), 10) || defaultLimit;
    const page = parseInt(urlParams.get('page'), 10) || 1;
    return {
        limit: [10, 25, 50, 100].includes(limit) ? limit : defaultLimit,
        page: page > 0 ? page : 1
    };
}

function updateSortControlsUI(urlParams) {
    const currentSort = getCurrentSortParams(urlParams);
    if(sort1Field) sort1Field.value = currentSort.sort1;
    if(sort1Order) sort1Order.value = currentSort.order1;
    if(sort2Field) sort2Field.value = (currentSort.sort2 && currentSort.sort2 !== 'none') ? currentSort.sort2 : 'none';
    if(sort2Order) {
        sort2Order.value = currentSort.order2;
        sort2Order.disabled = (sort2Field.value === 'none');
    }
}

function updatePaginationControlsUI(urlParams) {
    const currentPagination = getCurrentPaginationParams(urlParams);
    if(resultsPerPageSelect) resultsPerPageSelect.value = currentPagination.limit;
    if(pageInfoSpan) pageInfoSpan.textContent = `Page ${currentPagination.page}`;
}

function updateButtonStates(currentPage, hasMoreResults) {
    if(prevButton) prevButton.disabled = (currentPage <= 1);
    if(nextButton) nextButton.disabled = !hasMoreResults;
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

function fetch_movies() {
    const moviesDetailsDiv = document.getElementById("movies-details");
    const urlParams = getUrlParams();

    if (!urlParams.has('sort1')) {
        urlParams.set('sort1', 'rating'); urlParams.set('order1', 'desc');
        urlParams.set('sort2', 'title'); urlParams.set('order2', 'asc');
    }
    if (!urlParams.has('limit')) { urlParams.set('limit', '25'); }
    if (!urlParams.has('page')) { urlParams.set('page', '1'); }

    let apiUrl = "api/movies?" + urlParams.toString();
    console.log("Fetching:", apiUrl);

    fetch(apiUrl)
        .then(response => {
            if (response.status === 401) {
                console.log("User not logged in, redirecting to login.");
                window.location.href = '/login.html';
                throw new Error("User not logged in.");
            }
            if (!response.ok) {
                return response.json().then(errData => {
                    throw new Error(`HTTP error! Status: ${response.status} - ${errData.error || errData.message || 'Unknown error'}`);
                }).catch((e) => {
                    throw new Error(`HTTP error! Status: ${response.status} ${response.statusText}. Server Response: ${e.message || 'No details'}`);
                });
            }
            return response.json();
        })
        .then(data => {
            console.log("Data received:", data);
            if (data.error) { throw new Error(`Server error: ${data.error} - ${data.detail || ''}`); }

            const tableBody = document.querySelector("#movies-table tbody");
            if (!tableBody) { console.error("Error: Could not find table body #movies-table tbody"); return; }
            tableBody.innerHTML = "";

            if (!data.movies || data.movies.length === 0) {
                console.log("No movies found in the response");
                const row = document.createElement("tr");
                const cell = document.createElement("td");
                cell.colSpan = 7;
                const genreFilter = urlParams.get('genre');
                const titleInitialFilter = urlParams.get('titleInitial');
                const searchTitle = urlParams.get('title') || urlParams.get('ft_query');
                if (genreFilter) cell.textContent = `No movies found for genre "${escapeHTML(genreFilter)}"`;
                else if (titleInitialFilter) cell.textContent = `No movies found starting with "${escapeHTML(titleInitialFilter)}"`;
                else if (searchTitle) cell.textContent = `No movies found matching "${escapeHTML(searchTitle)}"`;
                else cell.textContent = "No movies found matching the criteria";
                row.appendChild(cell);
                tableBody.appendChild(row);
                updateButtonStates(data.currentPage || 1, false);
                if(pageInfoSpan) pageInfoSpan.textContent = `Page ${data.currentPage || 1}`;
            } else {
                data.movies.forEach(movie => {
                    const row = document.createElement("tr");
                    const genreLinks = (movie.genres || "")
                        .split(",").map(g => g.trim()).filter(g => g).slice(0, 3)
                        .map(trimmedGenre => `<a href="movies.html?${buildUpdatedUrl({ genre: trimmedGenre, page: 1 })}">${escapeHTML(trimmedGenre)}</a>`)
                        .join(", ");

                    const starLinks = (movie.stars || "")
                        .split(",").map(s => s.trim()).filter(s => s)
                        .map(starInfo => {
                            const parts = starInfo.split(":");
                            if (parts.length >= 2) {
                                const id = parts[0].trim(); const name = parts.slice(1).join(':').trim();
                                return `<a href="singlestar.html?id=${encodeURIComponent(id)}">${escapeHTML(name)}</a>`;
                            } return escapeHTML(starInfo);
                        }).join(", ");

                    const movieId = movie.id;
                    if (!movieId) { console.warn("Movie object missing 'id':", movie); }

                    row.innerHTML = `
                        <td><a href="singlemovie.html?id=${encodeURIComponent(movieId)}">${escapeHTML(movie.title)}</a></td>
                        <td>${escapeHTML(movie.year)}</td>
                        <td>${escapeHTML(movie.director)}</td>
                        <td>${genreLinks || 'N/A'}</td>
                        <td>${starLinks || 'N/A'}</td>
                        <td>${movie.rating !== null && movie.rating !== undefined && movie.rating !== 'N/A' ? escapeHTML(movie.rating) : 'N/A'}</td>
                        <td>
                            ${movieId ? `<button class="btn btn-primary btn-sm add-to-cart-btn" data-movie-id="${escapeHTML(movieId)}">Add to Cart</button>` : 'N/A'}
                        </td>
                    `;
                    tableBody.appendChild(row);
                });

                attachCartButtonListeners();

                updateButtonStates(data.currentPage, data.hasMoreResults);
                if(pageInfoSpan) pageInfoSpan.textContent = `Page ${data.currentPage}`;
            }

            if (moviesDetailsDiv) { moviesDetailsDiv.style.display = 'none'; }
        })
        .catch(error => {
            if (error.message === "User not logged in.") { return; }
            console.error("Error fetching or processing movies:", error);
            const tableBody = document.querySelector("#movies-table tbody");
            if (!tableBody) return;
            tableBody.innerHTML = "";
            const row = document.createElement("tr");
            const cell = document.createElement("td");
            cell.colSpan = 7;
            cell.textContent = "Error loading movies: " + error.message;
            cell.style.color = 'red';
            row.appendChild(cell);
            tableBody.appendChild(row);
            if (moviesDetailsDiv) { moviesDetailsDiv.style.display = 'none'; }
            if(prevButton) prevButton.disabled = true;
            if(nextButton) nextButton.disabled = true;
            if(pageInfoSpan) pageInfoSpan.textContent = 'Error';
        });
}

function attachCartButtonListeners() {
    const buttons = document.querySelectorAll('.add-to-cart-btn');
    buttons.forEach(button => {
        if (!button.dataset.listenerAttached) {
            button.addEventListener('click', function() {
                const movieId = this.dataset.movieId;
                if (movieId) {
                    addToCart(movieId);
                } else {
                    console.error('Movie ID missing from button data attribute');
                }
            });
            button.dataset.listenerAttached = 'true';
        }
    });
}

function fetchAutocompleteSuggestions(query) {
    console.log("Autocomplete search initiated for:", query);

    if (autocompleteCache[query]) {
        console.log("Using cached results for:", query);
        const suggestions = autocompleteCache[query];
        console.log("Cached suggestion list:", suggestions);
        displayAutocompleteSuggestions(suggestions);
        return;
    }

    console.log("Sending AJAX request to server for:", query);
    jQuery.ajax({
        dataType: "json",
        method: "GET",
        url: `api/movie-suggestion?query=${encodeURIComponent(query)}`,
        success: (data) => {
            console.log("Server response for:", query, data);
            const suggestions = data.suggestions || [];
            autocompleteCache[query] = suggestions;
            console.log("Fetched suggestion list:", suggestions);
            displayAutocompleteSuggestions(suggestions);
        },
        error: (jqXHR, textStatus, errorThrown) => {
            console.error("Autocomplete AJAX error:", textStatus, errorThrown, jqXHR.responseText);
            clearAutocompleteSuggestions();
        }
    });
}

function displayAutocompleteSuggestions(suggestions) {
    clearAutocompleteSuggestions();
    currentSuggestions = suggestions;
    selectedSuggestionIndex = -1;

    if (suggestions.length === 0) {
        autocompleteSuggestionsDiv.style.display = 'none';
        return;
    }

    suggestions.forEach((suggestion, index) => {
        const itemDiv = document.createElement("div");
        itemDiv.classList.add("autocomplete-suggestion-item");
        itemDiv.textContent = suggestion.title;
        itemDiv.dataset.movieId = suggestion.id;

        itemDiv.addEventListener("click", () => {
            console.log("Clicked suggestion:", suggestion.title, "ID:", suggestion.id);
            window.location.href = `singlemovie.html?id=${encodeURIComponent(suggestion.id)}`;
        });
        autocompleteSuggestionsDiv.appendChild(itemDiv);
    });
    autocompleteSuggestionsDiv.style.display = 'block';
}

function clearAutocompleteSuggestions() {
    if (autocompleteSuggestionsDiv) {
        autocompleteSuggestionsDiv.innerHTML = "";
        autocompleteSuggestionsDiv.style.display = 'none';
    }
    currentSuggestions = [];
    selectedSuggestionIndex = -1;
}

function handleAutocompleteKeyDown(event) {
    if (!autocompleteSuggestionsDiv || autocompleteSuggestionsDiv.style.display === 'none' || currentSuggestions.length === 0) {
        return;
    }

    const items = autocompleteSuggestionsDiv.children;

    if (event.key === "ArrowDown") {
        event.preventDefault();
        selectedSuggestionIndex = Math.min(selectedSuggestionIndex + 1, currentSuggestions.length - 1);
        updateSelectedSuggestion(items);
    } else if (event.key === "ArrowUp") {
        event.preventDefault();
        selectedSuggestionIndex = Math.max(selectedSuggestionIndex - 1, 0);
        updateSelectedSuggestion(items);
    } else if (event.key === "Enter") {
        event.preventDefault();
        if (selectedSuggestionIndex > -1 && items[selectedSuggestionIndex]) {
            items[selectedSuggestionIndex].click();
        } else {
            searchForm.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
            clearAutocompleteSuggestions();
        }
    } else if (event.key === "Escape") {
        clearAutocompleteSuggestions();
    }
}

function updateSelectedSuggestion(items) {
    for (let i = 0; i < items.length; i++) {
        items[i].classList.remove("selected");
    }
    if (selectedSuggestionIndex > -1 && items[selectedSuggestionIndex]) {
        items[selectedSuggestionIndex].classList.add("selected");
        if(titleInput) titleInput.value = currentSuggestions[selectedSuggestionIndex].title;
        items[selectedSuggestionIndex].scrollIntoView({ block: 'nearest' });
    }
}


if (titleInput) {
    titleInput.addEventListener("keyup", (event) => {
        if (["ArrowDown", "ArrowUp", "Enter", "Escape"].includes(event.key)) {
            return;
        }
        const query = titleInput.value.trim();
        clearTimeout(debounceTimeout);

        if (query.length >= MIN_CHARS_FOR_AUTOCOMPLETE) {
            debounceTimeout = setTimeout(() => {
                fetchAutocompleteSuggestions(query);
            }, DEBOUNCE_DELAY);
        } else {
            clearAutocompleteSuggestions();
        }
    });

    titleInput.addEventListener("keydown", handleAutocompleteKeyDown);

    titleInput.addEventListener("blur", () => {
        setTimeout(() => {
            if (autocompleteSuggestionsDiv && !autocompleteSuggestionsDiv.matches(':hover')) {
                clearAutocompleteSuggestions();
            }
        }, 150);
    });

    titleInput.addEventListener("focus", () => {
        const query = titleInput.value.trim();
        if (query.length >= MIN_CHARS_FOR_AUTOCOMPLETE && currentSuggestions.length > 0 && autocompleteSuggestionsDiv) {
            autocompleteSuggestionsDiv.style.display = 'block';
        }
    });
}


function fetchInitials() {
    fetch("api/title-initials")
        .then(response => response.json())
        .then(data => {
            const urlParams = getUrlParams();
            const selectedInitial = urlParams.get('titleInitial');
            const initialListDiv = document.querySelector("#initial-list div");
            if (!initialListDiv) return;
            initialListDiv.innerHTML = "";
            const currentPagination = getCurrentPaginationParams(urlParams);
            const currentSort = getCurrentSortParams(urlParams);

            data.initials.forEach(initial => {
                const link = document.createElement("a");
                const browseParams = {
                    titleInitial: initial, page: 1, limit: currentPagination.limit,
                    sort1: currentSort.sort1, order1: currentSort.order1,
                    sort2: currentSort.sort2, order2: currentSort.order2
                };
                link.href = "movies.html?" + buildUpdatedUrl(browseParams);
                link.textContent = initial;
                link.style.marginRight = "10px"; link.style.textDecoration = "none";
                link.style.color = "blue"; link.style.fontWeight = "bold";
                if (initial === selectedInitial) { link.classList.add("selected-browse-link"); link.style.color = "red"; }
                initialListDiv.appendChild(link);
            });
        })
        .catch(error => {
            console.error("Error fetching title initials:", error);
            const initialListDiv = document.querySelector("#initial-list div");
            if (initialListDiv) initialListDiv.textContent = "Failed to load title initials.";
        });
}

function fetchGenres() {
    fetch("api/genres")
        .then(response => response.json())
        .then(data => {
            const urlParams = getUrlParams();
            const selectedGenre = urlParams.get('genre');
            const genreListDiv = document.querySelector("#genre-list div");
            if (!genreListDiv) return;
            genreListDiv.innerHTML = "";
            const currentPagination = getCurrentPaginationParams(urlParams);
            const currentSort = getCurrentSortParams(urlParams);

            data.genres.forEach(genre => {
                const link = document.createElement("a");
                const browseParams = {
                    genre: genre, page: 1, limit: currentPagination.limit,
                    sort1: currentSort.sort1, order1: currentSort.order1,
                    sort2: currentSort.sort2, order2: currentSort.order2
                };
                link.href = "movies.html?" + buildUpdatedUrl(browseParams);
                link.textContent = genre;
                link.style.marginRight = "10px"; link.style.textDecoration = "none";
                link.style.color = "blue"; link.style.fontWeight = "bold";
                if (genre === selectedGenre) { link.classList.add("selected-browse-link"); link.style.color = "red"; }
                genreListDiv.appendChild(link);
            });
        })
        .catch(error => {
            console.error("Error fetching genres:", error);
            const genreListDiv = document.querySelector("#genre-list div");
            if (genreListDiv) genreListDiv.textContent = "Failed to load genres.";
        });
}

if(searchForm) {
    searchForm.addEventListener("submit", function(event) {
        event.preventDefault();

        if (selectedSuggestionIndex !== -1 && currentSuggestions[selectedSuggestionIndex]) {
            const selectedItem = autocompleteSuggestionsDiv.children[selectedSuggestionIndex];
            if(selectedItem) selectedItem.click();
            return;
        }

        const newSearchParams = new URLSearchParams();
        const currentUrlParams = getUrlParams();
        const currentSort = getCurrentSortParams(currentUrlParams);
        const currentPagination = getCurrentPaginationParams(currentUrlParams);

        const titleValueFromInput = titleInput ? titleInput.value.trim() : '';
        if (titleValueFromInput) {
            newSearchParams.set('ft_query', titleValueFromInput);
        }


        ['year', 'director', 'star_name'].forEach(param => {
            const element = document.getElementById(param);
            const value = element ? element.value.trim() : '';
            if (value) {
                newSearchParams.set(param, value);
            }
        });

        newSearchParams.set('sort1', currentSort.sort1);
        newSearchParams.set('order1', currentSort.order1);
        if (currentSort.sort2 && currentSort.sort2 !== 'none') {
            newSearchParams.set('sort2', currentSort.sort2);
            newSearchParams.set('order2', currentSort.order2);
        } else {
            newSearchParams.delete('sort2');
            newSearchParams.delete('order2');
        }


        newSearchParams.set('limit', currentPagination.limit);
        newSearchParams.set('page', '1');

        clearAutocompleteSuggestions();
        window.location.search = newSearchParams.toString();
    });
}

if (resetButton) {
    resetButton.addEventListener("click", function() {
        if (searchForm) searchForm.reset();

        const urlParams = getUrlParams();
        const currentPagination = getCurrentPaginationParams(urlParams);

        const resetUrlParams = new URLSearchParams();
        resetUrlParams.set('limit', currentPagination.limit);
        resetUrlParams.set('page', '1');
        resetUrlParams.set('sort1', 'rating');
        resetUrlParams.set('order1', 'desc');
        resetUrlParams.set('sort2', 'title');
        resetUrlParams.set('order2', 'asc');

        window.location.search = resetUrlParams.toString();
    });
}


if(applySortButton) {
    applySortButton.addEventListener("click", function() {
        const urlParams = getUrlParams();
        const currentPagination = getCurrentPaginationParams(urlParams);
        const s1f = sort1Field.value; const s1o = sort1Order.value;
        const s2f = sort2Field.value; const s2o = sort2Order.value;
        if (s2f !== 'none' && s1f === s2f) { alert("Primary and Secondary sort fields cannot be the same."); return; }

        const newParams = {};
        ['title', 'year', 'director', 'star_name', 'genre', 'titleInitial', 'ft_query'].forEach(param => {
            if (urlParams.has(param) && urlParams.get(param) !== null && urlParams.get(param) !== '') {
                newParams[param] = urlParams.get(param);
            }
        });

        newParams.sort1 = s1f; newParams.order1 = s1o;
        if (s2f !== 'none') {
            newParams.sort2 = s2f; newParams.order2 = s2o;
        }
        else {
            newParams.sort2 = null; newParams.order2 = null;
        }
        newParams.limit = currentPagination.limit; newParams.page = '1';
        window.location.search = buildUpdatedUrl(newParams);
    });
}


if(resultsPerPageSelect) {
    resultsPerPageSelect.addEventListener('change', function() {
        const urlParams = getUrlParams();
        const newParams = {};
        ['title', 'year', 'director', 'star_name', 'genre', 'titleInitial', 'sort1', 'order1', 'sort2', 'order2', 'ft_query'].forEach(param => {
            if (urlParams.has(param) && urlParams.get(param) !== null && urlParams.get(param) !== '') {
                newParams[param] = urlParams.get(param);
            }
        });
        newParams.limit = this.value;
        newParams.page = '1';

        const newUrlParamsString = buildUpdatedUrl(newParams);

        history.pushState({ path: newUrlParamsString }, '', 'movies.html?' + newUrlParamsString);
        fetch_movies();
        updatePaginationControlsUI(new URLSearchParams(newUrlParamsString));


    });
}

if(prevButton) {
    prevButton.addEventListener('click', function() {
        const urlParams = getUrlParams();
        const currentPagination = getCurrentPaginationParams(urlParams);
        if (currentPagination.page > 1) {
            urlParams.set('page', currentPagination.page - 1);
            const newUrlParamsString = urlParams.toString();
            history.pushState({ path: newUrlParamsString }, '', 'movies.html?' + newUrlParamsString);
            fetch_movies();
            updatePaginationControlsUI(urlParams);
        }
    });
}

if(nextButton) {
    nextButton.addEventListener('click', function() {
        const urlParams = getUrlParams();
        const currentPagination = getCurrentPaginationParams(urlParams);
        urlParams.set('page', currentPagination.page + 1);
        const newUrlParamsString = urlParams.toString();
        history.pushState({ path: newUrlParamsString }, '', 'movies.html?' + newUrlParamsString);
        fetch_movies();
        updatePaginationControlsUI(urlParams);
    });
}


if(sort2Field) {
    sort2Field.addEventListener('change', function() {
        if(sort2Order) sort2Order.disabled = (this.value === 'none');
    });
}

document.addEventListener("DOMContentLoaded", function() {
    const urlParams = getUrlParams();
    updateSortControlsUI(urlParams);
    updatePaginationControlsUI(urlParams);
    ['title', 'year', 'director', 'star_name', 'ft_query'].forEach(param => {
        const paramValue = urlParams.get(param);
        if (paramValue) {
            const element = document.getElementById(param === 'ft_query' ? 'title' : param);
            if (element) { element.value = paramValue; }
        }
    });
    if (urlParams.has('ft_query') && titleInput && !urlParams.has('title')) {
        titleInput.value = urlParams.get('ft_query');
    }


    fetchInitials();
    fetchGenres();
    fetch_movies();
});