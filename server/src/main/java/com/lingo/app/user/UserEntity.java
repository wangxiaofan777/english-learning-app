package com.lingo.app.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_user")
public class UserEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String openId;
  private String unionId;
  private String phone;
  private String nickname;
  private String avatar;
  private Boolean isGuest;
  private LocalDateTime createdAt;
}
