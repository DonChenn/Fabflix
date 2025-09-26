package models;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets; // Import StandardCharsets
import java.util.Date;
import java.util.Map;

public class JwtUtil {

    // IMPORTANT: Use a single, strong secret key.
    // Consider loading this from an environment variable for better security.
    private static final String SECRET_KEY = "mySecretKeyForFabflixThatIsSuperSecureAndLongEnoughForHS256";

    // CORRECTED: Generate the key directly from the secret string's bytes.
    private static final SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    /**
     * Generate a JWT token with custom claims.
     * This is the primary method for creating tokens in the login-service.
     */
    public static String generateToken(String subject, Map<String, Object> claims) {
        return Jwts.builder()
                .setSubject(subject)
                .addClaims(claims) // Add custom claims
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validate a JWT token and return its claims.
     * If the token is not valid, it returns null.
     */
    public static Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            // Token is invalid for any reason (expired, malformed, etc.)
            return null;
        }
    }

    /**
     * Sets or updates the JWT token in an HTTP-only cookie.
     */
    public static void updateJwtCookie(HttpServletResponse response, String newJwtToken) {
        Cookie newCookie = new Cookie("jwtToken", newJwtToken);
        newCookie.setHttpOnly(true);
        newCookie.setPath("/");
        newCookie.setMaxAge(24 * 60 * 60); // 1 day
        // For production, uncomment the line below to send the cookie only over HTTPS
        // newCookie.setSecure(true);
        response.addCookie(newCookie);
    }

    /**
     * Retrieves a cookie value by its name from the request.
     */
    public static String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie.getValue();
                }
            }
        }
        return null; // Cookie not found
    }
}