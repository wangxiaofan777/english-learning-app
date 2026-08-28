package com.lingo.app.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_message")
public class MessageEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long conversationId;
  private Integer idx;
  private String role;
  private String content;
  private String feedbackJson;
  private LocalDateTime createdAt;
}
