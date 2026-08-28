package com.lingo.app.scenario;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_scenario")
public class ScenarioEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String track;
  private String topic;
  private String titleZh;
  private String titleEn;
  private String cefr;
  private String roleSetting;
  private String introZh;
  private String source;
  private String status;
  private Integer sortNo;
  private LocalDateTime createdAt;
}
