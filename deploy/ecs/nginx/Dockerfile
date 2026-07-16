# ECS nginx 전용 이미지 (2차 배포: app 서비스와 분리).
# nginx.conf를 이미지에 포함시켜 ECS에서 파일 마운트 없이 바로 실행한다.
# D3: nginxinc/nginx-unprivileged로 non-root 실행, listen 8080.
FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 8080
