package com.lingo.app.common;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

  private final JdbcTemplate jdbcTemplate;

  /** 存活 + 数据库连通性探测（容器 healthcheck 与负载均衡探活使用） */
  @GetMapping("/health")
  public ResponseEntity<ApiResponse<Map<String, String>>> health() {
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "up", "db", "up")));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(ApiResponse.error(503, "database unavailable"));
    }
  }
}
