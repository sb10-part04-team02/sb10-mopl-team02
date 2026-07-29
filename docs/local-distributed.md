# 로컬 분산 환경 실행 방법

## 구성

Docker Compose로 로컬에서 다음 컨테이너를 실행합니다.

- `nginx`: 로컬 진입점, `localhost:8080`
- `app-1`: Spring Boot 인스턴스 1, 직접 접근 `localhost:8081`
- `app-2`: Spring Boot 인스턴스 2, 직접 접근 `localhost:8082`
- `db`: PostgreSQL
- `redis`: Redis
- `kafka`: Kafka broker, 로컬 접근 `localhost:9092`

요청 흐름은 다음과 같습니다.

```text
Client -> localhost:8080 -> Nginx -> app-1 또는 app-2
```

## 실행 전 준비

`.env.example`을 복사해 `.env` 파일을 생성하고, 로컬 환경에 맞는 값을 입력합니다.

```bash
cp .env.example .env
```

필수 환경변수는 다음과 같습니다.

## AWS 배포 시 환경변수 매핑

로컬 `.env`에는 Docker Compose 실행에 필요한 최소값만 작성합니다.
일부 값은 `docker-compose.distributed.yml`에서 컨테이너 환경변수로 직접 주입합니다.

AWS ECS 배포 시에는 아래 값들을 Task Definition의 environment 또는 secrets로 매핑해야 합니다.

### 로컬 `.env`에 필요한 값

- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET_KEY`
- `ADMIN_EMAIL`
- `ADMIN_NAME`
- `ADMIN_PASSWORD`

### AWS ECS에서 매핑해야 하는 값

- `DB_URL`
    - 로컬: `jdbc:postgresql://db:5432/${DB_NAME:-mopl}`
    - AWS: RDS PostgreSQL JDBC URL

- `DB_USERNAME`
    - AWS: RDS 사용자명

- `DB_PASSWORD`
    - AWS: RDS 비밀번호
    - Secrets Manager 또는 ECS Secret 권장

- `REDIS_HOST`
    - 로컬: `redis`
    - AWS: ElastiCache Redis endpoint

- `REDIS_PORT`
    - 로컬: `6379`
    - AWS: ElastiCache Redis port

- `KAFKA_BOOTSTRAP_SERVERS`
    - 로컬: `kafka:29092`
    - AWS/운영: Confluent Cloud bootstrap server

- `KAFKA_CONSUMER_GROUP_ID`
    - 로컬: `mopl-local`
    - AWS/운영: 환경별 consumer group id

- `TMDB_ACCESS_TOKEN`
    - 영화/드라마 콘텐츠 수집용 TMDB API access token
    - 운영 환경에서는 ECS Secret 또는 Secrets Manager 관리 권장
    - 수집 기능을 사용하지 않는 환경에서는 비워둘 수 있음

- `SPORTSDB_API_KEY`
    - 스포츠 콘텐츠 수집용 The Sports DB API key
    - 운영 환경에서는 ECS Secret 또는 Secrets Manager 관리 권장
    - 수집 기능을 사용하지 않는 환경에서는 비워둘 수 있음

- `JWT_SECRET_KEY`
    - Secrets Manager 또는 ECS Secret 권장

- `ADMIN_EMAIL`
- `ADMIN_NAME`
- `ADMIN_PASSWORD`
    - 초기 관리자 계정 설정
    - `ADMIN_PASSWORD`는 secret으로 관리 권장

`.env` 파일은 비밀값을 포함할 수 있으므로 Git에 커밋하지 않습니다.

## 실행

```bash
docker compose -f docker-compose.distributed.yml --env-file .env up -d --build
```

## 상태 확인

```bash
docker compose -f docker-compose.distributed.yml --env-file .env ps
```

`app-1`, `app-2`, `db`, `redis`, `kafka`가 `healthy` 또는 `Up` 상태이고, `nginx`가 `Up` 상태이면 정상입니다.

## 접속 주소

```text
http://localhost:8080  # Nginx 경유
http://localhost:8081  # app-1 직접 접근
http://localhost:8082  # app-2 직접 접근
```

일반 사용자는 `http://localhost:8080`으로 접속합니다.  
`8081`, `8082`는 각 Spring Boot 인스턴스를 직접 확인할 때 사용합니다.

## 요청 분산 확인

Nginx를 경유해 health check 요청을 여러 번 보냅니다.

```bash
for i in {1..10}; do curl -s http://localhost:8080/actuator/health; echo; done
```

Nginx 로그를 확인합니다.

```bash
docker compose -f docker-compose.distributed.yml --env-file .env logs nginx
```

로그에서 `upstream` 값이 서로 다른 주소로 찍히면 요청 분산이 정상 동작한 것입니다.

예시:

```text
GET /actuator/health ... upstream=172.20.0.4:8080
GET /actuator/health ... upstream=172.20.0.5:8080
```

## 종료

컨테이너만 종료합니다.

```bash
docker compose -f docker-compose.distributed.yml --env-file .env down
```

컨테이너와 볼륨을 함께 삭제합니다.

```bash
docker compose -f docker-compose.distributed.yml --env-file .env down -v
```

`down -v`를 사용하면 PostgreSQL/Redis 볼륨 데이터도 삭제됩니다.  
일반적인 종료는 `down`만 사용합니다.

- [로컬 Redis/Kafka 검증 방법](local-redis-kafka.md)
