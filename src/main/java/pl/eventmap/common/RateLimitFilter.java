package pl.eventmap.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for the credential endpoints only.
 *
 * Deliberately in-memory: correct for the single-instance deployment this
 * project targets. Behind more than one instance it must move to Redis.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String GUARDED_PREFIX = "/api/v1/auth/";
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant minute, AtomicInteger hits) {
    }

    private final int limitPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith(GUARDED_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        Instant minute = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String client = request.getRemoteAddr();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(entry -> !entry.getValue().minute().equals(minute));
        }

        Window window = windows.compute(client, (key, existing) ->
                existing == null || !existing.minute().equals(minute)
                        ? new Window(minute, new AtomicInteger())
                        : existing);

        if (window.hits().incrementAndGet() > limitPerMinute) {
            ProblemResponse.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many authentication attempts, retry in a minute");
            return;
        }

        chain.doFilter(request, response);
    }
}
