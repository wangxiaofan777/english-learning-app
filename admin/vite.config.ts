import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// 独立管理后台：构建产物为纯静态文件；开发/预览模式下 /api 代理到本机后端
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
  preview: {
    port: 5175,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
});
