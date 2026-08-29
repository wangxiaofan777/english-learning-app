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
  /** 是否开放游客登录（生产可关闭） */
  private boolean guestEnabled = true;
  /** 微信 appid 未配置时是否允许用 code 派生 openId 的开发兜底（仅本地开发开启） */
  private boolean wxDevFallback = false;
  /** LLM 未配置时是否允许降级到内置 Mock（生产必须 false，避免用户拿到假回复） */
  private boolean llmMockAllowed = false;
  /** 跨域白名单：逗号分隔的来源；留空表示不限制（H5 生产走同源 nginx，不依赖 CORS） */
  private String corsAllowedOrigins;

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
