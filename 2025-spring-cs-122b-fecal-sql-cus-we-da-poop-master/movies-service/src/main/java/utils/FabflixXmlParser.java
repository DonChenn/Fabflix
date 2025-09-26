package utils;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


// --- POJO Classes ---

class StarPojo {
    String stageName;
    Integer birthYear; // Parsed from <dob>

    public StarPojo(String stageName, Integer birthYear) {
        this.stageName = stageName;
        this.birthYear = birthYear;
    }

    @Override
    public String toString() {
        return "StarPojo{" +
                "stageName='" + stageName + '\'' +
                ", birthYear=" + birthYear +
                '}';
    }

    // Optional: equals and hashCode if storing in Sets directly
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StarPojo starPojo = (StarPojo) o;
        return Objects.equals(stageName, starPojo.stageName) &&
                Objects.equals(birthYear, starPojo.birthYear);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stageName, birthYear);
    }
}

class MoviePojo {
    String xmlFid;
    String title;
    Integer year;
    String directorName;
    List<String> genreNames = new ArrayList<>();

    @Override
    public String toString() {
        return "MoviePojo{" +
                "xmlFid='" + xmlFid + '\'' +
                ", title='" + title + '\'' +
                ", year=" + year +
                ", directorName='" + directorName + '\'' +
                ", genreNames=" + genreNames +
                '}';
    }
    // For checking duplicates based on content before DB insertion if needed
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MoviePojo moviePojo = (MoviePojo) o;
        return Objects.equals(title, moviePojo.title) &&
                Objects.equals(year, moviePojo.year) &&
                Objects.equals(directorName, moviePojo.directorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, year, directorName);
    }
}

class CastingPojo {
    String movieXmlFid;
    String actorStageName;

    public CastingPojo(String movieXmlFid, String actorStageName) {
        this.movieXmlFid = movieXmlFid;
        this.actorStageName = actorStageName;
    }

    @Override
    public String toString() {
        return "CastingPojo{" +
                "movieXmlFid='" + movieXmlFid + '\'' +
                ", actorStageName='" + actorStageName + '\'' +
                '}';
    }
}

// Not strictly a POJO for XML, but for DB interaction later
class MovieKey {
    String title;
    Integer year;
    String directorName;

    public MovieKey(String title, Integer year, String directorName) {
        this.title = title;
        this.year = year;
        this.directorName = directorName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MovieKey movieKey = (MovieKey) o;
        return Objects.equals(title, movieKey.title) &&
                Objects.equals(year, movieKey.year) &&
                Objects.equals(directorName, movieKey.directorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, year, directorName);
    }

    @Override
    public String toString() {
        return "MovieKey{" +
                "title='" + title + '\'' +
                ", year=" + year +
                ", directorName='" + directorName + '\'' +
                '}';
    }
}

// --- SAX Handlers ---

class ActorsSaxHandler extends DefaultHandler {
    private StringBuilder currentElementValue;
    private StarPojo currentStar;
    private Map<String, StarPojo> parsedStars;
    private List<String> inconsistencyLog;

    public ActorsSaxHandler(Map<String, StarPojo> parsedStars, List<String> inconsistencyLog) {
        this.parsedStars = parsedStars;
        this.inconsistencyLog = inconsistencyLog;
        this.currentElementValue = new StringBuilder();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        currentElementValue.setLength(0); // Reset buffer
        if ("actor".equalsIgnoreCase(qName)) {
            currentStar = new StarPojo(null, null);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        currentElementValue.append(ch, start, length);
    }

    private Integer safeParseInteger(String value, String fieldName, String context) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            inconsistencyLog.add("Invalid " + fieldName + " format: '" + value + "' for " + context + ". Storing as NULL.");
            return null;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String value = currentElementValue.toString().trim();
        if (currentStar != null) {
            if ("stagename".equalsIgnoreCase(qName)) {
                currentStar.stageName = value;
            } else if ("dob".equalsIgnoreCase(qName)) {
                currentStar.birthYear = safeParseInteger(value, "birth year (dob)", "actor " + currentStar.stageName);
            } else if ("actor".equalsIgnoreCase(qName)) {
                if (currentStar.stageName != null && !currentStar.stageName.isEmpty()) {
                    if (parsedStars.containsKey(currentStar.stageName)) {
                        inconsistencyLog.add("Duplicate actor stagename in actors63.xml: '" + currentStar.stageName + "'. Overwriting with later entry.");
                    }
                    parsedStars.put(currentStar.stageName, currentStar);
                } else {
                    inconsistencyLog.add("Actor record found without a stagename in actors63.xml. Record skipped.");
                }
                currentStar = null; // Reset for next actor
            }
        }
    }
}

