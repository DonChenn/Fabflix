document.addEventListener('DOMContentLoaded', () => {
    const addStarForm = document.getElementById('add-star-form');
    const addMovieForm = document.getElementById('add-movie-form');
    const showMetadataButton = document.getElementById('show-metadata-button');
    const addStarMessageArea = document.getElementById('add-star-message');
    const addMovieMessageArea = document.getElementById('add-movie-message');
    const metadataMessageArea = document.getElementById('metadata-message');
    const metadataContentArea = document.getElementById('metadata-content');

    function showMessage(areaElement, message, isSuccess = true) {
        areaElement.textContent = message;
        areaElement.className = 'message-area';
        if (isSuccess) {
            areaElement.classList.add('success');
        } else {
            areaElement.classList.add('error');
        }
        setTimeout(() => {
            areaElement.textContent = '';
            areaElement.className = 'message-area';
        }, 5000);
    }

    if (addStarForm) {
        addStarForm.addEventListener('submit', async (event) => {
            event.preventDefault();

            const starNameInput = document.getElementById('star-name');
            const birthYearInput = document.getElementById('birth-year');
            const starName = starNameInput.value.trim();
            const birthYear = birthYearInput.value.trim();

            if (!starName) {
                showMessage(addStarMessageArea, 'Star Name is required.', false);
                return;
            }

            const payload = {
                star_name: starName
            };
            if (birthYear) {
                payload.birth_year = parseInt(birthYear, 10);
                if (isNaN(payload.birth_year)) {
                    showMessage(addStarMessageArea, 'Birth year must be a valid number.', false);
                    return;
                }
            }

            try {
                const response = await fetch('api/dashboard/add-star', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify(payload)
                });

                const result = await response.json();

                if (response.ok && result.success) {
                    showMessage(addStarMessageArea, result.message || 'Star added successfully!', true);
                    addStarForm.reset();
                } else {
                    showMessage(addStarMessageArea, result.message || 'Failed to add star. Please try again.', false);
                }
            } catch (error) {
                console.error('Error adding star:', error);
                showMessage(addStarMessageArea, 'An error occurred while adding the star. Check console for details.', false);
            }
        });
    }

    if (addMovieForm) {
        addMovieForm.addEventListener('submit', async (event) => {
            event.preventDefault();

            const title = document.getElementById('movie-title').value.trim();
            const year = document.getElementById('movie-year').value.trim();
            const director = document.getElementById('movie-director').value.trim();
            const starName = document.getElementById('movie-star-name').value.trim();
            const genreName = document.getElementById('movie-genre-name').value.trim();

            if (!title || !year || !director || !starName || !genreName) {
                showMessage(addMovieMessageArea, 'All fields marked with * are required.', false);
                return;
            }

            const movieData = {
                title: title,
                year: parseInt(year, 10),
                director: director,
                star_name: starName,
                genre_name: genreName
            };

            if (isNaN(movieData.year)) {
                showMessage(addMovieMessageArea, 'Movie year must be a valid number.', false);
                return;
            }

            try {
                const response = await fetch('api/dashboard/add-movie', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify(movieData)
                });

                const result = await response.json();

                if (response.ok && result.success) {
                    showMessage(addMovieMessageArea, result.message || 'Movie added successfully!', true);
                    addMovieForm.reset();
                } else {
                    showMessage(addMovieMessageArea, result.message || 'Failed to add movie. Please try again.', false);
                }
            } catch (error) {
                console.error('Error adding movie:', error);
                showMessage(addMovieMessageArea, 'An error occurred while adding the movie. Check console for details.', false);
            }
        });
    }

    if (showMetadataButton) {
        showMetadataButton.addEventListener('click', async () => {
            metadataContentArea.innerHTML = '<p>Loading metadata...</p>';
            showMessage(metadataMessageArea, '');

            try {
                const response = await fetch('api/dashboard/metadata', {
                    method: 'GET',
                    headers: {}
                });

                const result = await response.json();

                if (response.ok && result.success && result.data) {
                    displayMetadata(result.data);
                    showMessage(metadataMessageArea, 'Metadata loaded successfully.', true);
                } else {
                    metadataContentArea.innerHTML = '<p>Failed to load metadata.</p>';
                    showMessage(metadataMessageArea, result.message || 'Failed to load metadata. Please try again.', false);
                }
            } catch (error) {
                console.error('Error fetching metadata:', error);
                metadataContentArea.innerHTML = '<p>An error occurred while fetching metadata.</p>';
                showMessage(metadataMessageArea, 'An error occurred. Check console for details.', false);
            }
        });
    }

    function displayMetadata(metadata) {
        metadataContentArea.innerHTML = '';

        if (Object.keys(metadata).length === 0) {
            metadataContentArea.innerHTML = '<p>No metadata available or database is empty.</p>';
            return;
        }

        for (const tableName in metadata) {
            if (metadata.hasOwnProperty(tableName)) {
                const tableDiv = document.createElement('div');
                tableDiv.classList.add('metadata-table');

                const tableTitle = document.createElement('h3');
                tableTitle.textContent = tableName;
                tableDiv.appendChild(tableTitle);

                const attributeList = document.createElement('ul');
                const attributes = metadata[tableName];

                if (attributes && attributes.length > 0) {
                    attributes.forEach(attr => {
                        const listItem = document.createElement('li');
                        listItem.textContent = `${attr.attributeName} (${attr.type})`;
                        attributeList.appendChild(listItem);
                    });
                } else {
                    const listItem = document.createElement('li');
                    listItem.textContent = 'No attributes found for this table.';
                    attributeList.appendChild(listItem);
                }
                tableDiv.appendChild(attributeList);
                metadataContentArea.appendChild(tableDiv);
            }
        }
    }
});