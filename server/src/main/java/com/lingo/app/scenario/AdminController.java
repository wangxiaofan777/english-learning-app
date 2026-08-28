package com.lingo.app.scenario;

import com.lingo.app.common.ApiException;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.LingoProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
    if (token == null || !token.equals(props.getAdminToken())) {
      throw ApiException.unauthorized("admin token 无效");
    }
    String cefr = req.getCefr() == null ? "A2" : req.getCefr();
    return ApiResponse.ok(generationService.generate(req.getTrack(), req.getTopic(), cefr));
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
