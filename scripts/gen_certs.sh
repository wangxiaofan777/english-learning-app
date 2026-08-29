#!/usr/bin/env bash
# 生成自签名 TLS 证书到 ./certs（仅供内网/测试使用）。
# 正式上线：用 CA 签发证书替换 ./certs/fullchain.pem 与 ./certs/privkey.pem 后重启 web 容器。
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p certs
if [ -f certs/fullchain.pem ] && [ -f certs/privkey.pem ]; then
  echo "certs/fullchain.pem 已存在，跳过生成（如需重新生成请先删除 ./certs）"
  exit 0
fi

DOMAIN="${CERT_DOMAIN:-localhost}"
openssl req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes \
  -keyout certs/privkey.pem -out certs/fullchain.pem \
  -subj "/CN=${DOMAIN}" \
  -addext "subjectAltName=DNS:${DOMAIN},DNS:localhost,IP:127.0.0.1"

echo "已生成自签名证书（CN=${DOMAIN}，10 年有效期）→ ./certs"
echo "浏览器访问会出现不受信提示，属预期；对外服务请替换为正式证书。"
