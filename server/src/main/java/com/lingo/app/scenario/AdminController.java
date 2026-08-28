package com.lingo.app.scenario;

import com.lingo.app.common.ApiException;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.LingoProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端点：用 X-Admin-Token 鉴权（与用户体系隔离）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

  private final GenerationService generationService;
  private final LingoProperties props;

  @PostMapping("/scenarios/generate")
  public ApiResponse<GenerationService.ScenarioCard> generate(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody GenerateReq req) {
    checkToken(token);
    String cefr = req.getCefr() == null ? "A2" : req.getCefr();
    return ApiResponse.ok(generationService.generate(req.getTrack(), req.getTopic(), cefr));
  }

  // ---------- 内容真实化流水线 ----------

  @GetMapping("/content/status")
  public ApiResponse<?> contentStatus(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    checkToken(token);
    return ApiResponse.ok(generationService.contentStatus());
  }

  @GetMapping("/content/scenarios")
  public ApiResponse<?> contentScenarios(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(defaultValue = "template") String source,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long size) {
    checkToken(token);
    return ApiResponse.ok(generationService.listBySource(source, page, size));
  }

  @PostMapping("/content/rewrite")
  public ApiResponse<GenerationService.ScenarioCard> rewrite(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody RewriteReq req) {
    checkToken(token);
    return ApiResponse.ok(generationService.rewrite(req.getScenarioId()));
  }

  @PostMapping("/content/rewrite-batch")
  public ApiResponse<?> rewriteBatch(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody RewriteReq req) {
    checkToken(token);
    return ApiResponse.ok(generationService.rewriteBatch(
        req.getLimit() == null ? 10 : req.getLimit()));
  }

  private void checkToken(String token) {
    if (token == null || !token.equals(props.getAdminToken())) {
      throw ApiException.unauthorized("admin token 无效");
    }
  }

  @Data
  public static class RewriteReq {
    private Long scenarioId;
    private Integer limit;
  }

  @Data
  public static class GenerateReq {
    @NotBlank(message = "缺少轨道")
    private String track;
    @NotBlank(message = "缺少主题")
    private String topic;
    private String cefr;
  }
}
