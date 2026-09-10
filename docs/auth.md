# JWT 인증 가이드

Spring Security 없이 서블릿 필터로 구현한 JWT 인증입니다. Access Token 하나만 사용하고(만료 1일), Refresh Token과 서버 측 로그아웃은 없습니다.

## 요청 흐름

```
클라이언트
  │  Authorization: Bearer {accessToken}
  ▼
JwtAuthenticationFilter  (/api/* 전체, 화이트리스트 제외)
  │  토큰 없음/위조/만료 → 여기서 401 JSON 응답 후 종료
  │  검증 성공 → request attribute에 MemberPrincipal(memberId, role) 저장
  ▼
DispatcherServlet
  ▼
LoginMemberArgumentResolver  (@LoginMember 파라미터에 MemberPrincipal 주입)
  ▼
Controller
```

## 인증이 필요한 API 만들기 (모든 백엔드 팀원)

`/api/` 아래 경로는 별도 설정 없이 자동으로 인증이 걸립니다. 요청한 회원이 누구인지는 파라미터로 받습니다.

```java
@GetMapping("/api/v1/trips")
public ResponseEntity<ApiResponse<List<TripResponse>>> myTrips(@LoginMember MemberPrincipal principal) {
    return ResponseEntity.ok(ApiResponse.success(tripService.findAll(principal.memberId())));
}
```

- `MemberPrincipal`에는 `memberId()`와 `role()`만 있습니다. 닉네임 등 다른 정보가 필요하면 `MemberRepository.findById(principal.memberId())`로 조회하세요.
- `@LoginMember`는 필터를 거친 요청에서만 값이 채워집니다. 화이트리스트 경로나 `/api/` 밖에 붙이면 `IllegalStateException`(500)이 납니다. 인증 실패가 아니라 코드 버그이니 그대로 두었습니다.
- 인증이 필요 없는 공개 API를 `/api/` 아래에 두려면 `AuthConfig.WHITELIST`에 경로를 추가하세요.

### 401 응답 형태

```json
{"status": 401, "message": "인증이 필요합니다.", "data": null}
```

메시지는 상황별로 셋입니다: `인증이 필요합니다.`(헤더 없음/Bearer 아님), `유효하지 않은 토큰입니다.`(위조·형식 오류), `만료된 토큰입니다.`

## 로컬 실행 준비

1. `backend/.env.example`을 복사해 `backend/.env`를 만듭니다. 이 파일은 gitignore 대상입니다.
2. `JWT_SECRET`을 채웁니다. 32바이트 이상이어야 하며, 미달이면 부팅 시 `WeakKeyException`으로 실패합니다.
   ```powershell
   $b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Fill($b); ($b | % { $_.ToString('x2') }) -join ''
   ```
3. `DEV_LOGIN_ENABLED=true`로 두면 개발용 로그인이 열립니다. 운영에서는 비우거나 `false`.
4. `GOOGLE_CLIENT_ID`에 구글 "웹 애플리케이션" 클라이언트 ID를 넣습니다(프론트 `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`와 동일 값). 구글 로그인 응답의 `aud` 검증에 씁니다.

`.env`는 `springboot4-dotenv` 라이브러리가 읽습니다. 원래 있던 `spring-dotenv 4.0.0`은 Spring Boot 3 전용으로, Boot 4에서는 아무 에러 없이 `.env`를 무시하므로 교체했습니다.

## 개발용 로그인 (소셜 로그인 완성 전 테스트용)

```http
POST /api/v1/auth/dev-login
Content-Type: application/json

{"email": "dev@test.com"}
```
```json
{"status": 200, "message": "성공", "data": {"accessToken": "eyJ..."}}
```

해당 이메일의 회원이 없으면 만들고(닉네임은 `@` 앞부분, provider는 null), 있으면 재사용합니다. `auth.dev-login.enabled=true`일 때만 컨트롤러 빈이 생성되므로 꺼져 있으면 404입니다.

받은 토큰으로 확인:
```http
GET /api/v1/auth/me
Authorization: Bearer eyJ...
```

## 소셜 로그인

### 구글 (구현됨)

안드로이드가 구글 인증으로 받은 ID 토큰을 서버로 넘기면, 서버가 토큰에서 이메일·닉네임·프로필을 꺼내
회원을 찾거나 새로 만들고 우리 서비스의 accessToken을 발급합니다.

```http
POST /api/v1/auth/google
Content-Type: application/json

{"idToken": "eyJ..."}
```
```json
{"status": 200, "message": "성공", "data": {
  "accessToken": "eyJ...",
  "isNewUser": true,
  "member": {"id": 1, "email": "...", "nickname": "...", "profileImageUrl": "...", "provider": "GOOGLE", "role": "USER"}
}}
```

- `/api/v1/auth/google` 는 `AuthConfig.WHITELIST`에 등록돼 있어 토큰 없이 호출됩니다.
- 검증은 `GoogleIdTokenParser`가 구글 `tokeninfo` 엔드포인트(`https://oauth2.googleapis.com/tokeninfo?id_token=`)를 호출해서 합니다.
  서명·만료(`exp`)·발급자(`iss`)는 구글이 검사하고(유효하지 않으면 200이 아닌 응답), 서버는 `aud` == `GOOGLE_CLIENT_ID` 만 직접 확인합니다.
- 로그인 1회당 구글로 HTTP GET 1회가 나갑니다. 구글은 이 엔드포인트를 디버깅용으로 안내하므로, 트래픽이 커지면
  공개키(JWKS) 로컬 검증(`google-api-client` 등)으로 교체를 검토하세요.

