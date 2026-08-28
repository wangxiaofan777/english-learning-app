package com.lingo.app.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingo.app.common.ApiException;
import com.lingo.app.common.LingoProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WxClient {

  private final LingoProperties props;
  private final ObjectMapper objectMapper;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  public String codeToOpenId(String code) {
    String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + props.getWx().getAppid()
        + "&secret=" + props.getWx().getSecret()
        + "&js_code=" + code + "&grant_type=authorization_code";
    try {
      HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      JsonNode node = objectMapper.readTree(resp.body());
      if (node.hasNonNull("openid")) {
        return node.get("openid").asText();
      }
      log.error("wx code2session failed: {}", resp.body());
      throw ApiException.badRequest("微信登录失败，请重试");
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("wx code2session error", e);
      throw ApiException.badRequest("微信登录失败，请稍后重试");
    }
  }
}
