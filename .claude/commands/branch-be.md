# 📄 브랜치 생성 커맨드

## 사용법

```
/branch-be <이슈번호> <설명>
```

예시: `/branch-be 121 register-user`

## 동작 순서

1. `$ARGUMENTS`에서 이슈번호와 설명을 파싱한다.
2. 커밋 타입을 선택하도록 사용자에게 묻는다.
3. 브랜치명을 조합하고 확인을 요청한다.
4. 확인 후 아래 순서로 명령을 실행한다.
   - `git fetch origin dev` — 최신 dev 동기화
   - `git checkout -b <브랜치명> origin/dev` — 로컬 브랜치 생성
   - `git push -u origin <브랜치명>` — 원격 브랜치 생성 및 추적 설정
5. 완료 후 생성된 브랜치명을 출력한다.

## 브랜치 명명 규칙

```
<type>/#<이슈번호>/<설명>
```

### 타입 선택 기준

| 타입 | 사용 시점 |
|------|----------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `docs` | 문서, 주석 변경 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드, 설정, 의존성 등 기타 변경 |

### 예시

```
feat/#69/content-crud-basic
feat/#26/follow-service
test/#69/content-crud-basic-test
```

## 실행 명령 예시

```bash
git fetch origin dev
git checkout -b feat/#69/content-crud-basic origin/dev
git push -u origin feat/#69/content-crud-basic
```
