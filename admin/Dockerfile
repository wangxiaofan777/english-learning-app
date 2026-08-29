# 管理后台：构建为纯静态 SPA，由独立 Nginx 托管并反代后端 API。
# 与用户端 (app/) 分离部署：独立入口、独立端口，便于防火墙/隧道收敛访问面。
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
