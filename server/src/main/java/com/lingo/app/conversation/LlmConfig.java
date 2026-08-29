package com.lingo.app.conversation;

import com.lingo.app.common.LingoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class LlmConfig {

  /**
   * 配置了 LLM_API_KEY 时走真实模型（LangChain4j 接入）；未配置时只有在
   * lingo.llm-mock-allowed=true（本地开发）才降级 Mock，否则直接启动失败——
   * 避免生产环境密钥注入失败时用户静默拿到假回复。
   */
  @Bean
  @Primary
  public LlmClient llmClient(LingoProperties props, MockLlmClient mock, LangChainLlmClient real) {
    if (props.llmEnabled()) {
      return real;
    }
    if (!props.isLlmMockAllowed()) {
      throw new IllegalStateException(
          "LLM 未配置（LLM_BASE_URL / LLM_API_KEY 为空）且未开启 lingo.llm-mock-allowed，"
              + "拒绝以 Mock 模式启动。请配置大模型密钥，或仅本地开发时设置 LLM_MOCK_ALLOWED=true");
    }
    log.warn("LLM 未配置，当前使用内置 Mock 回复（仅限本地开发）");
    return mock;
  }
}
