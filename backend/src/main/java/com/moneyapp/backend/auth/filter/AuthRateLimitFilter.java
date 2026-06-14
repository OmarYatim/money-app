package com.moneyapp.backend.auth.filter;

import com.moneyapp.backend.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final String CF_CONNECTING_IP = "CF-Connecting-IP";
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";

  private final AppProperties appProperties;
  private final JsonMapper jsonMapper;
  private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public AuthRateLimitFilter(AppProperties appProperties, JsonMapper jsonMapper) {
    this.appProperties = appProperties;
    this.jsonMapper = jsonMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!shouldRateLimit(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = request.getRequestURI() + ":" + clientIp(request);
    Bucket bucket = buckets.computeIfAbsent(key, ignored -> createBucket(request.getRequestURI()));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      filterChain.doFilter(request, response);
      return;
    }

    writeRateLimitResponse(response, retryAfterSeconds(probe));
  }

  private boolean shouldRateLimit(HttpServletRequest request) {
    return appProperties.rateLimit().enabled()
        && HttpMethod.POST.matches(request.getMethod())
        && ("/api/auth/login".equals(request.getRequestURI())
            || "/api/auth/refresh".equals(request.getRequestURI()));
  }

  private Bucket createBucket(String path) {
    AppProperties.EndpointRateLimitProperties properties =
        "/api/auth/refresh".equals(path)
            ? appProperties.rateLimit().refresh()
            : appProperties.rateLimit().login();
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(properties.capacity())
            .refillIntervally(properties.capacity(), Duration.ofSeconds(properties.refillSeconds()))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private String clientIp(HttpServletRequest request) {
    String cloudflareIp = request.getHeader(CF_CONNECTING_IP);
    if (hasText(cloudflareIp)) {
      return cloudflareIp.trim();
    }
    String forwardedFor = request.getHeader(X_FORWARDED_FOR);
    if (hasText(forwardedFor)) {
      return forwardedFor.split(",", 2)[0].trim();
    }
    return request.getRemoteAddr();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private long retryAfterSeconds(ConsumptionProbe probe) {
    return Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
  }

  private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds)
      throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write(
            jsonMapper.writeValueAsString(
                Map.of(
                    "code",
                    "RATE_LIMIT_EXCEEDED",
                    "message",
                    "Too many requests. Please try again later.")));
  }
}
