package com.lingo.app.conversation;

import com.lingo.app.common.LingoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LlmConfig {

  /** 配置了 LLM_API_KEY 时走真实模型（LangChain4j 接入），否则用内置 Mock */
  @Bean
  @Primary
  public LlmClient llmClient(LingoProperties props, MockLlmClient mock, LangChainLlmClient real) {
    return props.llmEnabled() ? real : mock;
  }
}
