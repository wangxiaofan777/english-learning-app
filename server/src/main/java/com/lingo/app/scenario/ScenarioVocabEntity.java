package com.lingo.app.scenario;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_scenario_vocab")
public class ScenarioVocabEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long scenarioId;
  private String word;
  private String phonetic;
  private String meaningZh;
  private String exampleEn;
  private String exampleZh;
}
