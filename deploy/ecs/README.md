# ECS 1차 배포 기록

모두의 플리(mopl) 1차 배포(단일 인스턴스, desired count=1)의 산출물과 진행 기록.
상세 절차 가이드는 [../../docs/deploy-ecs-guide.html](../../docs/deploy-ecs-guide.html) 참고.

## 목표 구조

```
Client -> CloudFlare -> ALB -> ECS Task(사이드카) -> RDS / ElastiCache
                                 ├─ nginx (:80)
                                 └─ app   (:8080)
```

- 1차 배포는 nginx + app을 같은 Task에 둔 사이드카 구성.
- 2차 확장(desired count 2+) 시 app/nginx 서비스를 분리하고 Service Connect로 전환 필요.

## 리전 / 계정

- 리전: `ap-northeast-2` (서울)
- 계정 ID: `<AWS_ACCOUNT_ID>`
- VPC: 기본 VPC (4 서브넷 / 4 AZ)
- 접속 주소: `https://api.<도메인>` (CloudFlare → ALB)

## CloudFlare (10단계)

- 도메인: 레지스트라에서 구매, 네임서버를 CloudFlare로 변경
- DNS 레코드: `api` CNAME → `<ALB DNS 이름>` (Proxied, 주황 구름)
- HTTPS: CloudFlare Universal SSL 자동 발급. 클라이언트↔CloudFlare HTTPS, CloudFlare↔ALB HTTP(80).
- 프론트는 `VITE_API_BASE_URL=https://api.<도메인>`로 설정 필요.

## 생성된 리소스

### 보안 그룹
| 이름 | 역할 | 인바운드 |
|---|---|---|
| `mopl-alb-sg` | ALB | 인터넷 80/443 |
| `mopl-ecs-sg` | ECS Task | `mopl-alb-sg` → 8080 |
| `mopl-rds-sg` | RDS | `mopl-ecs-sg` → 5432 |
| `mopl-redis-sg` | Redis | `mopl-ecs-sg` → 6379 |

