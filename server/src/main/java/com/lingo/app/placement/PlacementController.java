package com.lingo.app.placement;

import com.lingo.app.common.ApiResponse;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/placement")
@RequiredArgsConstructor
public class PlacementController {

  private final PlacementService placementService;

  @GetMapping("/questions")
  public ApiResponse<?> questions() {
    return ApiResponse.ok(placementService.questions());
  }

  @PostMapping("/submit")
  public ApiResponse<PlacementService.PlacementResult> submit(@RequestBody SubmitReq req) {
    return ApiResponse.ok(placementService.submit(req.getAnswers(), req.getSpokenText()));
  }

  @Data
  public static class SubmitReq {
    private Map<String, String> answers;
    private String spokenText;
  }
}
