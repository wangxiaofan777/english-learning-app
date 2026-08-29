package com.lingo.app.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;
  private final LingoProperties lingoProperties;

  public WebConfig(AuthInterceptor authInterceptor, LingoProperties lingoProperties) {
    this.authInterceptor = authInterceptor;
    this.lingoProperties = lingoProperties;
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns(
            "/api/v1/health",
            "/api/v1/auth/**",
            "/api/v1/admin/**"
        );
    // 管理端的鉴权拦截器由 admin 包内的 AdminWebConfig 自行注册，与用户体系互不感知
  }

  @Override
  public void addCorsMappings(@NonNull CorsRegistry registry) {
    // 生产走同源 nginx 反代，不依赖 CORS；跨域来源用 CORS_ALLOWED_ORIGINS 白名单收敛
    registry.addMapping("/**")
        .allowedOriginPatterns(corsOriginPatterns())
        .allowedMethods("*")
        .allowedHeaders("*")
        .maxAge(3600);
  }

  private String[] corsOriginPatterns() {
    String raw = lingoProperties.getCorsAllowedOrigins();
    if (raw == null || raw.isBlank()) {
      return new String[] {"*"};
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toArray(String[]::new);
  }
}
