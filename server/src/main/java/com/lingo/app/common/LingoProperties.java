package com.lingo.app.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lingo")
public class LingoProperties {

  private String jwtSecret;
  private String adminToken;

  private Wx wx = new Wx();
  private Llm llm = new Llm();

  @Data
  public static class Wx {
    private String appid;
    private String secret;
  }

  @Data
  public static class Llm {
    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature;
  }

  public boolean llmEnabled() {
    return llm.getApiKey() != null && !llm.getApiKey().isBlank()
        && llm.getBaseUrl() != null && !llm.getBaseUrl().isBlank();
  }
}
