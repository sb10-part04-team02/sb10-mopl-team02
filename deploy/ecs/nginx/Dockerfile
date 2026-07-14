# ECS 사이드카용 커스텀 Nginx 이미지.
# nginx.conf를 이미지에 포함시켜 ECS에서 파일 마운트 없이 바로 실행한다.
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