class MainsSaxHandler extends DefaultHandler {
    private StringBuilder currentElementValue;
    private String currentDirectorName; // From <directorfilms><director><dirname>
    private MoviePojo currentMovie;
    // private String currentFid; // Stored directly in currentMovie.xmlFid
    private List<String> currentCategoriesForFilm;

    private Map<String, MoviePojo> parsedMoviesWithFid;
    private List<MoviePojo> parsedMoviesWithoutFid;
    private Set<String> parsedUniqueGenreNames;
    private List<String> inconsistencyLog;

    private boolean inDirectorTag = false; // To identify dirname within director

    public MainsSaxHandler(Map<String, MoviePojo> parsedMoviesWithFid,
                           List<MoviePojo> parsedMoviesWithoutFid,
                           Set<String> parsedUniqueGenreNames,
                           List<String> inconsistencyLog) {
        this.parsedMoviesWithFid = parsedMoviesWithFid;
        this.parsedMoviesWithoutFid = parsedMoviesWithoutFid;
        this.parsedUniqueGenreNames = parsedUniqueGenreNames;
        this.inconsistencyLog = inconsistencyLog;
        this.currentElementValue = new StringBuilder();
    }
    private Integer safeParseYear(String value, String filmTitle) {
        if (value == null || value.trim().isEmpty() || "0".equals(value.trim()) || "undated".equalsIgnoreCase(value.trim())) {
            if (value != null && !value.trim().isEmpty()) { // Log only if there was some non-empty invalid value
                inconsistencyLog.add("Invalid year '" + value + "' for film '" + filmTitle + "'. Storing as NULL.");
            }
            return null;
        }
        // Handle Roman numerals - basic cases, can be expanded
        // This is a simplification. A robust Roman numeral parser is more complex.
        String upperValue = value.trim().toUpperCase();
        if (upperValue.matches("^[MDCLXVI]+$")) {
            inconsistencyLog.add("Roman numeral year '" + value + "' for film '" + filmTitle + "' detected. Treating as NULL (conversion not fully implemented).");
            return null; // Or implement full Roman numeral conversion
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            inconsistencyLog.add("Invalid year format: '" + value + "' for film '" + filmTitle + "'. Storing as NULL.");
            return null;
        }
    }


    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        currentElementValue.setLength(0);
        if ("directorfilms".equalsIgnoreCase(qName)) {
            currentDirectorName = null; // Reset for new director's films
        } else if ("director".equalsIgnoreCase(qName)) {
            inDirectorTag = true;
        } else if ("film".equalsIgnoreCase(qName)) {
            currentMovie = new MoviePojo();
            currentMovie.directorName = currentDirectorName; // Assign current group director
            currentCategoriesForFilm = new ArrayList<>();
        }
        // No specific action for <fid>, <t>, <year>, <dirn>, <cat> on start, data captured in characters()
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        currentElementValue.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String value = currentElementValue.toString().trim();

        if ("dirname".equalsIgnoreCase(qName) && inDirectorTag) {
            currentDirectorName = value;
        } else if ("director".equalsIgnoreCase(qName)) {
            inDirectorTag = false;
        }

