#!/bin/sh
# Service Connect가 /etc/hosts에 주입한 app.mopl.local 항목 중 IPv4 VIP만 추출해
# nginx.conf.template의 APP_IPV4를 치환한 설정을 /tmp/nginx.conf로 렌더링한다.
# (별칭에는 IPv4·IPv6 VIP가 함께 주입되지만 Envoy가 IPv4에서만 수신해
#  IPv6로 간 요청은 즉시 502가 나므로 IPv4만 골라 써야 한다.)
set -e

APP_IPV4=$(awk '/[[:space:]]app\.mopl\.local([[:space:]]|$)/ && $1 ~ /^[0-9.]+$/ {print $1; exit}' /etc/hosts)
if [ -z "$APP_IPV4" ]; then
  echo "render-upstream: /etc/hosts에 app.mopl.local IPv4 항목이 없습니다" >&2
  exit 1
fi

export APP_IPV4
envsubst '${APP_IPV4}' < /etc/nginx/nginx.conf.template > /tmp/nginx.conf
echo "render-upstream: upstream IPv4 VIP = $APP_IPV4"
