package pl.eventmap.auth.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.eventmap.auth.app.AuthenticatedUser;
import pl.eventmap.auth.app.JwtService;

import java.io.IOException;
import java.util.List;

/**
 * Not a bean on purpose: a Filter bean would also be auto-registered in the
 * servlet chain and run twice. It is wired into the security chain by SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                JwtService.TokenSubject subject = jwtService.parseAccessToken(header.substring(BEARER_PREFIX.length()));
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(subject.userId(), subject.email(), subject.role()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + subject.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
