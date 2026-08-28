/**
 * 语音能力封装：
 * - 听 AI（TTS）：H5 用浏览器 speechSynthesis；微信小程序用「微信同声传译」插件。
 * - 说给 AI（ASR）：微信小程序用同传插件的语音识别，按住说话。
 * - App 端 v1 先走文本，原生语音评测见设计文档 §7.1。
 */

// #ifdef MP-WEIXIN
declare function requirePlugin(name: string): any;
// #endif

export function ttsAvailable(): boolean {
  // #ifdef H5
  return typeof speechSynthesis !== "undefined";
  // #endif
  // #ifdef MP-WEIXIN
  try {
    requirePlugin("WechatSI");
    return true;
  } catch (e) {
    return false;
  }
  // #endif
  return false;
}

/** 朗读英文文本 */
export function speak(text: string) {
  if (!text) return;
  // #ifdef H5
  try {
    speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "en-US";
    utterance.rate = 0.95;
    speechSynthesis.speak(utterance);
  } catch (e) {
    console.warn("tts failed", e);
  }
  // #endif
  // #ifdef MP-WEIXIN
  try {
    const plugin = requirePlugin("WechatSI");
    plugin.textToSpeech({
      lang: "en_US",
      content: text.slice(0, 500),
      success: (res: { filename: string }) => {
        const audio = uni.createInnerAudioContext();
        audio.src = res.filename;
        audio.play();
      },
      fail: (err: unknown) => console.warn("wechat tts failed", err),
    });
  } catch (e) {
    console.warn("tts plugin missing", e);
  }
  // #endif
}

// #ifdef MP-WEIXIN
interface RecorderHandlers {
  onResult: (text: string) => void;
  onError: (message: string) => void;
}

/** 创建微信端「按住说话」识别器；插件未注册时返回 null */
export function createRecorder(handlers: RecorderHandlers): {
  start: () => void;
  stop: () => void;
} | null {
  try {
    const plugin = requirePlugin("WechatSI");
    const manager = plugin.getRecordRecognitionManager();
    let finalText = "";
    manager.onRecognize?.((res: { result: string }) => {
      finalText = res.result || "";
    });
    manager.onStop?.(() => {
      if (finalText.trim()) {
        handlers.onResult(finalText.trim());
      } else {
        handlers.onError("没听清，再试一次");
      }
    });
    manager.onError?.((res: unknown) => {
      handlers.onError("语音识别不可用");
      console.warn("asr error", res);
    });
    return {
      start: () => {
        finalText = "";
        manager.start({ lang: "en_US", duration: 30000 });
      },
      stop: () => manager.stop(),
    };
  } catch (e) {
    return null;
  }
}
// #endif

/** 朗读并等待播放结束（用于逐句精听的自动连播） */
export function speakAsync(text: string): Promise<void> {
  return new Promise<void>((resolve) => {
    // #ifdef H5
    try {
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = "en-US";
      utterance.rate = 0.9;
      utterance.onend = () => resolve();
      utterance.onerror = () => resolve();
      speechSynthesis.cancel();
      speechSynthesis.speak(utterance);
    } catch (e) {
      resolve();
    }
    return;
    // #endif
    // #ifdef MP-WEIXIN
    try {
      const plugin = requirePlugin("WechatSI");
      plugin.textToSpeech({
        lang: "en_US",
        content: text.slice(0, 500),
        success: (res: { filename: string }) => {
          const audio = uni.createInnerAudioContext();
          audio.src = res.filename;
          audio.onEnded(() => resolve());
          audio.onError(() => resolve());
          audio.play();
        },
        fail: () => resolve(),
      });
    } catch (e) {
      resolve();
    }
    return;
    // #endif
    resolve();
  });
}

/** 端上语音识别是否可用（H5 Web Speech / 微信同传插件） */
export function asrAvailable(): boolean {
  // #ifdef H5
  const w = window as unknown as Record<string, unknown>;
  return typeof w.webkitSpeechRecognition !== "undefined" || typeof w.SpeechRecognition !== "undefined";
  // #endif
  // #ifdef MP-WEIXIN
  try {
    requirePlugin("WechatSI");
    return true;
  } catch (e) {
    return false;
  }
  // #endif
  return false;
}

interface RecognitionHandlers {
  onResult: (text: string) => void;
  onError: (message: string) => void;
  onEnd?: () => void;
}

/** 开始一次语音识别；调用返回的 stop() 结束本次识别 */
export function startRecognition(handlers: RecognitionHandlers): { stop: () => void } | null {
  // #ifdef H5
  const w = window as unknown as Record<string, any>;
  const SR = w.webkitSpeechRecognition || w.SpeechRecognition;
  if (!SR) return null;
  try {
    const rec = new SR();
    rec.lang = "en-US";
    rec.interimResults = false;
    rec.maxAlternatives = 1;
    rec.onresult = (event: any) => {
      const text = Array.from(event.results as ArrayLike<any>)
        .map((r) => r[0].transcript as string)
        .join(" ")
        .trim();
      if (text) {
        handlers.onResult(text);
      }
    };
    rec.onerror = () => {
      handlers.onError("识别没成功，请再试一次或用自评");
      handlers.onEnd?.();
    };
    rec.onend = () => handlers.onEnd?.();
    rec.start();
    return {
      stop: () => {
        try {
          rec.stop();
        } catch (e) {
          // 已经停止时忽略
        }
      },
    };
  } catch (e) {
    return null;
  }
  // #endif
  // #ifdef MP-WEIXIN
  try {
    const plugin = requirePlugin("WechatSI");
    const manager = plugin.getRecordRecognitionManager();
    let finalText = "";
    manager.onRecognize?.((res: { result: string }) => {
      finalText = res.result || finalText;
    });
    manager.onStop?.(() => {
      if (finalText.trim()) {
        handlers.onResult(finalText.trim());
      } else {
        handlers.onError("没听清，再试一次");
      }
      handlers.onEnd?.();
    });
    manager.onError?.(() => {
      handlers.onError("语音识别不可用");
      handlers.onEnd?.();
    });
    manager.start({ lang: "en_US", duration: 30000 });
    return {
      stop: () => {
        try {
          manager.stop();
        } catch (e) {
          // 已经停止时忽略
        }
      },
    };
  } catch (e) {
    return null;
  }
  // #endif
  return null;
}
