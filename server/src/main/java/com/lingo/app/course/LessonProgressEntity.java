package com.lingo.app.course;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_lesson_progress")
public class LessonProgressEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;
  private Long lessonId;
  private Long courseId;
  private String status;
  private Integer score;
  private LocalDateTime completedAt;
}
