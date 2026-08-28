package com.lingo.app.conversation;

import com.lingo.app.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

  private final ChatService chatService;

  @PostMapping
  public ApiResponse<ChatService.CreateResult> create(@RequestBody CreateReq req) {
    return ApiResponse.ok(chatService.create(com.lingo.app.common.UserContext.get(),
        req.getScenarioId()));
  }

  @GetMapping
  public ApiResponse<?> history() {
    return ApiResponse.ok(chatService.history(com.lingo.app.common.UserContext.get()));
  }

  @GetMapping("/{id}")
  public ApiResponse<ChatService.ConversationDetail> detail(@PathVariable Long id) {
    return ApiResponse.ok(chatService.detail(com.lingo.app.common.UserContext.get(), id));
  }

  @GetMapping("/{id}/messages")
  public ApiResponse<?> rawMessages(@PathVariable Long id) {
    return ApiResponse.ok(chatService.messages(com.lingo.app.common.UserContext.get(), id));
  }

  @PostMapping("/{id}/messages")
  public ApiResponse<ChatService.MessageView> reply(@PathVariable Long id,
                                                    @RequestBody ReplyReq req) {
    return ApiResponse.ok(chatService.reply(com.lingo.app.common.UserContext.get(), id,
        req.getContent().trim()));
  }

  @GetMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable Long id, @RequestParam("text") String text) {
    return chatService.streamReply(com.lingo.app.common.UserContext.get(), id, text.trim());
  }

  @PostMapping("/{id}/finish")
  public ApiResponse<ChatService.FinishResult> finish(@PathVariable Long id) {
    return ApiResponse.ok(chatService.finish(com.lingo.app.common.UserContext.get(), id));
  }

  @Data
  public static class CreateReq {
    @NotNull(message = "缺少场景 id")
    private Long scenarioId;
  }

  @Data
  public static class ReplyReq {
    @NotBlank(message = "说点什么吧")
    private String content;
  }
}
