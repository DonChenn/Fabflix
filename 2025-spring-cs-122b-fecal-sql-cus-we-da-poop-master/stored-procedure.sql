DELIMITER //

DROP PROCEDURE IF EXISTS add_movie; //

CREATE PROCEDURE add_movie(
    IN p_title VARCHAR(100),
    IN p_year INT,
    IN p_director VARCHAR(100),
    IN p_star_name VARCHAR(100),
    IN p_genre_name VARCHAR(50),
    OUT p_new_movie_id VARCHAR(10),
    OUT p_message VARCHAR(512)
)

BEGIN
    DECLARE v_star_id VARCHAR(10);
    DECLARE v_genre_id INT;
    DECLARE v_existing_movie_id VARCHAR(10);
    DECLARE v_next_movie_id VARCHAR(10);
    DECLARE v_max_numeric_id INT;
    DECLARE v_star_status VARCHAR(10) DEFAULT 'existing';
    DECLARE v_genre_status VARCHAR(10) DEFAULT 'existing';

    SET p_new_movie_id = NULL;
    SET p_message = 'Procedure started.';

    START TRANSACTION;

    SELECT id INTO v_existing_movie_id
    FROM movies
    WHERE title = p_title AND year = p_year AND director = p_director
    LIMIT 1;

    IF v_existing_movie_id IS NOT NULL THEN
        SET p_message = CONCAT('Error: Movie ''', p_title, ''' (', p_year, ') by ', p_director, ' already exists with ID ', v_existing_movie_id, '.');
        ROLLBACK;
    ELSE
        SELECT id INTO v_star_id
        FROM stars
        WHERE name = p_star_name
        LIMIT 1;

        IF v_star_id IS NULL THEN
            SET v_star_status = 'new';
            SELECT MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)) INTO v_max_numeric_id FROM stars WHERE id LIKE 'nm%';
            IF v_max_numeric_id IS NULL THEN
                SET v_max_numeric_id = 0;
            END IF;
            SET v_star_id = CONCAT('nm', LPAD(v_max_numeric_id + 1, 7, '0'));

            INSERT INTO stars (id, name, birthYear) VALUES (v_star_id, p_star_name, NULL);

            IF ROW_COUNT() = 0 THEN
                SET p_message = CONCAT('Error: Failed to create new star ''', p_star_name, '''.');
                ROLLBACK;
            END IF;
        END IF;

        IF p_message = 'Procedure started.' THEN
            SELECT id INTO v_genre_id
            FROM genres
            WHERE name = p_genre_name
            LIMIT 1;

            IF v_genre_id IS NULL THEN
                SET v_genre_status = 'new';
                INSERT INTO genres (name) VALUES (p_genre_name);
                SET v_genre_id = LAST_INSERT_ID();

                IF v_genre_id IS NULL OR v_genre_id = 0 OR ROW_COUNT() = 0 THEN
                    SET p_message = CONCAT('Error: Failed to create or find genre ''', p_genre_name, '''.');
                    ROLLBACK;
                END IF;
            END IF;
        END IF;

        IF p_message = 'Procedure started.' THEN
            SELECT MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)) INTO v_max_numeric_id FROM movies WHERE id LIKE 'tt%';
            IF v_max_numeric_id IS NULL THEN
                SET v_max_numeric_id = 0;
            END IF;
            SET v_next_movie_id = CONCAT('tt', LPAD(v_max_numeric_id + 1, 7, '0'));

            INSERT INTO movies (id, title, year, director)
            VALUES (v_next_movie_id, p_title, p_year, p_director);

            IF ROW_COUNT() > 0 THEN
                SET p_new_movie_id = v_next_movie_id;

                INSERT INTO stars_in_movies (starId, movieId)
                VALUES (v_star_id, p_new_movie_id);

                IF ROW_COUNT() = 0 THEN
                    SET p_new_movie_id = NULL;
                    SET p_message = CONCAT('Error: Failed to link star ''', p_star_name, ''' (ID: ', v_star_id, ') to movie ''', p_title, '''.');
                    ROLLBACK;
                ELSE
                    INSERT INTO genres_in_movies (genreId, movieId)
                    VALUES (v_genre_id, p_new_movie_id);

                    IF ROW_COUNT() = 0 THEN
                        SET p_new_movie_id = NULL;
                        SET p_message = CONCAT('Error: Failed to link genre ''', p_genre_name, ''' (ID: ', v_genre_id, ') to movie ''', p_title, '''.');
                        ROLLBACK;
                    ELSE
                        SET p_message = CONCAT(
                            'Success: Movie ''', p_title, ''' added. ',
                            'Movie ID: ', p_new_movie_id, '. ',
                            'Star: ''', p_star_name, ''' (', v_star_status, ' ID: ', v_star_id, '). ',
                            'Genre: ''', p_genre_name, ''' (', v_genre_status, ' ID: ', v_genre_id, ').'
                        );
                        COMMIT;
                    END IF;
                END IF;
            ELSE
                SET p_message = CONCAT('Error: Failed to insert movie ''', p_title, ''' (no rows affected).');
                ROLLBACK;
            END IF;
        END IF;
    END IF;
END //

DELIMITER ;