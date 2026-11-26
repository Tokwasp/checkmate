# 규정 검사 일일 비용 한도

항공기 반입 검사와 촬영 검역 검사가 한국 시간 자정 기준으로 10,000원 한도를 공유한다. 앱은 첫 Knowledge Base 검색 전에 Upstash에서 요청 전체의 최대 비용을 원자적으로 예약한다. 요청이 끝나면 시도한 검색·Rerank 비용과 Bedrock 응답의 실제 토큰 사용량을 반영해 차액을 돌려준다. 호출 결과를 확인하지 못한 시도의 금액은 보수적으로 유지한다. GMS 비용은 제외한다.

- 운영 키는 `checkmate:rag-budget:YYYY-MM-DD`다. 잔액 필드 `remainingMicroKrw`는 100만으로 나누면 원화다. 한도는 새 일자 키의 첫 예약에 반영된다.
- 예약할 잔액이 부족하면 유료 AWS 호출 전에 HTTP 429를 반환한다. Redis나 입력 토큰 수 확인에 실패하면 HTTP 503으로 해당 호출을 중단한다. 토큰 또는 Rerank 분량 상한을 넘으면 HTTP 422를 반환한다.
- 생성 비용은 글로벌 Sonnet 4.6 또는 Haiku 4.5의 입력·출력 요율을 적용한다. 실제 Converse 요청에 사용하는 시스템 문구, 메시지, 도구 정의를 CountTokens에 전달한다. 토큰 계수에는 글로벌 추론 프로파일 대신 같은 기반 모델 ID를 사용한다. `RAG_BUDGET_COUNT_TOKENS_REGION` 기본값은 `us-east-1`이며, 프롬프트가 해당 리전으로 전송된다. 운영에서 모델과 리전의 지원 여부를 확인하고 IAM 역할에 `bedrock:CountTokens`를 허용한다.
- Rerank 차감액은 100개 청크당 $0.002를 적용한 보수적 추산이다. 긴 청크가 여러 과금 청크로 나뉠 수 있어 UTF-8 바이트와 질의 길이로 단위 수를 추정한다. 검색 건당 예약 금액에는 Knowledge Base의 쿼리 임베딩과 S3 Vectors 쿼리 비용을 포함해 운영 환경에서 지정한다. 원화 환산율에는 세금과 환율 변동의 여유를 포함한다. 이 한도는 앱의 추정 지출 원장으로 관리되며 AWS 청구서는 실제 사용량과 요율을 기준으로 한다.

## EC2에서 Upstash 차감 확인

CheckMate의 기존 로컬 프로필 `.env`에 아래 설정을 추가한다. Upstash 콘솔의 Connect → REST에서 URL과 쓰기 가능한 토큰을 가져온다. `1000`은 테스트용 검색 비용 가정인 $0.001이다.

```dotenv
RAG_BUDGET_ENABLED=true
UPSTASH_REDIS_REST_URL=
UPSTASH_REDIS_REST_TOKEN=
RAG_BUDGET_DAILY_LIMIT_KRW=10000
RAG_BUDGET_RETRIEVE_MAX_MICRO_USD=1000
RAG_BUDGET_TEST_API_ENABLED=true
RAG_BUDGET_TEST_API_KEY=
```

테스트 키는 `openssl rand -hex 32`로 생성할 수 있다. 아래 명령은 별도 테스트 컨테이너를 EC2의 로컬 포트 `8081`에 띄운다. 이 테스트 API는 `prod` 프로필에서 제공하지 않는다.

```bash
docker build -t checkmate:rag-budget .
docker run -d --name checkmate-budget-test \
  --env-file .env --log-driver local \
  -e SPRING_PROFILES_ACTIVE=local \
  -e SPRING_H2_CONSOLE_ENABLED=false \
  -e DEV_LOGIN_ENABLED=false \
  -p 127.0.0.1:8081:8080 checkmate:rag-budget
docker logs --tail 30 checkmate-budget-test
```

Spring 로그에 `Started CheckmateApplication`이 나오면 다음을 실행한다.

```bash
read -rs -p '테스트 API 키: ' budget_test_key
printf '\n'
for check in quarantine security; do
  curl -sS -X POST -H "X-Budget-Test-Key: $budget_test_key" \
    "http://127.0.0.1:8081/internal/test-api/rag-budget/$check"
  printf '\n'
done
unset budget_test_key
```

응답의 `redisKey`를 Upstash Data Browser에서 열고 `remainingMicroKrw`를 확인한다. 두 호출은 `checkmate:rag-budget:test:YYYY-MM-DD`라는 같은 테스트 키를 쓰고 Bedrock·GMS를 호출하지 않는다. 기본 Haiku 모델과 위 설정으로 그날 처음 테스트하면 검역 후 약 9,969.53원, 반입까지 실행하면 약 9,945.53원이 남는다.