        if (currentMovie != null) { // Processing elements within a <film> context
            if ("fid".equalsIgnoreCase(qName)) {
                currentMovie.xmlFid = value;
            } else if ("t".equalsIgnoreCase(qName)) {
                currentMovie.title = value;
            } else if ("year".equalsIgnoreCase(qName)) {
                currentMovie.year = safeParseYear(value, currentMovie.title != null ? currentMovie.title : "Unknown Title");
            } else if ("dirn".equalsIgnoreCase(qName)) { // Director name specific to this film
                if (currentMovie.directorName == null || currentMovie.directorName.isEmpty()) {
                    currentMovie.directorName = value; // Fallback if group director wasn't set
                } else if (!currentMovie.directorName.equals(value)) {
                    inconsistencyLog.add("Director name mismatch for film '" + currentMovie.title +
                            "': Group director '" + currentMovie.directorName +
                            "', film specific director '" + value + "'. Using group director.");
                    // Policy: Group director from <directorfilms><director><dirname> takes precedence.
                }
            } else if ("cat".equalsIgnoreCase(qName)) {
                if (!value.isEmpty() && !value.equalsIgnoreCase("NULL")) {
                    // Normalize common genre codes/names here if necessary
                    // e.g. "Susp" -> "Suspense", "Comd" -> "Comedy"
                    // For now, just add trimmed value.
                    String genre = normalizeGenre(value);
                    if (genre != null) {
                        currentCategoriesForFilm.add(genre);
                        parsedUniqueGenreNames.add(genre);
                    } else {
                        inconsistencyLog.add("Skipped potentially invalid genre code: '" + value + "' for film '" + currentMovie.title + "'");
                    }
                }
            } else if ("film".equalsIgnoreCase(qName)) {
                currentMovie.genreNames.addAll(currentCategoriesForFilm);

                if (currentMovie.title == null || currentMovie.title.isEmpty()) {
                    inconsistencyLog.add("Film record found without a title. XML FID: " + currentMovie.xmlFid + ". Record skipped.");
                } else {
                    if (currentMovie.directorName == null || currentMovie.directorName.isEmpty()){
                        inconsistencyLog.add("Film '" + currentMovie.title + "' (FID: " + currentMovie.xmlFid + ") has no director information. Storing director as NULL.");
                    }

                    if (currentMovie.xmlFid != null && !currentMovie.xmlFid.isEmpty()) {
                        if (parsedMoviesWithFid.containsKey(currentMovie.xmlFid)) {
                            inconsistencyLog.add("Duplicate film FID in mains243.xml: '" + currentMovie.xmlFid +
                                    "' for title '" + currentMovie.title + "'. Overwriting with later entry.");
                        }
                        parsedMoviesWithFid.put(currentMovie.xmlFid, currentMovie);
                    } else {
                        inconsistencyLog.add("Film '" + currentMovie.title + "' is missing FID. Cannot be linked for casting. Added to separate list.");
                        parsedMoviesWithoutFid.add(currentMovie);
                    }
                }
                currentMovie = null; // Reset for next film
            }
        }
    }

    // Simple genre normalization, can be expanded
    private String normalizeGenre(String rawGenre) {
        if (rawGenre == null) return null;
        String g = rawGenre.trim().toLowerCase();

        // Define known mappings
        Map<String, String> genreMap = new HashMap<>();
        genreMap.put("susp", "Suspense");
        genreMap.put("cmr", "Crime");
        genreMap.put("cnr", "Cops and Robbers");
        genreMap.put("dram", "Drama");
        genreMap.put("west", "Western");
        genreMap.put("myst", "Mystery");
        genreMap.put("s.f.", "Sci-Fi");
        genreMap.put("scfi", "Sci-Fi");
        genreMap.put("advt", "Adventure");
        genreMap.put("horr", "Horror");
        genreMap.put("comd", "Comedy");
        genreMap.put("musc", "Musical");
        genreMap.put("docu", "Documentary");
        genreMap.put("porn", "Adult");
        genreMap.put("biop", "Biographical Picture");
        genreMap.put("tv", "TV Show");
        genreMap.put("tvs", "TV Series");
        genreMap.put("tvm", "TV Movie");
        genreMap.put("actn", "Action");
        genreMap.put("fant", "Fantasy");
        genreMap.put("romt", "Romance");
        genreMap.put("cart", "Animation");
        genreMap.put("hist", "Historical");
        genreMap.put("biog", "Biography");
        genreMap.put("epic", "Epic");
        genreMap.put("noir", "Film Noir");
        genreMap.put("fam", "Family");

        // Known full genre names
        Set<String> knownFullGenres = Set.of(
                "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime", "Documentary",
                "Drama", "Family", "Fantasy", "Film Noir", "Historical", "Horror", "Musical",
                "Mystery", "Romance", "Sci-Fi", "Suspense", "TV Show", "Western", "Adult", "Epic"
        );

        if (genreMap.containsKey(g)) {
            return genreMap.get(g);
        }

        String cleaned = capitalize(rawGenre.trim());

        if (knownFullGenres.contains(cleaned)) {
            return cleaned;
        }


        // Reject suspicious/unknown genres
        return null;
    }


    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // A more robust capitalization might be needed if genres are multi-word and not consistently cased.
        // For single word genres, this is often enough.
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}

