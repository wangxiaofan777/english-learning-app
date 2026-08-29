package com.lingo.app.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端拦截器注册：刻意收敛在 admin 包内。剥离管理后台时删除本包即可，
 * 公共配置（common/WebConfig）不需要保留任何管理端痕迹。
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

  private final AdminAuthInterceptor adminAuthInterceptor;

  public AdminWebConfig(AdminAuthInterceptor adminAuthInterceptor) {
    this.adminAuthInterceptor = adminAuthInterceptor;
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    // /api/v1/admin/** 全部鉴权，仅登录端点自身放行
    registry.addInterceptor(adminAuthInterceptor)
        .addPathPatterns("/api/v1/admin/**")
        .excludePathPatterns("/api/v1/admin/auth/login");
  }
}
