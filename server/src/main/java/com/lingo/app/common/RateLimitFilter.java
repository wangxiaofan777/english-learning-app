package com.lingo.app.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 轻量级固定窗口限流（无外部依赖）：按客户端 IP 对敏感端点做基础防护，
 * 防止 auth 爆破、admin 爆破与 LLM 额度被刷。窗口 1 分钟，计数存在本机内存——
 * 当前单实例部署够用；多实例部署时需换成 Redis 等集中式实现。
 */
@Slf4j
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MILLIS = 60_000L;
  private static final int MAX_BUCKETS = 50_000;

  /** 限流规则：路径前缀 → 每分钟允许的请求数 */
  private static final String[][] RULES = {
      {"/api/v1/auth/", "10"},
      {"/api/v1/admin/", "30"},
      {"/api/v1/conversations", "60"},
      {"/api/v1/companion", "60"},
  };
  private static final int DEFAULT_LIMIT = 240;

  private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();

  private record Window(long start, int count) {
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
    String uri = request.getRequestURI();
    if (!uri.startsWith("/api/")) {
      chain.doFilter(request, response);
      return;
    }
    int limit = DEFAULT_LIMIT;
    for (String[] rule : RULES) {
      if (uri.startsWith(rule[0])) {
        limit = Integer.parseInt(rule[1]);
        break;
      }
    }
    if (!tryAcquire(clientKey(request, uri), limit)) {
      log.warn("rate limited: {} {}", request.getMethod(), uri);
      response.setStatus(429);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"code\":429,\"message\":\"请求太频繁，请稍后再试\",\"data\":null}");
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean tryAcquire(String key, int limit) {
    long now = System.currentTimeMillis();
    if (buckets.size() > MAX_BUCKETS) {
      // 粗暴防内存膨胀：超阈值时清掉已过期的窗口
      buckets.entrySet().removeIf(e -> now - e.getValue().start() > WINDOW_MILLIS);
    }
    Window next = buckets.compute(key, (k, w) ->
        w == null || now - w.start() >= WINDOW_MILLIS
            ? new Window(now, 1)
            : new Window(w.start(), w.count() + 1));
    return next.count() <= limit;
  }

  private String clientKey(HttpServletRequest request, String uri) {
    return clientIp(request) + ":" + ruleBucket(uri);
  }

  /** 经 nginx 反代时取真实 IP；直连时取 remoteAddr */
  private String clientIp(HttpServletRequest request) {
    String real = request.getHeader("X-Real-IP");
    if (real != null && !real.isBlank()) {
      return real;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  /** 同一规则的端点共享一个计数桶，避免路径参数（如会话 id）撑爆 key 空间 */
  private String ruleBucket(String uri) {
    for (String[] rule : RULES) {
      if (uri.startsWith(rule[0])) {
        return rule[0];
      }
    }
    return "/api/";
  }
}
