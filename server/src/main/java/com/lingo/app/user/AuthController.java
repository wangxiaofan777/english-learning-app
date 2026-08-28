package com.lingo.app.user;

import com.lingo.app.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/guest")
  public ApiResponse<AuthService.LoginResponse> guest(@RequestBody(required = false) NicknameReq req) {
    String nickname = req == null ? null : req.getNickname();
    return ApiResponse.ok(authService.guestLogin(nickname));
  }

  @PostMapping("/wechat")
  public ApiResponse<AuthService.LoginResponse> wechat(@RequestBody WechatLoginReq req) {
    return ApiResponse.ok(authService.wechatLogin(req.getCode(), req.getNickname()));
  }

  @Data
  public static class NicknameReq {
    private String nickname;
  }

  @Data
  public static class WechatLoginReq {
    @NotBlank(message = "缺少微信登录 code")
    private String code;
    private String nickname;
  }
}
