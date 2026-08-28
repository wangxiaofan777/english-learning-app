package com.lingo.app.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_conversation")
public class ConversationEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;
  private Long scenarioId;
  private String status;
  private String aiSummary;
  private String coachJson;
  private Integer msgCount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
