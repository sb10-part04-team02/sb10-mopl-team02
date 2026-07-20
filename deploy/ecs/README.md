# ECS 배포

모두의 플리(mopl) 백엔드의 AWS ECS(Fargate) 배포 산출물.
`dev` 브랜치에 push하면 CD 워크플로우(`.github/workflows/cd.yml`)가 자동으로 배포한다.

## 아키텍처

```text
Client → CloudFlare(HTTPS) → ALB(HTTPS:443)
          → nginx 서비스 (mopl-nginx-service, desired 1, :8080)
             → Service Connect (app.mopl.local:8080)
                → app 서비스 (mopl-app-service, desired 2)
                   → RDS PostgreSQL / ElastiCache Redis
                   → Confluent Kafka / S3 + CloudFront
```

- nginx와 app은 별도 ECS 서비스로 분리되어 있고, nginx가 Service Connect 별칭(`app.mopl.local`)으로 app 인스턴스 2대에 요청을 분산한다.
- 모든 태스크는 ARM64(Graviton) Fargate에서 실행된다.
- nginx 서비스는 desired 1이라 교체·장애 시 순단 가능성이 있는 단일 지점이다. 무중단이 필요해지면 nginx도 2대 이상으로 확장한다.
- 엣지 구간은 CloudFlare가 Full (Strict) 모드로 ALB의 ACM 인증서를 검증하며 종단 간 암호화된다. ALB가 TLS를 종료하고 뒤쪽 nginx로는 VPC 내부에서 HTTP로 전달한다.

## 이 디렉터리의 파일

| 파일 | 용도 |
|---|---|
| `task-definition-app.json` | app 서비스 태스크 정의 템플릿 (512 CPU / 1024 MB) |
| `task-definition-nginx.json` | nginx 서비스 태스크 정의 템플릿 (256 CPU / 512 MB) |
| `nginx/Dockerfile` | 커스텀 nginx 이미지. `nginxinc/nginx-unprivileged` 기반(non-root, listen 8080) |
| `nginx/nginx.conf.template` | nginx 설정 템플릿. upstream은 `${APP_IPV4}` 플레이스홀더 |
| `nginx/render-upstream.sh` | 컨테이너 기동 시 Service Connect IPv4 VIP를 추출해 설정을 렌더링 |
| `deploy.env.example` | 태스크 정의 템플릿에 주입할 값 목록 (실제 값 파일은 커밋 금지) |

태스크 정의는 플레이스홀더(`${...}`) 템플릿이며, 실제 값(엔드포인트·ARN 등)은
CD가 GitHub Secrets/Variables에서 `envsubst`로 주입해 등록한다.

## CD 파이프라인

`dev` push 시 자동 실행되며, 수동 실행(`workflow_dispatch`)도 가능하다. 흐름:

1. bootJar를 러너에서 네이티브 빌드 (도커 빌드는 JAR 복사만 수행)
2. app 이미지를 ARM64로 크로스빌드해 ECR push (태그: 커밋 short SHA)
3. nginx는 관련 파일(`deploy/ecs/nginx`, `task-definition-nginx.json`)이 마지막 성공 배포 이후 바뀐 커밋에서만 빌드·배포 (수동 실행 시에는 항상 배포)
4. 태스크 정의 렌더링·등록 후 서비스 업데이트, 두 서비스의 안정화를 병렬로 대기 (최대 20분)
5. 이번 런이 등록한 태스크 정의가 실제 PRIMARY로 남았는지 검증 (circuit breaker 롤백을 성공으로 오인하지 않기 위함)

- 인증은 GitHub OIDC로 배포 역할을 AssumeRole한다 (장기 액세스 키 없음).
- 실패한 배포는 ECS deployment circuit breaker가 서버 측에서 자동 롤백한다.
- 소요 시간은 nginx를 건너뛰는 평상시 커밋 기준 약 8분.

### 롤백

- 배포 실패(태스크 기동 불가 등)는 circuit breaker가 자동으로 이전 revision으로 되돌린다.
- 코드 문제로 수동 롤백이 필요하면 해당 커밋을 revert해 `dev`에 push한다 (새 배포로 이전 상태 복원).

## nginx 구성 참고

