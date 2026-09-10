package com.rootcause.foshol.identity.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rootcause.foshol.common.contract.ErrorCodes;
import com.rootcause.foshol.common.enums.Role;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

class JwtAuthenticationFilterTest {

    private final JwtService jwt =
            new JwtService("foshol-doctor", Duration.ofHours(8), "local-dev-jwt-secret-must-be-32chars");
    private final RequestAttributeSecurityContextRepository repository = new RequestAttributeSecurityContextRepository();
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwt, new SecurityProblemWriter(new ObjectMapper()), repository);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthorizationHeaderContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        Instant now = Instant.now().minus(Duration.ofHours(9));
        JwtService shortLived =
                new JwtService("foshol-doctor", Duration.ofSeconds(1), "local-dev-jwt-secret-must-be-32chars");
        UUID sub = UUID.fromString("01800000-0000-7000-8000-000000000301");
        String token = shortLived.issue(sub, Role.FARMER, now).compact();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(ErrorCodes.ERR_TOKEN_EXPIRED);
        assertThat(loadedAuthentication(request)).isNull();
        verifyNoInteractions(chain);
    }

    @Test
    void setsRoleAuthorityFromValidToken() throws Exception {
        jwt.validateSecret();
        UUID sub = UUID.fromString("01800000-0000-7000-8000-000000000301");
        String token = jwt.issue(sub, Role.ADMIN, Instant.now()).compact();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
        SecurityContext saved = repository.loadDeferredContext(request).get();
        assertThat(saved.getAuthentication().getName()).isEqualTo(sub.toString());
        assertThat(saved.getAuthentication().getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
        verify(chain).doFilter(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnknownRoleClaim() throws Exception {
        jwt.validateSecret();
        Instant now = Instant.now();
        String token = JWT.create()
                .withIssuer("foshol-doctor")
                .withSubject(UUID.randomUUID().toString())
                .withClaim("role", "SUPERUSER")
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(60))
                .sign(Algorithm.HMAC256("local-dev-jwt-secret-must-be-32chars"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/stats");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(ErrorCodes.ERR_TOKEN_INVALID);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(loadedAuthentication(request)).isNull();
        verifyNoInteractions(chain);
    }

    private Authentication loadedAuthentication(MockHttpServletRequest request) {
        return repository.loadDeferredContext(request).get().getAuthentication();
    }
}
