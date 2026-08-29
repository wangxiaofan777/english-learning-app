package com.lingo.app.companion;

import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.UserContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companion")
@RequiredArgsConstructor
@Validated
public class CompanionController {

  private final CompanionService companionService;

  /** 陪练人设列表（含各自记得你的事） */
  @GetMapping
  public ApiResponse<List<CompanionService.CompanionView>> list() {
    return ApiResponse.ok(companionService.list(com.lingo.app.common.UserContext.get()));
  }

  /** 开始/继续与某个陪练的对话 */
  @PostMapping("/start")
  public ApiResponse<CompanionService.StartResult> start(@RequestBody StartReq req) {
    return ApiResponse.ok(companionService.start(UserContext.get(), req.getCompanionKey()));
  }

  /** 查看某个陪练记得的事 */
  @GetMapping("/memory")
  public ApiResponse<List<String>> memory(String companionKey) {
    return ApiResponse.ok(companionService.memory(UserContext.get(), companionKey));
  }

  /** 忘掉一条记忆 */
  @PostMapping("/forget")
  public ApiResponse<List<String>> forget(@RequestBody ForgetReq req) {
    return ApiResponse.ok(companionService.forget(UserContext.get(), req.getCompanionKey(),
        req.getFact()));
  }

  @Data
  public static class StartReq {
    @NotBlank(message = "缺少陪练 key")
    private String companionKey;
  }

  @Data
  public static class ForgetReq {
    @NotBlank(message = "缺少陪练 key")
    private String companionKey;
    @NotEmpty(message = "缺少要忘记的内容")
    private String fact;
  }
}
