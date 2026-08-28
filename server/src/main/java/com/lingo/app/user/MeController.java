package com.lingo.app.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.common.ApiResponse;
import com.lingo.app.common.UserContext;
import com.lingo.app.user.mapper.UserMapper;
import com.lingo.app.user.mapper.UserProfileMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MeController {

  private static final Set<String> TRACKS = Set.of("daily", "work", "travel", "exam");
  private static final Set<String> AGE_BANDS = Set.of("child", "teen", "adult", "senior");

  private final UserMapper userMapper;
  private final UserProfileMapper profileMapper;
  private final com.lingo.app.course.CourseService courseService;

  @GetMapping("/me")
  public ApiResponse<AuthService.ProfileView> me() {
    Long userId = UserContext.get();
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      throw ApiException.notFound("用户不存在");
    }
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>().eq(UserProfileEntity::getUserId, userId));
    return ApiResponse.ok(AuthService.ProfileView.of(user, profile));
  }

  @PostMapping("/onboarding")
  public ApiResponse<OnboardingResponse> onboarding(@RequestBody OnboardingReq req) {
    if (!TRACKS.contains(req.getGoalTrack())) {
      throw ApiException.badRequest("学习目标不合法");
    }
    if (req.getAgeBand() != null && !AGE_BANDS.contains(req.getAgeBand())) {
      throw ApiException.badRequest("年龄段不合法");
    }
    Long userId = UserContext.get();
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>().eq(UserProfileEntity::getUserId, userId));
    profile.setAgeBand(req.getAgeBand());
    profile.setGoalTrack(req.getGoalTrack());
    profile.setDailyMinutes(req.getDailyMinutes());
    profile.setOnboardingStep(profile.getCefrLevel() == null ? "placement" : "done");
    profileMapper.updateById(profile);

    // 按登记信息自动制定课程（已有进行中课程则保持不变）
    courseService.autoEnroll(userId, profile.getGoalTrack(), profile.getAgeBand(),
        profile.getCefrLevel());

    UserEntity user = userMapper.selectById(userId);
    return ApiResponse.ok(new OnboardingResponse(profile.getOnboardingStep(),
        AuthService.ProfileView.of(user, profile)));
  }

  @Data
  public static class OnboardingReq {
    private String ageBand;
    @NotBlank(message = "请选择学习目标")
    private String goalTrack;
    @Min(value = 5, message = "每日时长最少 5 分钟")
    @Max(value = 120, message = "每日时长最多 120 分钟")
    private Integer dailyMinutes = 15;
  }

  public record OnboardingResponse(String nextStep, AuthService.ProfileView profile) {
  }
}
