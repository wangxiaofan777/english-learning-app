package com.lingo.app.scenario;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_scenario_line")
public class ScenarioLineEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long scenarioId;
  private Integer idx;
  private String speaker;
  private String en;
  private String zh;
  private String audioUrl;
}
