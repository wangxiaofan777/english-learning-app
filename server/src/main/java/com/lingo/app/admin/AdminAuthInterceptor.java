package com.lingo.app.admin;

import com.lingo.app.common.ApiException;
import com.lingo.app.common.LingoProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端统一鉴权：/api/v1/admin/** 下除登录端点外全部经过这里。
 * 两条通道：
 * 1. admin_session 会话 Cookie —— 独立管理后台站点登录后获得（HttpOnly，2 小时）；
 * 2. X-Admin-Token 静态令牌 —— 供脚本/冒烟测试使用，与用户 JWT 体系完全隔离。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

  private final AdminSessionService sessions;
  private final LingoProperties props;

  public AdminAuthInterceptor(AdminSessionService sessions, LingoProperties props) {
    this.sessions = sessions;
    this.props = props;
  }

  @Override
  public boolean preHandle(@NonNull HttpServletRequest request,
                           @NonNull HttpServletResponse response,
                           @NonNull Object handler) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String cookie = cookieValue(request, AdminSessionService.COOKIE_NAME);
    if (cookie != null && sessions.verify(cookie)) {
      return true;
    }
    String token = request.getHeader("X-Admin-Token");
    if (matches(token, props.getAdminToken())) {
      return true;
    }
    throw ApiException.unauthorized("管理端登录已失效，请重新登录");
  }

  private static boolean matches(String actual, String expected) {
    String exp = expected == null ? "" : expected;
    return !exp.isBlank() && actual != null
        && MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
            exp.getBytes(StandardCharsets.UTF_8));
  }

  private static String cookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie c : cookies) {
      if (name.equals(c.getName())) {
        return c.getValue();
      }
    }
    return null;
  }
}
