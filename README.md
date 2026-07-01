# 모두의 플리
# {팀 이름}

[![codecov](https://codecov.io/gh/sb10-part04-team02/sb10-mopl-team02/graph/badge.svg?token=9J2Y96NIRM)](https://codecov.io/gh/sb10-part04-team02/sb10-mopl-team02)
### [팀 노션 페이지 링크](https://tar-sandwich-ba0.notion.site/_-04_-02-404f1e38171183698be38177e52096db?pvs=74)
## 팀원 구성
웨인 (개인 Github 링크)  
제이든 (개인 Github 링크)  
마크 (개인 Github 링크)  
데이지 (개인 Github 링크)  
제이 (개인 Github 링크)
---
## 프로젝트 소개
- 프로그래밍 교육 사이트의 Spring 백엔드 시스템 구축
- 프로젝트 기간: 2024.08.13 ~ 2024.09.03
---
## 기술 스택
- Backend: Spring Boot, Spring Security, Spring Data JPA, QueryDSL
- Database: PostgreSQL, Redis
- 공통 Tool: Git & Github, Discord
---

<details>
<summary><span style="font-size: 1.5em; font-weight: bold;">로컬 개발 환경</span></summary>
<div markdown="1">

### 사전 요구사항
- JDK 17
- Docker / Docker Compose

### 1. 환경 변수 설정
(.env 파일을 열어 필수 값 입력)
```bash
cp .env.example .env
```

### 2. 인프라 실행 (PostgreSQL · Redis)

#### 시작 - 빌드 + 백그라운드 실행
```bash
docker compose --env-file .env up -d --build
```

#### 상태 / 로그 확인
```bash
docker compose ps   # 컨테이너 상태
```
```bash
mkdir -p logs/db && docker compose logs -f db | tee logs/db/$(date +%Y%m%d_%H%M%S).log   # DB 로그 (실시간 + 저장)
```
```bash
mkdir -p logs/redis && docker compose logs -f redis | tee logs/redis/$(date +%Y%m%d_%H%M%S).log   # Redis 로그
```

#### 종료
```bash
docker compose down   # 중지 (데이터 보존)
```
```bash
docker compose down -v   # 중지 + DB 초기화 (볼륨 삭제)
```

### 3. 애플리케이션 실행

```bash
mkdir -p logs/app && set -a && source .env && set +a && ./gradlew bootRun 2>&1 | tee logs/app/$(date +%Y%m%d_%H%M%S).log    
```

프로파일을 명시적으로 지정해 실행 (dev가 기본값이라 보통 생략 가능)
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

실행 후 Swagger UI: http://localhost:8080/swagger-ui.html

### 4. 빌드

```bash
./gradlew build   # 컴파일 + 테스트 + 패키징 (최초 build 시 Git pre-commit 훅 자동 설치)
```
```bash
./gradlew clean build   # 클린 후 전체 빌드
```

> `build` 태스크는 `installGitHooks`에 의존해, 최초 빌드 시 `config/git-hooks`의 pre-commit 훅(커밋 시 `spotlessApply` 자동 실행)이 `.git/hooks`로 설치됩니다.

### 5. 테스트 & 커버리지

```bash
./gradlew test   # 전체 테스트
```
```bash
./gradlew test --tests 'com.team02.mopl.SomeTest'   # 단일 테스트 클래스
```
```bash
./gradlew test --tests 'com.team02.mopl.SomeTest.method'   # 단일 테스트 메서드
```

```bash
./gradlew jacocoTestReport   # 커버리지 HTML/XML 리포트 생성
```
```bash
./gradlew jacocoTestCoverageVerification   # 최소 커버리지(80%) 검증
```

- 커버리지 리포트: `build/reports/jacoco/test/html/index.html`

> 테스트 프로파일(`test`)은 Testcontainers로 PostgreSQL 컨테이너를 자동으로 띄워 실행합니다. Docker가 실행 중이어야 합니다.
> `test` 실행 후 `jacocoTestReport`가 자동으로 이어서 실행됩니다. QueryDSL/MapStruct 자동생성 코드는 커버리지 측정에서 제외됩니다.

### 6. 코드 품질 검사

```bash
./gradlew spotlessApply   # 코드 포맷 자동 적용 (Google Java Format)
```
```bash
./gradlew spotlessCheck   # 포맷 위반 검사 (수정 없이 확인만)
```
```bash
./gradlew spotbugsMain   # 정적 분석 (버그 패턴 탐지)
```
```bash
./gradlew check   # 전체 검증 (test + spotlessCheck + spotbugs 등 통합)
```

- SpotBugs 리포트: `build/reports/spotbugs/main.html`

</div>
</details>

---
## 팀원별 구현 기능 상세
### 박승민

(자신이 개발한 기능에 대한 사진이나 gif 파일 첨부)

- **소셜 로그인 API**
    - Google OAuth 2.0을 활용한 소셜 로그인 기능 구현
    - 로그인 후 추가 정보 입력을 위한 RESTful API 엔드포인트 개발
- **회원 추가 정보 입력 API**
    - 회원 유형(관리자, 학생)에 따른 조건부 입력 처리 API 구현

### 이승민

### 임지호

### 조성진

### 최종인

---
## 파일 구조
```markdown
com.team02.mopl
├── domain
│   ├── user
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   ├── dto
│   │   ├── mapper
|   |   └── exception
|   |
|
└── global
    ├── config
    ├── security
    ├── exception
    ├── entity
    ├── dto 
    └── util
```
---
## 구현 홈페이지
(개발한 홈페이지에 대한 링크 게시)
https://www.codeit.kr/
---
## 프로젝트 회고록
(제작한 발표자료 링크 혹은 첨부파일 첨부)