class CastsSaxHandler extends DefaultHandler {
    private StringBuilder currentElementValue;
    private String currentMovieFidInCast;
    private String currentActorNameInCast;

    private List<CastingPojo> parsedCastings;
    private List<String> inconsistencyLog;
    // private String currentDirectorInCast; // Not explicitly used for DB schema but present in XML structure

    public CastsSaxHandler(List<CastingPojo> parsedCastings, List<String> inconsistencyLog) {
        this.parsedCastings = parsedCastings;
        this.inconsistencyLog = inconsistencyLog;
        this.currentElementValue = new StringBuilder();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        currentElementValue.setLength(0);
        if ("m".equalsIgnoreCase(qName)) { // A single movie-actor role
            currentMovieFidInCast = null;
            currentActorNameInCast = null;
        }
        // if ("dirfilms".equalsIgnoreCase(qName)) { // DTD suggests <casts><filmc><dirid>...</dirid><dirname>...</dirname><m>...</m></filmc></casts>
        //    // currentDirectorInCast = null; // if needed
        // }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        currentElementValue.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String value = currentElementValue.toString().trim();

        // if ("dirname".equalsIgnoreCase(qName)) { // If capturing director from casts.xml
        //    currentDirectorInCast = value;
        // }

        if ("f".equalsIgnoreCase(qName)) { // Film ID ref inside <m>
            currentMovieFidInCast = value;
        } else if ("a".equalsIgnoreCase(qName)) { // Actor stage name inside <m>
            currentActorNameInCast = value;
        } else if ("m".equalsIgnoreCase(qName)) {
            if (currentMovieFidInCast != null && !currentMovieFidInCast.isEmpty() &&
                    currentActorNameInCast != null && !currentActorNameInCast.isEmpty()) {
                parsedCastings.add(new CastingPojo(currentMovieFidInCast, currentActorNameInCast));
            } else {
                String missing = "";
                if (currentMovieFidInCast == null || currentMovieFidInCast.isEmpty()) missing += "film FID";
                if (currentActorNameInCast == null || currentActorNameInCast.isEmpty()) {
                    if (!missing.isEmpty()) missing += " and ";
                    missing += "actor name";
                }
                inconsistencyLog.add("Casting entry in casts124.xml missing " + missing + ". Entry skipped. FID: "+currentMovieFidInCast+", Actor: "+currentActorNameInCast);
            }
            // Reset for next <m> element
            currentMovieFidInCast = null;
            currentActorNameInCast = null;
        }
    }
}

// --- Main Parser Orchestration Class ---
public class FabflixXmlParser {

    private BufferedWriter logWriter;

    private void initLogWriter() throws IOException {
        logWriter = new BufferedWriter(new FileWriter("parser_log.txt"));
    }

