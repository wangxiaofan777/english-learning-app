package com.lingo.app.vocab;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_review_log")
public class ReviewLogEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;
  private Long vocabId;
  private Integer rating;
  private Double stability;
  private Double difficulty;
  private LocalDateTime reviewedAt;
}
