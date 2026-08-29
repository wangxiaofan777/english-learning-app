import type { MessageView } from "./types";
import { api, getToken } from "./api";

let STREAM_URL = "";
// #ifndef H5
STREAM_URL = import.meta.env.VITE_API_BASE_URL ?? "";
// #endif

interface StreamHandlers {
  onStart?: () => void;
  onDelta: (text: string) => void;
  onMeta: (message: MessageView) => void;
  onDone: () => void;
  onError: (message: string) => void;
}

/**
 * 对话流式：H5 用 fetch ReadableStream 解析 SSE；
 * 微信小程序用 uni.request enableChunked；其他端降级为非流式请求。
 */
export function streamMessage(
  conversationId: string,
  text: string,
  handlers: StreamHandlers
): void {
  // #ifdef H5
  h5Stream(conversationId, text, handlers);
  return;
  // #endif

  // #ifdef MP-WEIXIN
  mpStream(conversationId, text, handlers);
  return;
  // #endif

  // 其他平台：降级为非流式
  fallbackStream(conversationId, text, handlers);
}

function dispatchEvent(event: string, data: string, h: StreamHandlers) {
  try {
    const payload = data ? JSON.parse(data) : {};
    if (event === "start") {
      h.onStart?.();
    } else if (event === "delta") {
      h.onDelta(payload.text || "");
    } else if (event === "meta") {
      h.onMeta(payload as MessageView);
    } else if (event === "done") {
      h.onDone();
    } else if (event === "error") {
      h.onError(payload.message || "AI 回复失败");
    }
  } catch (e) {
    console.warn("bad sse payload", data);
  }
}

// ---------- H5 ----------
// #ifdef H5
async function h5Stream(conversationId: string, text: string, h: StreamHandlers) {
  try {
    const resp = await fetch(
      `${STREAM_URL}/api/v1/conversations/${conversationId}/messages/stream?text=${encodeURIComponent(text)}`,
      { headers: { Authorization: `Bearer ${getToken()}` } }
    );
    if (!resp.ok || !resp.body) {
      h.onError(`连接失败(${resp.status})`);
      return;
    }
    const reader = resp.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    let event = "";
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split("\n\n");
      buffer = blocks.pop() || "";
      for (const block of blocks) {
        for (const line of block.split("\n")) {
          if (line.startsWith("event:")) {
            event = line.slice(6).trim();
          } else if (line.startsWith("data:")) {
            dispatchEvent(event, line.slice(5).trim(), h);
            event = "";
          }
        }
      }
    }
    h.onDone();
  } catch (e) {
    h.onError("网络中断，请重试");
  }
}
// #endif

// ---------- 微信小程序 ----------
// #ifdef MP-WEIXIN
class Utf8Decoder {
  private pending: number[] = [];

  push(bytes: Uint8Array): string {
    const all = this.pending.concat(Array.from(bytes));
    let out = "";
    let i = 0;
    while (i < all.length) {
      const b = all[i];
      let need = 0;
      if (b < 0x80) {
        need = 0;
      } else if (b >> 5 === 0b110) {
        need = 1;
      } else if (b >> 4 === 0b1110) {
        need = 2;
      } else if (b >> 3 === 0b11110) {
        need = 3;
      } else {
        i++;
        continue;
      }
      if (i + need >= all.length) break;
      if (need === 0) {
        out += String.fromCharCode(b);
      } else {
        let code = b & ((1 << (6 - need)) - 1);
        let ok = true;
        for (let k = 1; k <= need; k++) {
          const cb = all[i + k];
          if (cb >> 6 !== 0b10) {
            ok = false;
            break;
          }
          code = (code << 6) | (cb & 0x3f);
        }
        if (ok) {
          out += String.fromCharCode(code);
          i += need;
        }
      }
      i++;
    }
    this.pending = all.slice(i);
    return out;
  }
}

function mpStream(conversationId: string, text: string, h: StreamHandlers) {
  const decoder = new Utf8Decoder();
  let event = "";
  const task = uni.request({
    url: `${STREAM_URL}/api/v1/conversations/${conversationId}/messages/stream?text=${encodeURIComponent(text)}`,
    method: "GET",
    enableChunked: true,
    header: { Authorization: `Bearer ${getToken()}` },
    success: () => h.onDone(),
    fail: () => h.onError("网络异常，请重试"),
  } as unknown as UniNamespace.RequestOptions) as unknown as {
    onChunkReceived?: (cb: (res: { data: ArrayBuffer }) => void) => void;
  };

  task.onChunkReceived?.((res: { data: ArrayBuffer }) => {
    const chunk = decoder.push(new Uint8Array(res.data));
    for (const line of chunk.split("\n")) {
      if (line.startsWith("event:")) {
        event = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dispatchEvent(event, line.slice(5).trim(), h);
        event = "";
      }
    }
  });
}
// #endif

// ---------- 降级：非流式 ----------
async function fallbackStream(conversationId: string, text: string, h: StreamHandlers) {
  try {
    h.onStart?.();
    const message = await api.reply(conversationId, text);
    h.onDelta(message.content);
    h.onMeta(message);
    h.onDone();
  } catch (e) {
    h.onError("AI 回复失败，请重试");
  }
}
