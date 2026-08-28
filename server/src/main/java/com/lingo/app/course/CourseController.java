package com.lingo.app.course;

import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.UserContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

  private final CourseService courseService;

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(courseService.listForUser(UserContext.get()));
  }

  @GetMapping("/current")
  public ApiResponse<CourseService.CourseDetail> current() {
    return ApiResponse.ok(courseService.current(UserContext.get()));
  }

  @GetMapping("/{id}")
  public ApiResponse<CourseService.CourseDetail> detail(@PathVariable Long id) {
    return ApiResponse.ok(courseService.detail(UserContext.get(), id));
  }

  @PostMapping("/{id}/enroll")
  public ApiResponse<CourseService.CourseCard> enroll(@PathVariable Long id) {
    return ApiResponse.ok(courseService.enroll(UserContext.get(), id));
  }

  /** 对话/精听/跟读完成时由端上调用，标记对应课时完成 */
  @PostMapping("/complete")
  public ApiResponse<CourseService.CompleteResult> complete(@RequestBody CompleteReq req) {
    return ApiResponse.ok(courseService.completeLesson(UserContext.get(), req.getLessonType(),
        req.getScenarioId(), req.getScore()));
  }

  @Data
  public static class CompleteReq {
    private String lessonType;
    private Long scenarioId;
    private Integer score;
  }
}
