package com.lingo.app.admin;

import com.lingo.app.common.ApiException;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.LingoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台登录：以 ADMIN_TOKEN 作为管理密码，换取短时效 HttpOnly 会话 Cookie。
 * Cookie 属性 SameSite=Strict + HttpOnly，配合独立部署入口降低 XSS/CSRF 面；
 * 静态令牌本身不再落浏览器存储。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

  private final AdminSessionService sessions;
  private final LingoProperties props;

  @PostMapping("/login")
  public ApiResponse<Map<String, Object>> login(@RequestBody LoginReq req,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
    String expected = props.getAdminToken() == null ? "" : props.getAdminToken();
    boolean ok = !expected.isBlank() && req.getPassword() != null
        && MessageDigest.isEqual(req.getPassword().getBytes(StandardCharsets.UTF_8),
            expected.getBytes(StandardCharsets.UTF_8));
    if (!ok) {
      log.warn("[admin] 登录失败 ip={}", clientIp(request));
      throw ApiException.unauthorized("管理密码错误");
    }
    response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(sessions.issue(),
        AdminSessionService.TTL.toSeconds(), request).toString());
    log.info("[admin] 登录成功 ip={}", clientIp(request));
    return ApiResponse.ok(Map.of("expiresIn", AdminSessionService.TTL.toSeconds()));
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE,
        sessionCookie("", 0, null).toString());
    return ApiResponse.ok();
  }

  /** 会话探活：经过 AdminAuthInterceptor 才到这里，能返回即代表登录有效 */
  @GetMapping("/me")
  public ApiResponse<Map<String, Object>> me() {
    return ApiResponse.ok(Map.of("authenticated", true,
        "expiresIn", AdminSessionService.TTL.toSeconds()));
  }

  private ResponseCookie sessionCookie(String value, long maxAge, HttpServletRequest request) {
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
        .from(AdminSessionService.COOKIE_NAME, value)
        .httpOnly(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(maxAge);
    if (request != null && isHttps(request)) {
      builder.secure(true);
    }
    return builder.build();
  }

  /** 经 nginx 反代时按 X-Forwarded-Proto 判定，直连时按 socket 是否 TLS */
  private static boolean isHttps(HttpServletRequest request) {
    if (request.isSecure()) {
      return true;
    }
    String proto = request.getHeader("X-Forwarded-Proto");
    return proto != null && proto.equalsIgnoreCase("https");
  }

  private static String clientIp(HttpServletRequest request) {
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

  @Data
  public static class LoginReq {
    private String password;
  }
}
