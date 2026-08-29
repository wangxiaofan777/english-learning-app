package com.lingo.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
    "com.lingo.app.user.mapper",
    "com.lingo.app.scenario.mapper",
    "com.lingo.app.conversation.mapper",
    "com.lingo.app.companion.mapper",
    "com.lingo.app.vocab.mapper",
    "com.lingo.app.study.mapper",
    "com.lingo.app.course.mapper"
})
public class LingoApplication {

  public static void main(String[] args) {
    SpringApplication.run(LingoApplication.class, args);
  }
}