- Service Connect 별칭은 DNS가 아니라 태스크 `/etc/hosts`에 주입된 VIP로 해석된다. IPv4·IPv6 VIP가 함께 주입되지만 로컬 프록시(Envoy)는 IPv4에서만 수신하므로, 기동 시 `render-upstream.sh`가 IPv4 VIP만 추출해 설정을 렌더링한다. nginx의 `resolver`는 `/etc/hosts`를 읽지 않으므로 동적 해석으로는 대체할 수 없다.
- `/actuator/**`는 엣지에서 403으로 차단하고, ALB 헬스체크가 쓰는 `/actuator/health`만 정확 매치로 통과시킨다.
- SSE(`/api/sse`)는 버퍼링 off·long timeout, WebSocket(`/ws`)은 upgrade 헤더 처리로 분리했다.
- 프록시 연결 실패 시 재시도(`proxy_next_upstream`)로 app 태스크 교체 중 순단을 완화한다.
- `/nginx-health`는 nginx 자체 생존 확인용이며 app 상태와 결합하지 않는다 (end-to-end 확인은 ALB 헬스체크 담당).

## AWS 리소스

리전 `ap-northeast-2`, 접속 주소 `https://api.<도메인>` (CloudFlare → ALB).

| 리소스 | 이름 | 비고 |
|---|---|---|
| ECS 클러스터 | `mopl-cluster` | Fargate |
| ECS 서비스 | `mopl-app-service` | desired 2, Service Connect `app.mopl.local:8080` |
| ECS 서비스 | `mopl-nginx-service` | desired 1, ALB 타겟 등록 |
| 태스크 정의 | `mopl-app-task` / `mopl-nginx-task` | ARM64 |
| ALB | `mopl-alb` | HTTPS:443 리스너 → `mopl-nginx-tg`(8080, 헬스체크 `/actuator/health`), HTTP:80은 HTTPS로 301 리다이렉트 |
| 인증서 | ACM (`ap-northeast-2`) | `api.<도메인>` + 와일드카드, DNS 검증·자동 갱신 |
| RDS | PostgreSQL 16 | `db.t4g.micro`, 스키마는 Flyway가 관리 |
| ElastiCache | Redis OSS 7.1 | `cache.t4g.micro`, 세션·실시간 Pub/Sub |
| Kafka | Confluent Cloud | 알림 fan-out (SASL_SSL) |
| S3 + CloudFront | 파일 스토리지 | OAC로 버킷 비공개 유지 |
| 로그 그룹 | `/ecs/mopl` | 스트림 접두사 `app` / `nginx` |

### 보안 그룹

| 이름 | 역할 | 인바운드 |
|---|---|---|
| `mopl-alb-sg` | ALB | CloudFlare IPv4 대역 80/443 (관리형 접두사 목록 `cloudflare-ipv4` 참조) |
| `mopl-ecs-sg` | ECS 태스크 | `mopl-alb-sg` → 8080, self → 8080 (nginx → app) |
| `mopl-rds-sg` | RDS | `mopl-ecs-sg` → 5432 |
| `mopl-redis-sg` | Redis | `mopl-ecs-sg` → 6379 |

### IAM / Secrets

- 실행 역할 `moplEcsTaskExecutionRole`: 이미지 pull·로그·시크릿 읽기.
- 태스크 역할 `moplEcsTaskRole`: S3 업로드/삭제 최소 권한 (app 태스크만 사용).
- 비밀 값(DB 비밀번호, JWT 서명 키, 관리자 비밀번호, Kafka API 키, 메일 비밀번호)은
  Secrets Manager 참조로 태스크에 주입한다. 주입 대상 환경변수 목록은 태스크 정의 템플릿과
  `deploy.env.example`을 참고한다 (시크릿 이름·ARN은 저장소에 기재하지 않는다).

## 데이터베이스 스키마

스키마는 Flyway가 앱 기동 시 자동 마이그레이션한다 (`src/main/resources/db/migration`,
`ddl-auto=validate`). 수동 스키마 주입은 더 이상 사용하지 않는다.

## 운영 참고

- 배포 상태 확인: GitHub Actions CD 런 로그, 또는 ECS 콘솔 `mopl-cluster` 서비스 이벤트.
- 앱 로그: CloudWatch `/ecs/mopl` (접두사 `app`), nginx 접근 로그: 접두사 `nginx` (`upstreamlog` 포맷).
- nginx 이미지를 손대는 커밋은 CD가 자동으로 감지해 함께 배포하므로 수동 빌드·푸시가 필요 없다.
