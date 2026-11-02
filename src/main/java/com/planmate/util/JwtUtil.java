package com.planmate.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.PostConstruct;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Component
public class JwtUtil {
	
	private static final long ACCESS_TOKEN_VALIDITY = 1000L * 60 * 15; // 15 minutes
	
	private static final long REFRESH_TOKEN_VALIDITY = 1000L * 60 * 60 * 24 * 7; // 7 days

	private static final String TYPE_CLAIM = "type";
	private static final String TYPE_ACCESS = "access";
	private static final String TYPE_REFRESH = "refresh";

	private static final int MIN_SECRET_LENGTH = 32;

	@Value("${jwt.secret}")
	private String secret;

	/**
	 * Refuse to start without a signing secret (there is intentionally no default). A short secret only
	 * logs a warning so that existing deployments keep running until the secret is rotated.
	 */
	@PostConstruct
	void verifySecret() {
		if (secret == null || secret.trim().isEmpty()) {
			throw new IllegalStateException("JWT_SECRET is not set. Refusing to start without a JWT signing secret.");
		}
		if (secret.trim().length() < MIN_SECRET_LENGTH) {
			LoggerFactory.getLogger(JwtUtil.class).warn(
					"JWT_SECRET is shorter than {} characters. Rotate it to a longer random value (e.g. openssl rand -base64 48).",
					MIN_SECRET_LENGTH);
		}
	}

	public String extractUsername(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
	}

	private Boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	public Boolean validateToken(String token, String username) {
		final String extractedUsername = extractUsername(token);
		return (extractedUsername.equals(username) && !isTokenExpired(token));
	}

	/**
	 * Access tokens may be used to call the API. Tokens issued before the "type" claim existed
	 * have no type and are treated as access tokens (they expire within 15 minutes anyway).
	 */
	public boolean isAccessToken(String token) {
		return !TYPE_REFRESH.equals(extractClaim(token, c -> c.get(TYPE_CLAIM, String.class)));
	}

	/** Refresh tokens may only be exchanged for new access tokens, never used to call the API. */
	public boolean isRefreshToken(String token) {
		return TYPE_REFRESH.equals(extractClaim(token, c -> c.get(TYPE_CLAIM, String.class)));
	}

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TYPE_CLAIM, TYPE_ACCESS);
        return createToken(claims, userDetails.getUsername(), ACCESS_TOKEN_VALIDITY);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TYPE_CLAIM, TYPE_REFRESH);
        return createToken(claims, userDetails.getUsername(), REFRESH_TOKEN_VALIDITY);
    }

    private String createToken(Map<String, Object> claims, String subject, long validity) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + validity))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }
}
