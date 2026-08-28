package com.lingo.app.course;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_course_lesson")
public class CourseLessonEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long courseId;
  private Integer idx;
  private String lessonType;
  private Long scenarioId;
  private String titleZh;
  private Integer minutes;
}