### RDS PostgreSQL
- 엔드포인트: `<RDS 엔드포인트>:5432`
- DB 이름: `mopl` / 사용자: `mopl`
- 엔진: PostgreSQL 16, `db.t4g.micro`, 단일 AZ
- 스키마: `src/main/resources/01_schema_v8.sql` 수동 주입 완료 (테이블 14개). `ddl-auto=validate`.
- 후속: Flyway 도입 예정 (이슈 #363)

### ElastiCache Redis
- 엔진: Redis OSS 7.1, `cache.t4g.micro`, 클러스터 모드 비활성화, 복제본 0
- 전송 중 암호화 OFF (앱이 평문 접속), 보안그룹 `mopl-redis-sg`
- 엔드포인트: `<ElastiCache 엔드포인트>:6379` → `REDIS_HOST`

### Confluent Kafka — 1차 배포에서 스킵
- 앱은 Kafka 브로커 없이도 정상 기동함(검증 완료). 팔로우 알림만 미동작.
- SASL_SSL 설정 추가 후속: 이슈 #365

### ECR 이미지 (멀티아키 amd64 + arm64)
- 앱: `<ECR_REGISTRY>/mopl-app:<tag>`
- Nginx: `<ECR_REGISTRY>/mopl-nginx:<tag>`
- `<ECR_REGISTRY>` = `<AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com`

## 이 디렉터리의 파일

- `nginx/Dockerfile` — 사이드카용 커스텀 nginx 이미지
- `nginx/nginx.conf` — upstream을 `127.0.0.1:8080`으로. SSE/WebSocket/body size는 로컬과 동일.

### nginx 이미지 빌드/푸시 명령

```bash
REGISTRY=<AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com
aws ecr get-login-password --region ap-northeast-2 --profile <profile> \
  | docker login --username AWS --password-stdin $REGISTRY
cd deploy/ecs/nginx
docker buildx build --platform linux/amd64,linux/arm64 \
  -t $REGISTRY/mopl-nginx:v1 -t $REGISTRY/mopl-nginx:latest --push .
```

## Task Definition 환경변수

정의 파일 `deploy/ecs/task-definition.json`은 플레이스홀더(`${...}`) 템플릿이며,
실제 값(엔드포인트·ARN·계정 ID 등)은 배포 시점에 주입한다.

평문 환경변수(비밀 아님): `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`, `DB_URL`, `DB_USERNAME`,
`REDIS_HOST`, `REDIS_PORT`, `ADMIN_EMAIL`, `ADMIN_NAME`, `INGESTION_SCHEDULER_ENABLED`,
`AWS_S3_BUCKET`, `AWS_S3_REGION`, `AWS_S3_BASE_URL`

Secrets Manager 참조(비밀):
```
DB_PASSWORD     -> mopl/db-password
JWT_SECRET_KEY  -> mopl/jwt-secret
ADMIN_PASSWORD  -> mopl/admin-password
```

## ECS / ALB 리소스

### IAM
- Task 실행 역할: `moplEcsTaskExecutionRole`
  - `AmazonECSTaskExecutionRolePolicy`(관리형) + `moplReadSecrets`(인라인, 시크릿 3개만 읽기)
- Task 역할: 없음 (앱이 AWS SDK 미사용)

### Secrets Manager
- `mopl/db-password` → `DB_PASSWORD`
- `mopl/jwt-secret` → `JWT_SECRET_KEY`
- `mopl/admin-password` → `ADMIN_PASSWORD`

### ECS
- 클러스터: `mopl-cluster` (Fargate)
- Task Definition: `mopl-task` (ARM64, 512 CPU / 1024 MB, nginx+app 사이드카)
  - 정의 파일: `deploy/ecs/task-definition.json`
- 서비스: `mopl-service` (desired count=1)
- 로그 그룹: `/ecs/mopl` (스트림 접두사 app / nginx)

### ALB
- ALB: `mopl-alb` (internet-facing)
- DNS: `<ALB DNS 이름>`
- 리스너: HTTP:80 → 타겟 그룹
- 타겟 그룹: `mopl-ecs-tg` (IP 타입, HTTP:80, 헬스체크 `/actuator/health`)
- 서비스가 nginx 컨테이너 80포트를 타겟 그룹에 자동 등록
- SG 보강: `mopl-ecs-sg`에 `mopl-alb-sg`→80 인바운드 추가 (ALB→nginx)

## 진행 상태

- [x] 1단계 VPC / 보안그룹
- [x] 2단계 RDS + 스키마 주입
- [x] 3단계 ElastiCache Redis
- [x] 4단계 Confluent — 스킵 (이슈 #365)
- [x] 5단계 ECR 이미지 (app + nginx 멀티아키)
- [x] 6단계 IAM 역할 / Secrets / 클러스터 / Task Definition / 서비스
- [x] 7단계 서비스 기동 확인 (태스크 RUNNING/HEALTHY, 타겟 healthy, ALB 200)
- [x] 8단계 (사이드카에 통합됨)
- [x] 9단계 ALB / 타겟 그룹 / 리스너
- [x] 10단계 CloudFlare 도메인 / HTTPS (`api.<도메인>`)

## 기동 확인 결과 (7단계)

ALB DNS로 직접 확인:
- `GET /actuator/health` → `200 {"status":"UP"}`
- `GET /` → `200`
- `GET /api/contents` → `401` (인증 요구, 정상)

app 컨테이너 HEALTHY, nginx RUNNING, 타겟 그룹 `healthy` 등록 확인.

## 후속 이슈

- #363 Flyway 도입 (스키마 마이그레이션 자동화)
- #365 Confluent SASL_SSL 설정 (팔로우 알림 Kafka 연동)
- IAM 최소 권한 조정 (배포용 임시 AdministratorAccess → 최소 권한)
