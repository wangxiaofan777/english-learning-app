package com.lingo.app.study;

import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudyController {

  private final StudyService studyService;

  @GetMapping("/today")
  public ApiResponse<StudyService.TodayView> today() {
    return ApiResponse.ok(studyService.today(UserContext.get()));
  }

  @GetMapping("/stats")
  public ApiResponse<StudyService.StatsView> stats() {
    return ApiResponse.ok(studyService.stats(UserContext.get()));
  }
}