### 카카오 담당자에게

토큰 발급 접점은 `AuthService.createAccessToken(Member)`입니다. `loginWithGoogle`과 같은 패턴으로
`AuthService`에 메서드를, `AuthController`(`/api/v1/auth`)에 `POST /login/kakao`를 추가하세요.

- `Member.role`은 생성자에서 `USER`로 자동 세팅됩니다. 빌더에 넣을 필요 없습니다.
- `MemberRepository`, `MemberService`는 이미 있으니 파일을 새로 만들지 말고 메서드만 추가하세요.

## 예외 인프라 담당자에게

- `ApiResponse.error(HttpStatus, String)`를 추가했습니다. `JwtAuthenticationFilter`가 401 본문을 만들 때 씁니다. `fail(ErrorCode)` 같은 팩토리를 만들 때 이 메서드와 합칠지 결정해주세요.
- 401은 필터에서 직접 응답하므로 `@RestControllerAdvice`가 관여하지 않습니다. `ApiResponse` 필드 구조가 바뀌면 `JwtAuthenticationFilter.sendUnauthorized`도 함께 수정해야 합니다.
- `MemberService.getMe`에서 회원이 없을 때 임시로 `IllegalArgumentException`을 던집니다(TODO 주석 있음). `MemberNotFoundException`(404)으로 교체 대상입니다.
- `DevLoginRequest`의 validation 실패(400)는 현재 Boot 기본 에러 본문으로 나갑니다. `MethodArgumentNotValidException` 핸들러가 생기면 자동으로 `ApiResponse` 형태가 됩니다.

## 프론트 담당자에게

현재 프론트 `auth.api.ts` / `client.ts`와 맞지 않는 부분입니다. 팀에서 정리가 필요합니다.

| 항목 | 백엔드 현재 | 프론트 현재 |
|---|---|---|
| URL 접두어 | `/api/v1/auth/me` | `/auth/me` (baseURL에 `/api/v1` 추가 필요) |
| 응답 형태 | `{status, message, data}`로 감쌈 | 본문을 그대로 읽음 (인터셉터에서 `data.data` 추출 필요) |
| refresh 토큰 | 없음 | `refreshToken` 필수 타입, 401 시 `/auth/refresh` 호출 |
| 로그아웃 | 서버 엔드포인트 없음 | `POST /auth/logout` 호출 |

- 로그아웃은 secure store의 accessToken을 지우면 끝입니다.
- 401을 받으면 토큰을 지우고 로그인 화면으로 보내면 됩니다. refresh 토큰이 저장되지 않으므로 기존 refresh 로직은 자연히 "세션 만료"로 떨어집니다.

## 토큰 구조

```json
{"sub": "1", "role": "USER", "iat": 1789012173, "exp": 1789098573}
```

- `sub`: memberId (JWT 표준상 문자열)
- `role`: `Role` enum 이름
- 서명 알고리즘 HS256. 페이로드는 암호화가 아니라 서명이므로 누구나 읽을 수 있습니다. 개인정보는 넣지 않습니다.

## 파일 위치

| 파일 | 역할 |
|---|---|
| `global/auth/JwtProperties` | `jwt.secret`, `jwt.access-token-expiration` 바인딩 |
| `global/auth/JwtTokenProvider` | 토큰 생성·파싱. Spring 의존 없는 순수 클래스 |
| `global/auth/JwtAuthenticationFilter` | 토큰 검증 필터. 401 직접 응답 |
| `global/auth/MemberPrincipal` | 요청자 정보(memberId, role) |
| `global/auth/LoginMember` | 컨트롤러 파라미터 마커 애노테이션 |
| `global/auth/LoginMemberArgumentResolver` | `@LoginMember` 값 주입 |
| `global/config/AuthConfig` | 필터 등록, 화이트리스트, `JwtTokenProvider` 빈 |
| `global/config/WebConfig` | 리졸버 등록 |
| `domain/member/service/AuthService` | `loginWithGoogle(request)`, `createAccessToken(Member)`, `devLogin(email)` |
| `domain/member/service/oauth/GoogleIdTokenParser` | 구글 `tokeninfo` 호출 → 토큰 검증 + email/name/picture 추출, `aud` 확인 |
| `domain/member/service/oauth/GoogleProperties` | `google.client-id` 바인딩 (idToken `aud` 비교값) |
| `domain/member/controller/AuthController` | `POST /api/v1/auth/google`, `GET /api/v1/auth/me` |
| `domain/member/controller/DevAuthController` | `POST /api/v1/auth/dev-login` (플래그 on일 때만) |

## 테스트

```
.\gradlew.bat test
```

| 클래스 | 내용 |
|---|---|
| `JwtTokenProviderTest` | 순수 단위 테스트. 생성·파싱, 만료, 서명 불일치, 형식 오류 |
| `JwtAuthenticationFilterTest` | MockMvc. 401 세 가지 메시지, 정상 통과, 화이트리스트, `/api` 밖 경로 |
| `AuthFlowTest` | dev-login → `/auth/me` 사이클, 회원 중복 생성 방지, validation |
| `DevLoginDisabledTest` | 플래그 off면 dev-login 404 |
| `GoogleLoginTest` | 구글 로그인: 신규 가입 + 토큰 발급, 기존 회원 재사용, validation (`GoogleIdTokenParser`는 `@MockitoBean`) |

테스트는 `test` 프로필(`src/test/resources/application-test.yaml`)로 돌아가므로 `.env` 없이도 통과합니다.
