## Redis 검증

로컬 분산 환경에서는 `app-1`, `app-2`가 동일한 Redis 컨테이너를 사용합니다.  
Redis는 JWT refresh token 및 access token blacklist 저장소로 사용됩니다.

### Redis 컨테이너 상태 확인

```bash
docker compose -f docker-compose.distributed.yml --env-file .env ps
```

`redis` 컨테이너가 `healthy` 상태이면 정상입니다.

### Redis key 확인

전체 key 확인:

```bash
docker compose -f docker-compose.distributed.yml --env-file .env exec redis redis-cli keys '*'
```

refresh token key 확인:

```bash
docker compose -f docker-compose.distributed.yml --env-file .env exec redis redis-cli keys 'jwt:refresh:*'
```

blacklist key 확인:

```bash
docker compose -f docker-compose.distributed.yml --env-file .env exec redis redis-cli keys 'jwt:blacklist:*'
```

### app-1/app-2 Redis 공유 검증

1. app-1 직접 포트에서 로그인합니다.

```bash
curl -i -c cookies.txt http://localhost:8081/
```

```bash
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' cookies.txt | tail -1)
```

```bash
curl -i -X POST 'http://localhost:8081/api/auth/sign-in' \
  -b cookies.txt \
  -c cookies.txt \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H "X-XSRF-TOKEN: $XSRF" \
  --data-urlencode 'username=<email>' \
  --data-urlencode 'password=<password>'
```

2. Redis에 refresh token이 저장되었는지 확인합니다.

```bash
docker compose -f docker-compose.distributed.yml --env-file .env exec redis redis-cli keys 'jwt:refresh:*'
```

3. app-1에서 발급받은 access token으로 app-2 인증 API를 호출합니다.

```bash
curl -i 'http://localhost:8082/api/notifications?limit=20&sortBy=createdAt&sortDirection=DESCENDING' \
  -H 'Authorization: Bearer <access-token>'
```

`200 OK`가 반환되면 app-1에서 발급받은 토큰으로 app-2에서도 인증 흐름이 정상 동작하는 것입니다.

4. app-2 직접 포트에서 로그아웃합니다.

```bash
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' cookies.txt | tail -1)
```

```bash
curl -i -X POST 'http://localhost:8082/api/auth/sign-out' \
  -b cookies.txt \
  -c cookies.txt \
  -H 'Authorization: Bearer <access-token>' \
  -H "X-XSRF-TOKEN: $XSRF"
```

5. Redis에 blacklist key가 저장되었는지 확인합니다.

```bash
docker compose -f docker-compose.distributed.yml --env-file .env exec redis redis-cli keys 'jwt:blacklist:*'
```

다음 흐름이 확인되면 Redis 공유 검증이 완료된 것입니다.

```text
app-1 로그인 성공
-> Redis에 jwt:refresh:{userId} 저장
-> app-2 인증 API 호출 200 OK
-> app-2 로그아웃 204 No Content
-> Redis에 jwt:blacklist:{accessTokenJti} 저장
```

## Kafka 검증

로컬 분산 환경에서는 Kafka 컨테이너를 함께 실행해 Spring Boot 애플리케이션의 메시지 발행/소비 흐름을 검증합니다.  
AWS 환경에서는 로컬 Kafka 대신 Confluent Cloud Kafka로 전환할 예정입니다.

### Kafka 컨테이너 상태 확인

```bash
docker compose -f docker-compose.distributed.yml --env-file .env ps
```

`kafka` 컨테이너가 `healthy` 상태이면 정상입니다.

### Kafka topic 확인

```bash
docker compose -f docker-compose.distributed.yml --env-file .env exec kafka kafka-topics --bootstrap-server kafka:29092 --list
```

`mopl.local.test` 토픽이 없으면 생성합니다.

```bash
docker compose -f docker-compose.distributed.yml --env-file .env exec kafka kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists --topic mopl.local.test --partitions 1 --replication-factor 1
```

### Kafka smoke test 실행

Kafka smoke test는 Spring Boot 애플리케이션이 Kafka에 메시지를 발행하고, 다시 소비할 수 있는지 확인하는 용도입니다.

1. 로그인 후 access token을 준비합니다.

```bash
curl -i -c kafka-cookies.txt http://localhost:8080/
```

```bash
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' kafka-cookies.txt | tail -1)
```

```bash
curl -i -X POST 'http://localhost:8080/api/auth/sign-in' \
  -b kafka-cookies.txt \
  -c kafka-cookies.txt \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H "X-XSRF-TOKEN: $XSRF" \
  --data-urlencode 'username=<email>' \
  --data-urlencode 'password=<password>'
```

2. 로그인 응답의 `accessToken`을 사용해 smoke API를 호출합니다.

```bash
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' kafka-cookies.txt | tail -1)
```

```bash
curl -i -X POST http://localhost:8080/api/dev/kafka/smoke \
  -b kafka-cookies.txt \
  -c kafka-cookies.txt \
  -H "X-XSRF-TOKEN: $XSRF" \
  -H 'Authorization: Bearer <access-token>'
```

`204 No Content`가 반환되면 Kafka producer 호출이 성공한 것입니다.

3. app 로그에서 consumer 수신 여부를 확인합니다.

```bash
docker compose -f docker-compose.distributed.yml --env-file .env logs --tail=100 app-1 app-2
```

다음 로그가 출력되면 Kafka consumer가 메시지를 정상 수신한 것입니다.

```text
Kafka smoke message received: Kafka smoke test: ...
```

### Kafka consumer group 참고

`app-1`, `app-2`는 동일한 consumer group인 `mopl-local`을 사용합니다.  
따라서 하나의 메시지는 두 인스턴스가 모두 받는 것이 아니라, 둘 중 하나의 인스턴스에서만 소비되는 것이 정상입니다.

```text
같은 consumer group = 메시지를 인스턴스들 사이에 분산 처리
다른 consumer group = 각 group이 같은 메시지를 각각 수신
```

Kafka 검증이 완료되면 다음 흐름이 확인됩니다.

```text
POST /api/dev/kafka/smoke
-> Spring Kafka Producer가 mopl.local.test 토픽에 메시지 발행
-> Kafka Consumer가 mopl.local.test 토픽 메시지 수신
-> app 로그에 Kafka smoke message received 출력
```