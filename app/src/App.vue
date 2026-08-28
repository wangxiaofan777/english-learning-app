<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";

onLaunch(() => {
  console.log("Lingo launched");
});
</script>

<style>
page {
  background: #f6f7f9;
  color: #1f2937;
  font-size: 28rpx;
}

/* ---------- 通用组件样式 ---------- */
.card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin: 0 24rpx 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, 0.05);
}

.btn-primary {
  background: #16a34a;
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 32rpx;
  font-weight: 600;
  padding: 24rpx 0;
  text-align: center;
}

.btn-primary.disabled {
  background: #a7d7b9;
}

.btn-ghost {
  background: #ffffff;
  color: #16a34a;
  border: 2rpx solid #16a34a;
  border-radius: 999rpx;
  font-size: 30rpx;
  padding: 18rpx 40rpx;
  text-align: center;
}

.chip {
  display: inline-block;
  background: #ecfdf3;
  color: #15803d;
  border-radius: 999rpx;
  font-size: 22rpx;
  padding: 6rpx 20rpx;
  margin-right: 12rpx;
}

.chip--gray {
  background: #f3f4f6;
  color: #6b7280;
}

.chip--amber {
  background: #fef3c7;
  color: #b45309;
}

.title-lg {
  font-size: 40rpx;
  font-weight: 700;
  color: #111827;
}

.muted {
  color: #9ca3af;
  font-size: 24rpx;
}

.section-gap {
  height: 24rpx;
}
</style>
