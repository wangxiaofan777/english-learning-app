package com.lingo.app.vocab;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_vocab_entry")
public class VocabEntryEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;
  private String word;
  private String phonetic;
  private String meaningZh;
  private String exampleEn;
  private String exampleZh;
  private String source;
  private Long scenarioId;
  private String fsrsState;
  private Double fsrsStability;
  private Double fsrsDifficulty;
  private Integer fsrsReps;
  private Integer fsrsLapses;
  private LocalDateTime dueAt;
  private LocalDateTime lastReviewAt;
  private LocalDateTime createdAt;
}
