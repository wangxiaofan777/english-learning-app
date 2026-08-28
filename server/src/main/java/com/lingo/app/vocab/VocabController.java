package com.lingo.app.vocab;

import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.UserContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vocab")
@RequiredArgsConstructor
public class VocabController {

  private final VocabService vocabService;

  @PostMapping
  public ApiResponse<VocabEntryEntity> add(@RequestBody AddReq req) {
    VocabEntryEntity entry = vocabService.add(UserContext.get(), req.getWord(),
        req.getPhonetic(), req.getMeaningZh(), req.getExampleEn(), req.getExampleZh(),
        req.getSource(), req.getScenarioId());
    return ApiResponse.ok(entry);
  }

  @GetMapping
  public ApiResponse<?> list(@RequestParam(defaultValue = "1") long page,
                             @RequestParam(defaultValue = "20") long size) {
    return ApiResponse.ok(vocabService.list(UserContext.get(), page, size));
  }

  @GetMapping("/queue")
  public ApiResponse<QueueView> queue(@RequestParam(defaultValue = "15") int limit) {
    Long userId = UserContext.get();
    return ApiResponse.ok(new QueueView(vocabService.queue(userId, limit),
        vocabService.dueCount(userId)));
  }

  @PostMapping("/{id}/grade")
  public ApiResponse<VocabService.ReviewResult> grade(@PathVariable Long id,
                                                      @RequestBody GradeReq req) {
    return ApiResponse.ok(vocabService.grade(UserContext.get(), id, req.getRating()));
  }

  @Data
  public static class AddReq {
    @NotBlank(message = "缺少单词")
    private String word;
    private String phonetic;
    private String meaningZh;
    private String exampleEn;
    private String exampleZh;
    private String source;
    private Long scenarioId;
  }

  @Data
  public static class GradeReq {
    @NotNull(message = "缺少评分")
    @Min(value = 1, message = "评分 1-4")
    @Max(value = 4, message = "评分 1-4")
    private Integer rating;
  }

  public record QueueView(java.util.List<VocabEntryEntity> cards, long dueCount) {
  }
}
