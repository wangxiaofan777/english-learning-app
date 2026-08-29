package com.lingo.app.companion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_companion_memory")
public class CompanionMemoryEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;
  private String companionKey;
  /** JSON 字符串数组：关于用户的事实（英文短句，供 prompt 引用） */
  private String memoryJson;
  private LocalDateTime updatedAt;
}
