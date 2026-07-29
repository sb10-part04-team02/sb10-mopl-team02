# ECS nginx 전용 이미지 (2차 배포: app 서비스와 분리).
# 설정 템플릿을 이미지에 포함시키고, 기동 시 render-upstream.sh가 Service Connect
# IPv4 VIP를 치환한 /tmp/nginx.conf를 생성해 그 설정으로 실행한다.
# D3: nginxinc/nginx-unprivileged로 non-root 실행, listen 8080.
FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY nginx.conf.template /etc/nginx/nginx.conf.template
COPY --chmod=755 render-upstream.sh /docker-entrypoint.d/05-render-upstream.sh

EXPOSE 8080

CMD ["nginx", "-c", "/tmp/nginx.conf", "-g", "daemon off;"]
