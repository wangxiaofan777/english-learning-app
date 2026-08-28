package com.lingo.app.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_user_profile")
public class UserProfileEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;
  private String ageBand;
  private String goalTrack;
  private Integer dailyMinutes;
  private String cefrLevel;
  private String weakTags;
  private String onboardingStep;
  private Integer streakDays;
  private Integer xp;
  private String lastStudyDate;
  private LocalDateTime createdAt;
}
