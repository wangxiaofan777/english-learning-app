package com.lingo.app.admin;

import com.lingo.app.common.LingoProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 管理会话：短时效 JWT，签发后写入 HttpOnly Cookie（见 AdminAuthController），
 * 与用户登录态（JwtUtil，30 天 Bearer Token）相互独立、互不通用。
 */
@Component
public class AdminSessionService {

  public static final String COOKIE_NAME = "admin_session";
  public static final Duration TTL = Duration.ofHours(2);

  private final SecretKey key;

  public AdminSessionService(LingoProperties props) {
    String secret = props.getJwtSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("lingo.jwt-secret 未配置：管理会话签名依赖该密钥（≥32 字节）");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String issue() {
    Date now = new Date();
    return Jwts.builder()
        .subject("admin")
        .claim("scope", "admin")
        .issuedAt(now)
        .expiration(new Date(now.getTime() + TTL.toMillis()))
        .signWith(key)
        .compact();
  }

  /** @return 会话是否有效 */
  public boolean verify(String token) {
    try {
      var claims = Jwts.parser().verifyWith(key).build()
          .parseSignedClaims(token).getPayload();
      return "admin".equals(claims.getSubject())
          && "admin".equals(claims.get("scope", String.class));
    } catch (Exception e) {
      return false;
    }
  }
}
