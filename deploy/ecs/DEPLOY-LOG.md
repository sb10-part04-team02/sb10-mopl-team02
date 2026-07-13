# ECS 1차 배포 작업 이력

모두의 플리(mopl) 1차 배포(단일 인스턴스, desired count=1)를 진행하며 수행한 작업과 그 배경·판단을 시간순으로 기록한다. 리소스 값 요약은 [README.md](./README.md), 상세 절차 가이드는 [../../docs/deploy-ecs-guide.html](../../docs/deploy-ecs-guide.html) 참고.

- 리전: `ap-northeast-2` (서울)
- 계정 ID: `882321772989`
- 브랜치: `chore/#275/ecs-1st-deploy` (이슈 #275)
- 목표 구조: `Client -> CloudFlare -> ALB -> ECS Task(nginx+app 사이드카) -> RDS / ElastiCache`

---

## 0. 계정 / 권한 준비 (IAM)

- MFA 강제 정책 `Force-MFA-Self-Manage` 생성.
  - 인수인계로 받은 JSON을 붙여넣을 때 `*` 와일드카드가 마크다운 렌더링으로 증발해 `Missing ARN` 에러 발생 → `*`를 복원해 해결.
- 배포 작업용 CLI 사용자 확보 과정에서 문제 발견:
  - 로컬 aws CLI가 이전 프로젝트 사용자 `discodeit`으로 인증돼 있어 ECR 등 권한 전무.
  - 루트 계정으로 이 프로젝트용 IAM 사용자 `mopl-jiho` 생성, 임시 `AdministratorAccess` 부여, 액세스 키 발급.
  - `aws configure --profile mopl`로 CLI 등록. 이후 모든 aws 명령은 `--profile mopl` 사용.
  - `aws login`(SSO) 방식은 IAM Identity Center 설정이 선행돼야 해서 이번엔 액세스 키 방식 채택.
- 후속: 배포 완료 후 `AdministratorAccess`를 최소 권한으로 조정 필요.

## 1. VPC / 보안 그룹

- 기본 VPC(`vpc-0a10a5fb4abe8967a`, 172.31.0.0/16, 4서브넷/4AZ)를 그대로 사용. 신규 VPC/서브넷/NAT 구성은 1차 배포에 과하다고 판단.
- 보안 그룹 4개를 역할별로 분리 생성. 인바운드 소스를 IP가 아닌 "다른 보안 그룹"으로 지정해, 태스크가 새로 떠도 규칙 수정이 불필요하도록 구성.

| 이름 | 인바운드 소스 | 포트 |
|---|---|---|
| `mopl-alb-sg` | 인터넷 `0.0.0.0/0` | 80, 443 |
| `mopl-ecs-sg` | `mopl-alb-sg` | 8080 |
| `mopl-rds-sg` | `mopl-ecs-sg` | 5432 |
| `mopl-redis-sg` | `mopl-ecs-sg` | 6379 |

- 이름 접두어를 `sg-`로 하려다 AWS가 예약 접두어로 거부 → `mopl-*-sg` 형태로 변경.

## 2. RDS PostgreSQL

- 엔진 PostgreSQL 16.10(로컬 `postgres:16`과 메이저 일치), `db.t4g.micro`(Graviton, 저렴), 단일 AZ.
- 초기 DB 이름 `mopl`, 마스터 사용자 `mopl`. VPC/보안그룹 `mopl-rds-sg`, 퍼블릭 액세스는 스키마 주입 위해 임시로 열었다가 원복.
- 스토리지 gp2 20GB, 자동 조정 상한 주의(요금 폭탄 방지).
- 엔드포인트: `mopl-db.cb8u426m4qh3.ap-northeast-2.rds.amazonaws.com:5432`

### 스키마 주입 (중요)
- 앱은 `ddl-auto=validate`라 스키마가 미리 있어야 기동됨. 반영 수단이 없어 `01_schema_v8.sql`을 psql로 수동 주입.
- 결과: `CREATE COLLATION`(ko_icu ICU) 1 + `CREATE TABLE` 14 + `CREATE INDEX` 13, 에러 없이 완료. 테이블 14개 확인.
- 스키마 SQL이 main/test 양쪽에 중복 관리되는 문제 확인 → Flyway 도입 후속 이슈 #363 생성.
- 보안 사고: psql 접속 시 `PGPASSWORD='...'`를 명령줄에 넣어 실행해 비밀번호가 대화에 노출됨. 해당 암호는 유출 간주하고 교체 권고. 이후 자격증명은 명령줄 평문 금지.

## 3. ElastiCache Redis

- Redis OSS 7.1, `cache.t4g.micro`, 클러스터 모드 비활성화, 복제본 0(→ 다중 AZ·자동 장애 조치 자동 비활성).
- 앱 `RedisConfig`가 host/port만 쓰고 TLS/AUTH 없음 → 전송 중 암호화 OFF(필수), 액세스 제어 없음, 보안그룹 `mopl-redis-sg`.
- 용도: JWT refresh token / access token blacklist. 유실돼도 재로그인으로 복구 가능해 백업/복제 불필요.
- 엔드포인트: (생성 후 README에 기입) → `REDIS_HOST`

## 4. Confluent Cloud Kafka — 1차 배포에서 스킵

- 최신 dev를 pull해 재확인한 결과, #343로 팔로우 알림(`USER_FOLLOWED`)이 Kafka produce/consume에 연결됨. Producer/Consumer가 `@Profile` 없이 prod에서도 동작.
- 앱 설정에 SASL/SSL이 없어 Confluent(SASL_SSL 필수)에 붙으려면 코드 변경 + 팀 PR 필요.
- 검증: prod 프로파일 + Kafka 브로커 부재 상태를 로컬 Docker(Postgres+Redis)로 재현.
  - 결과: 앱 정상 기동(`Started MoplApplication`), 프로세스 생존, `/actuator/health` = `{"status":"UP"}`.
  - 컨슈머가 브로커 접속 실패를 WARN으로 ~1초 간격 재시도. 팔로우 알림만 미동작, 그 외 기능은 정상.
- 결론: 1차 배포는 Confluent 없이 진행 가능. Task Definition에서 `KAFKA_BOOTSTRAP_SERVERS`는 넘기지 않거나 기본값 사용.
- 후속: SASL_SSL 설정 + Confluent 구성 이슈 #365 생성.

## 5. ECR 이미지 (멀티아키)

- 아키텍처 결정: 맥이 arm64라 그냥 빌드하면 ARM 이미지. Fargate와 아키텍처가 반드시 일치해야 함.
  - 멀티아키(amd64 + arm64) 이미지로 빌드해, Fargate를 어느 쪽으로 만들든 대응 + 향후 x86 러너 CI 전환에도 대비.
- `docker buildx`로 `mopl-builder` 빌더 생성, `--platform linux/amd64,linux/arm64 --push`.
- 앱 이미지: 최신 dev(`edc2ae3`) worktree에서 빌드.
  - `882321772989.dkr.ecr.ap-northeast-2.amazonaws.com/mopl-app:v1` (+ latest). OCI image index로 amd64/arm64 포함 확인.
- Nginx 이미지: 사이드카 방식 채택에 따라 `deploy/ecs/nginx/`의 커스텀 이미지 빌드.
  - `882321772989.dkr.ecr.ap-northeast-2.amazonaws.com/mopl-nginx:v1` (+ latest).

### 사이드카 방식 채택
- 앱/Nginx 배치: 같은 Task에 nginx + app 두 컨테이너(사이드카). nginx는 `127.0.0.1:8080`으로 app 호출.
- 이유: Service Connect/Cloud Map 없이 단순. `nginx.conf` upstream만 localhost로 변경.
- 주의: 2차 배포(desired count 2+)에서는 app/nginx를 독립 스케일할 수 없음 → 서비스 분리 + Service Connect 전환 필요.

## 6. Secrets / IAM / ECS

- Secrets Manager에 비밀 3개를 콘솔에서 일반 텍스트(단일 문자열)로 저장. 키/값(JSON) 대신 순수 문자열이라 Task Definition에서 ARN만 참조하면 값이 그대로 환경변수로 주입됨.
  - `mopl/db-password`, `mopl/jwt-secret`, `mopl/admin-password`. 암호화 키는 기본 `aws/secretsmanager`.
  - JWT 키는 `openssl rand -base64 32` 출력을 통째로 사용. 앱은 이 문자열을 Base64 디코딩 없이 UTF-8 바이트 그대로 HS256 서명 키로 씀(`JwtTokenProvider`). `0/`로 시작해도 무방.
- Task 실행 역할 `moplEcsTaskExecutionRole` 생성(신뢰: `ecs-tasks.amazonaws.com`).
  - `AmazonECSTaskExecutionRolePolicy`(관리형) + `moplReadSecrets`(인라인, 위 시크릿 3개만 `GetSecretValue`).
  - IAM 정책 부여는 auto mode에서 차단돼 사용자가 CLI로 직접 실행.
- Task 역할: 없음. 앱이 AWS SDK 미사용(파일은 컨테이너 로컬 디스크 저장). → 파일 유실 이슈 후속 필요(S3/EFS).
- CloudWatch 로그 그룹 `/ecs/mopl` (스트림 접두사 app/nginx).
- Task Definition `mopl-task:1` 등록 (`deploy/ecs/task-definition.json`).
  - Fargate, ARM64(Graviton, RDS/Redis t4g와 일관·저렴), 512 CPU / 1024 MB, `networkMode=awsvpc`.
  - 컨테이너 2개: `app`(8080, 헬스체크 `/actuator/health` UP, startPeriod 90s) + `nginx`(80, `dependsOn app HEALTHY`).
  - 환경변수: prod 프로파일, DB/Redis 접속, `INGESTION_SCHEDULER_ENABLED=false`(수집 스케줄러 끔). 비밀 3개는 `secrets`로 주입.
- 클러스터 `mopl-cluster`(Fargate), 서비스 `mopl-service`(desired=1, 퍼블릭 서브넷+퍼블릭 IP, `mopl-ecs-sg`).
  - 퍼블릭 IP 필요: NAT 없이 ECR pull·Secrets 읽기·외부 API 접근하려면 퍼블릭 서브넷+공인 IP.

## 9. ALB

- `mopl-alb`(internet-facing, `mopl-alb-sg`, 4 AZ 서브넷). DNS `mopl-alb-69451351.ap-northeast-2.elb.amazonaws.com`.
- 타겟 그룹 `mopl-ecs-tg`(IP 타입, HTTP:80, 헬스체크 `/actuator/health` 200).
- 리스너 HTTP:80 → 타겟 그룹. HTTPS는 CloudFlare(10단계)에서 처리.
- SG 포트 불일치 해결: ALB는 nginx의 80으로 보내는데 `mopl-ecs-sg`엔 8080만 열려 있었음 → `mopl-alb-sg`→80 인바운드 추가.
- 서비스가 nginx 컨테이너 80포트를 타겟 그룹에 자동 등록.

## 7. 기동 확인

- 태스크 기동: PENDING(이미지 pull) → RUNNING, app HEALTHY, nginx RUNNING. 타겟 그룹 `healthy` 등록.
- ALB DNS로 인터넷 접속 확인:
  - `GET /actuator/health` → `200 {"status":"UP"}` (전체 경로 정상, DB·Redis 연결 정상)
  - `GET /` → `200`, `GET /api/contents` → `401`(인증 요구, 정상)
- 결론: 1차 배포 앱이 인터넷에서 정상 동작. 남은 건 CloudFlare 도메인/HTTPS(10단계)뿐.

## 10. CloudFlare 도메인 / HTTPS

- 도메인 `mopl2.cloud`를 가비아에서 구매(첫해 2,750원). CloudFlare Registrar는 국제 도메인만 팔아 `.kr`이 필요없어 가비아 선택. 안전잠금은 네임서버 변경을 막으므로 구매 시 끔.
- CloudFlare에 사이트 추가(Free) → 네임서버 2개(`alberto.ns.cloudflare.com`, `dee.ns.cloudflare.com`) 발급.
- 가비아 네임서버를 위 2개로 교체 → 전파 확인(약 3분).
- DNS 레코드: `api` CNAME → ALB DNS, Proxied(주황 구름). 주황 구름이어야 CloudFlare가 HTTPS를 대신 처리.
- Universal SSL 인증서 자동 발급(약 2분). 발급 전엔 HTTPS 핸드셰이크 실패(정상 초기 상태), HTTP는 즉시 동작.
- 암호화 모드: CloudFlare↔ALB는 HTTP(80)이므로 Flexible 필요. 기본값으로 200 통과 확인.
- 최종: `https://api.mopl2.cloud/actuator/health` → 200 UP.

### 로그인 / 리프레시 토큰 문제 진단 (배포 중 발견)

- 증상: HTTP(ALB 직접 접속)에서 프론트 로그인 안 되고, 새로고침 시 인증 풀림.
- 원인: 리프레시 토큰 쿠키가 `Secure`(`JwtUtils.java`)라 HTTPS에서만 저장/전송됨. HTTP 접속이라 브라우저가 `REFRESH_TOKEN` 쿠키를 버림 → 로그인 상태 유지 안 됨, `/api/auth/refresh` 실패 → 프론트 자동 로그아웃.
- 배포 버그 아님. 코드는 정상(프로덕션 HTTPS 전제). 로컬은 어드민 계정 + `localhost`(브라우저가 Secure 쿠키를 HTTP에서도 허용)라 안 드러남.
- 해결: CloudFlare HTTPS 적용으로 자동 해결. HTTPS에서 회원가입/로그인/리프레시 전 과정 검증 완료(REFRESH_TOKEN Secure 쿠키 정상 저장, 리프레시 토큰 rotation 동작).
- 참고: 로그인 파라미터명은 `username`(Spring formLogin 기본값). 프론트도 `username`으로 이메일을 보냄(정상). `email`로 보내면 실패.
- 참고: 프론트/API가 같은 루트 도메인이어야 `SameSite=Lax` 쿠키가 실림. `api.*` 서브도메인 구성이라 프론트를 같은 루트 도메인에 배포하면 코드 변경 불필요.

## 진행 상태

- [x] 0. IAM 사용자/권한 (`mopl-jiho`, 임시 Admin)
- [x] 1. VPC / 보안그룹 4개
- [x] 2. RDS + 스키마 주입
- [x] 3. ElastiCache Redis
- [x] 4. Confluent — 스킵 (이슈 #365)
- [x] 5. ECR 이미지 (app + nginx 멀티아키)
- [x] 6. IAM 역할 / Secrets / ECS 클러스터 / Task Definition / 서비스
- [x] 7. 서비스 기동 확인 (`/actuator/health` UP, ALB 200)
- [x] 9. ALB 연결
- [x] 10. CloudFlare 도메인 / HTTPS (`https://api.mopl2.cloud`)

## 후속 이슈

- #363 Flyway 도입 (스키마 마이그레이션 자동화)
- #365 Confluent SASL_SSL 설정 (팔로우 알림 Kafka 연동)
- IAM 최소 권한 조정 (임시 AdministratorAccess → 최소 권한)
- 업로드 파일 영속화: 현재 컨테이너 로컬 디스크 저장이라 태스크 재시작 시 유실. S3/EFS 전환 필요

## 다음 작업

- Secrets Manager에 비밀 3개 저장: `mopl/db-password`, `mopl/jwt-secret`, `mopl/admin-password`
- ECS 클러스터(Fargate) 생성 → Task Definition(nginx+app 사이드카) → 서비스(desired count=1)
