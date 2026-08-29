package com.lingo.app.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.scenario.GenerationService;
import com.lingo.app.scenario.ScenarioEntity;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容运营接口（从 scenario.AdminController 迁入）：鉴权统一由 AdminAuthInterceptor
 * 承担（会话 Cookie 或 X-Admin-Token），写操作记录审计日志。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContentController {

  private final GenerationService generationService;

  @PostMapping("/scenarios/generate")
  public ApiResponse<GenerationService.ScenarioCard> generate(@RequestBody GenerateReq req) {
    String cefr = req.getCefr() == null ? "A2" : req.getCefr();
    GenerationService.ScenarioCard card =
        generationService.generate(req.getTrack(), req.getTopic(), cefr);
    log.info("[admin] generate scenario track={} topic={} cefr={} id={}",
        req.getTrack(), req.getTopic(), cefr, card.id());
    return ApiResponse.ok(card);
  }

  // ---------- 内容真实化流水线 ----------

  @GetMapping("/content/status")
  public ApiResponse<Map<String, Object>> contentStatus() {
    return ApiResponse.ok(generationService.contentStatus());
  }

  @GetMapping("/content/scenarios")
  public ApiResponse<Map<String, Object>> contentScenarios(
      @RequestParam(defaultValue = "template") String source,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long size) {
    Page<ScenarioEntity> result = generationService.listBySource(source, page, size);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("total", result.getTotal());
    body.put("page", result.getCurrent());
    body.put("size", result.getSize());
    body.put("rows", result.getRecords());
    return ApiResponse.ok(body);
  }

  @PostMapping("/content/rewrite")
  public ApiResponse<GenerationService.ScenarioCard> rewrite(@RequestBody RewriteReq req) {
    GenerationService.ScenarioCard card = generationService.rewrite(req.getScenarioId());
    log.info("[admin] rewrite scenario id={}", req.getScenarioId());
    return ApiResponse.ok(card);
  }

  @PostMapping("/content/rewrite-batch")
  public ApiResponse<Map<String, Object>> rewriteBatch(@RequestBody RewriteReq req) {
    Map<String, Object> result = generationService.rewriteBatch(
        req.getLimit() == null ? 10 : req.getLimit());
    log.info("[admin] rewrite batch limit={} rewritten={}",
        req.getLimit(), result.get("rewritten"));
    return ApiResponse.ok(result);
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