    private void logInfo(String message) {
        try {
            logWriter.write("[INFO] " + message + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logError(String message) {
        try {
            logWriter.write("[ERROR] " + message + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeLogWriter() {
        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // In-memory collections for parsed data
    private Map<String, StarPojo> parsedStars = new HashMap<>();
    private Map<String, MoviePojo> parsedMoviesWithFid = new HashMap<>();
    private List<MoviePojo> parsedMoviesWithoutFid = new ArrayList<>();
    private Set<String> parsedUniqueGenreNames = new HashSet<>();
    private List<CastingPojo> parsedCastings = new ArrayList<>();

    // Log for inconsistencies
    private List<String> inconsistencyLog = new ArrayList<>();

    public void parseAllFiles(String actorsFilePath, String mainsFilePath, String castsFilePath) {
        logInfo("Starting XML parsing...");

        parseXml(actorsFilePath, new ActorsSaxHandler(parsedStars, inconsistencyLog));
        logInfo("Finished parsing actors. Found: " + parsedStars.size() + " unique stars.");

        parseXml(mainsFilePath, new MainsSaxHandler(parsedMoviesWithFid, parsedMoviesWithoutFid, parsedUniqueGenreNames, inconsistencyLog));
        logInfo("Finished parsing mains. Found: " + parsedMoviesWithFid.size() + " movies with FID, " + parsedMoviesWithoutFid.size() + " movies without FID. " + parsedUniqueGenreNames.size() + " unique genre names.");

        parseXml(castsFilePath, new CastsSaxHandler(parsedCastings, inconsistencyLog));
        logInfo("Finished parsing casts. Found: " + parsedCastings.size() + " casting entries.");

        logInfo("\n--- Parsing Summary ---");
        logInfo("Total unique stars parsed (from actors63): " + parsedStars.size());
        logInfo("Total movies with FID parsed (from mains243): " + parsedMoviesWithFid.size());
        logInfo("Total movies without FID parsed (from mains243): " + parsedMoviesWithoutFid.size());
        logInfo("Total unique genre names parsed (from mains243): " + parsedUniqueGenreNames.size());
        logInfo("Total casting relationships parsed (from casts124): " + parsedCastings.size());

        if (!inconsistencyLog.isEmpty()) {
            logInfo("\n--- Encountered Inconsistencies (" + inconsistencyLog.size() + ") ---");
            int limit = Math.min(2000, inconsistencyLog.size());
            for (int i = 0; i < limit; i++) {
                logInfo("LOG: " + inconsistencyLog.get(i));
            }
            if (inconsistencyLog.size() > limit) {
                logInfo("... and " + (inconsistencyLog.size() - limit) + " more inconsistencies.");
            }
        } else {
            logInfo("\nNo inconsistencies reported during parsing.");
        }
        logInfo("XML parsing phase complete.");
    }

    private void parseXml(String filePath, DefaultHandler handler) {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser saxParser = factory.newSAXParser();
            try (FileInputStream fis = new FileInputStream(filePath);
                 Reader reader = new InputStreamReader(fis, "ISO-8859-1")) {

                InputSource is = new InputSource(reader);
                is.setEncoding("ISO-8859-1");
                saxParser.parse(is, handler);
            }

        } catch (ParserConfigurationException | SAXException | IOException e) {
            String errorMessage = "CRITICAL PARSING ERROR for file " + filePath + ": " + e.getMessage();
            logError(errorMessage);
            inconsistencyLog.add(errorMessage);
        }
    }

    public Map<String, StarPojo> getParsedStars() { return parsedStars; }
    public Map<String, MoviePojo> getParsedMoviesWithFid() { return parsedMoviesWithFid; }
    public List<MoviePojo> getParsedMoviesWithoutFid() { return parsedMoviesWithoutFid; }
    public Set<String> getParsedUniqueGenreNames() { return parsedUniqueGenreNames; }
    public List<CastingPojo> getParsedCastings() { return parsedCastings; }
    public List<String> getInconsistencyLog() { return inconsistencyLog; }

    // --- Database Insertion Methods ---
    private Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // <-- ADD THIS LINE
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found.", e);
        }
        String url = "jdbc:mysql://localhost:3306/moviedb";
        String user = "mytestuser";
        String password = "My6$Password";
        return DriverManager.getConnection(url, user, password);
    }

    private Map<String, Integer> loadGenres(Connection conn) throws SQLException {
        Map<String, Integer> genreNameToId = new HashMap<>();
        String sql = "SELECT id, name FROM genres";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                genreNameToId.put(rs.getString("name"), rs.getInt("id"));
            }
        }
        return genreNameToId;
    }

    public void insertParsedDataIntoDatabase() {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Map<String, String> starNameToId = generateStarIds();

            insertStars(conn, starNameToId);
            insertMovies(conn);

            Map<String, Integer> genreNameToId = loadGenres(conn); // ← load existing genres
            insertGenres(conn, genreNameToId); // ← update genreNameToId after new inserts
            insertGenresInMovies(conn, genreNameToId); // ← use this updated map

            insertStarsInMovies(conn, starNameToId);

            conn.commit();
            logInfo("\nDatabase insertion completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            logError("Error during database insertion.");
        }
    }


    private Map<String, String> generateStarIds() {
        Map<String, String> starNameToId = new HashMap<>();
        int counter = 1;
        for (String starName : parsedStars.keySet()) {
            String id = String.format("nm%05d", counter);
            starNameToId.put(starName, id);
            counter++;
        }
        return starNameToId;
    }

    private void insertStars(Connection conn, Map<String, String> starNameToId) throws SQLException {
        String sql = "INSERT IGNORE INTO stars (id, name, birthYear) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int batchCount = 0;
            for (Map.Entry<String, StarPojo> entry : parsedStars.entrySet()) {
                String name = entry.getKey();
                StarPojo star = entry.getValue();
                String id = starNameToId.get(name);

                ps.setString(1, id);
                ps.setString(2, name);
                if (star.birthYear != null) {
                    ps.setInt(3, star.birthYear);
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                }
                ps.addBatch();

                if (++batchCount % 500 == 0) {
                    ps.executeBatch();
                    logInfo("Inserted " + batchCount + " stars...");
                }
            }
            ps.executeBatch();
            logInfo("Finished inserting all stars.");
        }
    }

    private void insertMovies(Connection conn) throws SQLException {
        String sql = "INSERT IGNORE INTO movies (id, title, year, director) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int batchCount = 0;
            for (MoviePojo movie : parsedMoviesWithFid.values()) {
                ps.setString(1, movie.xmlFid);
                ps.setString(2, movie.title);
                ps.setInt(3, movie.year != null ? movie.year : 0);
                ps.setString(4, movie.directorName);
                ps.addBatch();

                if (++batchCount % 500 == 0) {
                    ps.executeBatch();
                    logInfo("Inserted " + batchCount + " movies...");
                }
            }
            ps.executeBatch();
            logInfo("Finished inserting all movies.");
        }
    }

