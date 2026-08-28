package com.lingo.app.study;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_study_log")
public class StudyLogEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;
  private String studyDate;
  private String kind;
  private Integer minutes;
  private Integer count;
  private Integer xp;
  private LocalDateTime createdAt;
}
