package com.rootcause.foshol.identity.infrastructure;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Role;
import com.rootcause.foshol.identity.domain.IdentityException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SecurityProblemWriter problems;
    private final SecurityContextRepository securityContextRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            SecurityProblemWriter problems,
            SecurityContextRepository securityContextRepository) {
        this.jwtService = jwtService;
        this.problems = problems;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String token = bearerToken(header);
            DecodedJWT jwt = jwtService.verify(token);
            Role role = parseRole(jwt.getClaim("role").asString());
            var authentication = new UsernamePasswordAuthenticationToken(
                    jwt.getSubject(), jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            filterChain.doFilter(request, response);
        } catch (IdentityException ex) {
            SecurityContextHolder.clearContext();
            securityContextRepository.saveContext(
                    SecurityContextHolder.createEmptyContext(), request, response);
            problems.write(response, ex.status(), ex.errorCode(), ex.getMessage(), request);
        }
    }

    private static String bearerToken(String header) {
        String trimmed = header.trim();
        if (trimmed.length() < 7 || !trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new IdentityException(ErrorCodes.ERR_TOKEN_INVALID, 401, "The token is not valid.");
        }
        String token = trimmed.substring(7).trim();
        if (token.isEmpty()) {
            throw new IdentityException(ErrorCodes.ERR_TOKEN_INVALID, 401, "The token is not valid.");
        }
        return token;
    }

    private static Role parseRole(String claim) {
        if (claim == null || claim.isBlank()) {
            throw new IdentityException(ErrorCodes.ERR_TOKEN_INVALID, 401, "The token is not valid.");
        }
        try {
            return Role.valueOf(claim);
        } catch (IllegalArgumentException ex) {
            throw new IdentityException(ErrorCodes.ERR_TOKEN_INVALID, 401, "The token is not valid.");
        }
    }
}
