package com.rootcause.foshol.identity.infrastructure.adapter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.rootcause.foshol.common.contract.ConfigKeys;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import com.rootcause.foshol.identity.application.port.IssuedToken;
import com.rootcause.foshol.identity.application.port.TokenIssuer;
import com.rootcause.foshol.identity.domain.IdentityException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService implements TokenIssuer {

    private final String issuer;
    private final Duration ttl;
    private final String secret;

    public JwtService(
            @Value("${" + ConfigKeys.AUTH_JWT_ISSUER + "}") String issuer,
            @Value("${" + ConfigKeys.AUTH_JWT_TTL + "}") Duration ttl,
            @Value("${" + ConfigKeys.AUTH_JWT_SECRET + "}") String secret) {
        this.issuer = issuer;
        this.ttl = ttl;
        this.secret = secret;
    }

    @PostConstruct
    public void validateSecret() {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(ErrorCodes.ERR_JWT_SECRET_TOO_SHORT);
        }
    }

    @Override
    public IssuedToken issue(UUID subjectId, Role role, Instant now) {
        Instant exp = now.plus(ttl);
        String compact = JWT.create()
                .withIssuer(issuer)
                .withSubject(subjectId.toString())
                .withClaim("role", role.name())
                .withIssuedAt(now)
                .withExpiresAt(exp)
                .sign(Algorithm.HMAC256(secret));
        return new IssuedToken(compact, exp);
    }

    public DecodedJWT verify(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(secret)).withIssuer(issuer).build().verify(token);
        } catch (TokenExpiredException ex) {
            throw new IdentityException(ErrorCodes.ERR_TOKEN_EXPIRED, 401, "The token has expired.");
        } catch (JWTVerificationException ex) {
            throw new IdentityException(ErrorCodes.ERR_TOKEN_INVALID, 401, "The token is not valid.");
        }
    }
}
