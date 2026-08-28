package com.lingo.app.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.common.JwtUtil;
import com.lingo.app.common.LingoProperties;
import com.lingo.app.user.mapper.UserMapper;
import com.lingo.app.user.mapper.UserProfileMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserMapper userMapper;
  private final UserProfileMapper profileMapper;
  private final JwtUtil jwtUtil;
  private final LingoProperties props;
  private final WxClient wxClient;

  @Transactional
  public LoginResponse guestLogin(String nickname) {
    UserEntity user = findOrCreate("guest_" + UUID.randomUUID(), nickname, true);
    return toResponse(user);
  }

  @Transactional
  public LoginResponse wechatLogin(String code, String nickname) {
    if (code == null || code.isBlank()) {
      throw ApiException.badRequest("缺少微信登录 code");
    }
    String openId;
    if (props.getWx().getAppid() == null || props.getWx().getAppid().isBlank()) {
      // 未配置微信 appid：开发兜底，直接用 code 派生一个稳定 openId
      log.warn("WX_APPID not configured, using dev fallback for wechat login");
      openId = "wxdev_" + Integer.toHexString(code.hashCode());
    } else {
      openId = wxClient.codeToOpenId(code);
    }
    UserEntity user = findOrCreate(openId, nickname, false);
    return toResponse(user);
  }

  private UserEntity findOrCreate(String openId, String nickname, boolean guest) {
    UserEntity user = userMapper.selectOne(
        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getOpenId, openId));
    if (user != null) {
      if (nickname != null && !nickname.isBlank() && !nickname.equals(user.getNickname())) {
        user.setNickname(nickname);
        userMapper.updateById(user);
      }
      return user;
    }
    user = new UserEntity();
    user.setOpenId(openId);
    user.setNickname(nickname == null || nickname.isBlank() ? "英语学习者" : nickname);
    user.setIsGuest(guest);
    userMapper.insert(user);

    UserProfileEntity profile = new UserProfileEntity();
    profile.setUserId(user.getId());
    profile.setDailyMinutes(15);
    profile.setOnboardingStep("goal");
    profile.setStreakDays(0);
    profileMapper.insert(profile);
    return user;
  }

  private LoginResponse toResponse(UserEntity user) {
    UserProfileEntity profile = profileMapper.selectOne(
        new LambdaQueryWrapper<UserProfileEntity>().eq(UserProfileEntity::getUserId, user.getId()));
    String token = jwtUtil.issue(user.getId());
    return new LoginResponse(token, profile.getOnboardingStep(), ProfileView.of(user, profile));
  }

  public record LoginResponse(String token, String onboardingStep, ProfileView profile) {
  }

  public record ProfileView(Long userId, String nickname, Boolean isGuest, String ageBand,
                            String goalTrack, Integer dailyMinutes, String cefrLevel,
                            java.util.List<String> weakTags, String onboardingStep,
                            Integer streakDays) {

    public static ProfileView of(UserEntity u, UserProfileEntity p) {
      java.util.List<String> tags = java.util.List.of();
      if (p.getWeakTags() != null && !p.getWeakTags().isBlank()) {
        try {
          tags = new ObjectMapper().readValue(p.getWeakTags(),
              new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
              });
        } catch (Exception ignored) {
          // weakTags 仅用于展示，解析失败时给空列表
        }
      }
      return new ProfileView(u.getId(), u.getNickname(), u.getIsGuest(), p.getAgeBand(),
          p.getGoalTrack(), p.getDailyMinutes(), p.getCefrLevel(), tags, p.getOnboardingStep(),
          p.getStreakDays());
    }
  }
}
