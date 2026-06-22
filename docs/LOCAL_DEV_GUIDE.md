# 로컬 개발 환경 설정

실행 방법

1. DB + 프로그램 전체 빌드
```shell
docker compose up --build
```

2. 인프라만 따로 띄우고 싶을 때
```shell
docker compose up -d
docker compose down       # 정지
docker compose down -v    # 볼륨까지 삭제 (DB 초기화)
```
