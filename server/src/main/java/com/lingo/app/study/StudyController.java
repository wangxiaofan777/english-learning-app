package com.lingo.app.study;

import com.lingo.app.common.ApiException;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.UserContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudyController {

  private static final Set<String> KINDS =
      Set.of("review", "scenario", "dialog", "listening", "shadowing", "quiz", "daily");

  private final StudyService studyService;
  private final AchievementService achievementService;

  @GetMapping("/today")
  public ApiResponse<StudyService.TodayView> today() {
    return ApiResponse.ok(studyService.today(UserContext.get()));
  }

  @GetMapping("/stats")
  public ApiResponse<StudyService.StatsView> stats() {
    return ApiResponse.ok(studyService.stats(UserContext.get()));
  }

  @GetMapping("/achievements")
  public ApiResponse<?> achievements() {
    return ApiResponse.ok(achievementService.badges(UserContext.get()));
  }

  /** 听力精听、跟读等端上练习的自报时长（用于打卡与统计） */
  @PostMapping("/study/record")
  public ApiResponse<Void> record(@RequestBody RecordReq req) {
    if (!KINDS.contains(req.getKind())) {
      throw ApiException.badRequest("练习类型不合法");
    }
    studyService.record(UserContext.get(), req.getKind(), req.getMinutes(), req.getCount());
    return ApiResponse.ok();
  }

  @Data
  public static class RecordReq {
    private String kind;
    @Min(value = 0, message = "时长不合法")
    @Max(value = 120, message = "单次时长最多 120 分钟")
    private int minutes = 1;
    @Min(1)
    @Max(200)
    private int count = 1;
  }
}
