package com.lingo.app.course;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_course")
public class CourseEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String track;
  private String ageBand;
  private String cefr;
  private String titleZh;
  private String titleEn;
  private String description;
  private Integer sortNo;
  private LocalDateTime createdAt;
}
