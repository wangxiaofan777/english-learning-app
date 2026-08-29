package com.lingo.app.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private static final Duration TTL = Duration.ofDays(30);
  private final SecretKey key;

  public JwtUtil(LingoProperties props) {
    String secret = props.getJwtSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "lingo.jwt-secret 未配置：生产环境必须通过环境变量 JWT_SECRET 或 config/local.yml 提供"
              + "至少 32 字节的随机密钥（本地零配置体验请使用 h2 profile）");
    }
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("lingo.jwt-secret must be at least 32 bytes");
    }
    this.key = Keys.hmacShaKeyFor(bytes);
  }

  public String issue(Long userId) {
    Date now = new Date();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(now)
        .expiration(new Date(now.getTime() + TTL.toMillis()))
        .signWith(key)
        .compact();
  }

  /** @return userId, or null when the token is invalid/expired */
  public Long verify(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build()
          .parseSignedClaims(token).getPayload();
      return Long.parseLong(claims.getSubject());
    } catch (Exception e) {
      return null;
    }
  }
}