    private void insertGenres(Connection conn, Map<String, Integer> genreNameToId) throws SQLException {
        String insertGenreSql = "INSERT INTO genres (name) VALUES (?)";

        try (PreparedStatement ps = conn.prepareStatement(insertGenreSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            int batchCount = 0;
            for (String genre : parsedUniqueGenreNames) {
                if (!genreNameToId.containsKey(genre)) { // ← Only insert if not already existing
                    ps.setString(1, genre);
                    ps.executeUpdate();

                    try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int newId = generatedKeys.getInt(1);
                            genreNameToId.put(genre, newId); // ← update map for later use
                        }
                    }

                    if (++batchCount % 500 == 0) {
                        logInfo("Inserted " + batchCount + " new genres...");
                    }
                }
            }
            logInfo("Finished inserting new genres.");
        }
    }

    private void insertGenresInMovies(Connection conn, Map<String, Integer> genreNameToId) throws SQLException {
        String insertSql = "INSERT IGNORE INTO genres_in_movies (genreId, movieId) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            int batchCount = 0;
            for (MoviePojo movie : parsedMoviesWithFid.values()) {
                for (String genre : movie.genreNames) {
                    Integer genreId = genreNameToId.get(genre);
                    if (genreId != null) {
                        ps.setInt(1, genreId);
                        ps.setString(2, movie.xmlFid);
                        ps.addBatch();

                        if (++batchCount % 500 == 0) {
                            ps.executeBatch();
                            logInfo("Inserted " + batchCount + " genre-movie links...");
                        }
                    }
                }
            }
            ps.executeBatch();
            logInfo("Finished inserting all genre-movie links.");
        }
    }

    private void insertStarsInMovies(Connection conn, Map<String, String> starNameToId) throws SQLException {
        String sql = "INSERT IGNORE INTO stars_in_movies (starId, movieId) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int batchCount = 0;
            for (CastingPojo casting : parsedCastings) {
                String starId = starNameToId.get(casting.actorStageName);
                if (starId != null) {
                    ps.setString(1, starId);
                    ps.setString(2, casting.movieXmlFid);
                    ps.addBatch();

                    if (++batchCount % 500 == 0) {
                        ps.executeBatch();
                        logInfo("Inserted " + batchCount + " star-movie links...");
                    }
                }
            }
            ps.executeBatch();
            logInfo("Finished inserting all star-movie links.");
        }
    }

    public static void main(String[] args) {
        String basePath = "/Users/donovanchen/IdeaProjects/2025-spring-cs-122b-fecal-sql-cus-we-da-poop/stanford-movies/";
        String actorsFilePath = basePath + "actors63.xml";
        String mainsFilePath = basePath + "mains243.xml";
        String castsFilePath = basePath + "casts124.xml";

        FabflixXmlParser xmlParser = new FabflixXmlParser();
        try {
            xmlParser.initLogWriter();

            xmlParser.parseAllFiles(actorsFilePath, mainsFilePath, castsFilePath);

            xmlParser.logInfo("\nParsing complete. Now inserting into database...");
            xmlParser.insertParsedDataIntoDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            xmlParser.closeLogWriter();
        }
    }
}
