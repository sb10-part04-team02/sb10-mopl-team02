# 로컬 개발 환경 설정

프로그램에 필요한 인프라는 docker-compose.yml으로 구동합니다. 
Spring 프로젝트의 경우 개발 단계에서는 docker-compose.yml에 포함하지 않습니다.

인프라 구동 안내

1. 관련 인프라 띄우기 명령어
```shell
docker compose up -d 
```

2. 인프라 가동 정지 (데이터는 유지됨)
```shell
docker compose down
```

3. 인프라 완전 초기화
```shell
docker compose down -v
```

IntelliJ 로컬 환경에서 구동 시 .env 파일 내부의 환경 변수가 자동으로 들어가지 않습니다.

따라서 IntelliJ에서 EnvFile 플러그인을 설치한 후 실행 구성 설정에 들어가 EnvFile을 활성화, 
.env 파일을 잡아주어야 정상적으로 환경 변수 대입이 작동합니다.