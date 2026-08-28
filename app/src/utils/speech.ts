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
