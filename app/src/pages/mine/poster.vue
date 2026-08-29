<template>
  <view class="poster-page">
    <canvas
      canvas-id="poster"
      id="poster"
      class="poster-canvas"
      :style="{ width: canvasW + 'px', height: canvasH + 'px' }"
    />
    <view class="actions">
      <button class="btn-primary" @tap="saveOrPreview">保存 / 分享成绩卡</button>
      <text class="muted tip">长按图片即可保存或转发；每周更新一次战绩</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad, onReady } from "@dcloudio/uni-app";
import { api } from "../../utils/api";
import type { Profile, StatsView } from "../../utils/types";

const canvasW = 340;
const canvasH = 480;
const profile = ref<Profile | null>(null);
const stats = ref<StatsView | null>(null);

onLoad(async () => {
  const [me, s] = await Promise.all([api.me(), api.stats()]);
  profile.value = me;
  stats.value = s;
});

onReady(() => {
  // 等数据到齐再画
  const timer = setInterval(() => {
    if (profile.value && stats.value) {
      clearInterval(timer);
      draw();
    }
  }, 200);
});

function draw() {
  const ctx = uni.createCanvasContext("poster");
  const W = canvasW;
  const H = canvasH;
  const p = profile.value!;
  const s = stats.value!;

  // 背景
  ctx.setFillStyle("#0f766e");
  ctx.fillRect(0, 0, W, H);
  ctx.setFillStyle("#16a34a");
  ctx.fillRect(0, 0, W, 210);

  // 品牌行
  ctx.setFillStyle("#dcfce7");
  ctx.setFontSize(12);
  ctx.fillText("英语搭子 · AI 英语陪练", 24, 34);

  // 主标语
  ctx.setFillStyle("#ffffff");
  ctx.setFontSize(22);
  ctx.fillText("我的英语战绩", 24, 84);
  ctx.setFontSize(13);
  ctx.setFillStyle("#d1fae5");
  ctx.fillText("每天 15 分钟，在真实场景里开口说", 24, 112);

  // 用户与等级
  ctx.setFillStyle("#ffffff");
  ctx.setFontSize(17);
  ctx.fillText(`${p.nickname}`, 24, 168);
  ctx.setFontSize(12);
  ctx.fillText(`LV.${p.level} ${p.levelTitle} · ${p.cefrLevel || "A2"}`, 24, 192);

  // 数据卡
  const rows: [string, string][] = [
    ["🔥 连续打卡", `${p.streakDays} 天`],
    ["⭐ 本周经验", `+${s.weekXp}`],
    ["⏱ 累计学习", `${s.totalMinutes} 分钟`],
    ["🎙 开口对话", `${s.totalDialogs} 次`],
    ["📚 收养生词", `${s.wordsTotal} 个`],
  ];
  let y = 250;
  for (const [label, value] of rows) {
    ctx.setFillStyle("rgba(255,255,255,0.08)");
    ctx.fillRect(24, y - 22, W - 48, 40);
    ctx.setFillStyle("#a7f3d0");
    ctx.setFontSize(13);
    ctx.fillText(label, 36, y + 4);
    ctx.setFillStyle("#ffffff");
    ctx.setFontSize(16);
    ctx.fillText(value, W - 36 - ctx.measureText(value).width, y + 4);
    y += 52;
  }

  // 底部
  ctx.setFillStyle("#6ee7b7");
  ctx.setFontSize(11);
  ctx.fillText("和我一起每天开口说英语 →", 24, H - 46);
  ctx.setFillStyle("#a7f3d0");
  const date = new Date().toISOString().slice(0, 10);
  ctx.fillText(date, W - 24 - 80, H - 24);

  ctx.draw();
}

function saveOrPreview() {
  uni.canvasToTempFilePath({
    canvasId: "poster",
    success: (res) => {
      // #ifdef H5
      // H5 无相册 API，且 previewImage 对 canvas 产出的 data: URL 不弹预览层——
      // 直接触发浏览器下载，落点是浏览器的下载目录
      const link = document.createElement("a");
      link.href = res.tempFilePath;
      link.download = `talkbuddy-成绩卡-${new Date().toISOString().slice(0, 10)}.png`;
      link.click();
      uni.showToast({ title: "成绩卡已开始下载", icon: "none" });
      // #endif
      // #ifndef H5
      uni.saveImageToPhotosAlbum({
        filePath: res.tempFilePath,
        success: () => {
          uni.showToast({ title: "已保存到相册", icon: "success" });
        },
        fail: (err) => {
          const msg = String(err?.errMsg ?? "");
          if (msg.includes("auth") || msg.includes("deny")) {
            // 相册权限被拒：引导去设置页开启
            uni.showModal({
              title: "需要相册权限",
              content: "请在设置中允许保存图片到相册，再回来保存成绩卡",
              confirmText: "去设置",
              success: (m) => {
                if (m.confirm) {
                  uni.openSetting({});
                }
              },
            });
          } else {
            // 其他失败（如开发工具不支持）退回预览兜底
            uni.previewImage({ urls: [res.tempFilePath] });
          }
        },
      });
      // #endif
    },
    fail: () => {
      uni.showToast({ title: "生成失败，请截图保存", icon: "none" });
    },
  });
}
</script>

<style scoped>
.poster-page {
  min-height: 100vh;
  padding: 32rpx 0 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.poster-canvas {
  background: #0f766e;
  border-radius: 20rpx;
  box-shadow: 0 12rpx 40rpx rgba(15, 118, 110, 0.35);
}

.actions {
  margin-top: 36rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  width: 70%;
}

.tip {
  text-align: center;
}
</style>
