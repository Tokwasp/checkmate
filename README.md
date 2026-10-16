# 🧳 CheckMate — AI 기반 여행 짐 싸기 도우미

> 카메라로 짐을 싸고, AI가 기내·위탁 반입 규정까지 확인해 줍니다.

여행지와 일정에 맞춘 AI 체크리스트를 만들고, 카메라로 비춘 물건을 **온디바이스 AI**로 인식해 가방에 담으며, **RAG 기반 반입 규정 검사**로 출국 전 짐을 점검하는 모바일 서비스

<!-- 서비스 스크린샷 -->


---
## 🚀 주요 기능

- **AI 맞춤 체크리스트** — 여행지·기간·날씨(Open-Meteo)를 바탕으로 LLM이 준비물 체크리스트를 추천합니다.
- **카메라로 짐 싸기** — 온디바이스 YOLO(TFLite) 모델이 카메라 속 물건을 실시간으로 인식해 인벤토리와 가방에 등록합니다.
- **기내·위탁 반입 규정 검사** — Bedrock Knowledge Base로 12개국 항공보안 규정을 검색하고, LLM이 물품별 기내/위탁 반입 가능 여부를 판정합니다.
- **기념품 입국 판정** — 기념품 사진을 비전 모델로 판독하고, 한국 입국 세관·검역 규정을 검색해 반입 가능 여부를 알려줍니다.
- **귀가 전 점검** — 여행지에서 돌아오기 전 챙겨 온 짐을 다시 스캔해 빠진 물건을 확인합니다.

---
## 🛠️ 기술 스택
### Frontend (`frontend/`)
- React Native 0.86 + Expo 57, TypeScript
- Expo Router, Zustand, TanStack Query, NativeWind
- react-native-vision-camera + react-native-fast-tflite — 온디바이스 물체 인식

### Backend (`backend/`) — 이 저장소
- Java 21, Spring Boot 4.1.1 (Spring MVC, Spring Data JPA, Validation)
- MySQL 8.0 (운영), H2 (로컬)
- JWT(jjwt 0.13), Google ID Token 로그인
- AWS SDK (S3, Bedrock Agent Runtime, Bedrock Runtime)
- 배포: AWS EC2 + Docker Compose

### AI (`AI/`)
- Python, YOLO26n-seg — 23개 클래스 물체 탐지 + 세그멘테이션
- TFLite 변환·양자화 → 앱 온디바이스 추론
- Bedrock Knowledge Base + S3 Vectors + Titan Text Embeddings V2 — 반입 규정 RAG
- Claude Haiku 4.5 (Bedrock) — 반입 판정 생성, Cohere Rerank 3.5 — 검역 규정 리랭크

### Infra / CI-CD
- AWS: VPC, EC2, RDS(MySQL), S3, Bedrock, SSM Parameter Store, CloudWatch Logs
- Nginx + Let's Encrypt, Docker Compose, GitLab CI
- Firebase App Distribution, Expo EAS Update (앱 배포·OTA)

---
## 🏗️ 시스템 아키텍처

<!-- 아키텍처 다이어그램 -->

1. **온디바이스 인식** — 카메라 프레임을 앱 안의 YOLO(TFLite) 모델로 인식해 서버 왕복 없이 물건을 판별
2. **반입 규정 검사(RAG)** — 물품 이름별로 Bedrock Knowledge Base를 검색하고, 검색된 규정을 근거로 Claude Haiku 4.5가 기내/위탁 반입 여부를 판정
   - 항공보안 규정(`avsec`) → 출국 짐 기내·위탁 판정
   - 한국 입국 세관·검역 규정(`import`) → 기념품 반입 판정 (Cohere Rerank로 검색 결과 리랭크)
3. **배포** — `develop` 브랜치 backend 변경 시 GitLab Runner가 SSM Parameter Store에서 `.env`를 만들고 `docker compose up -d --build`로 EC2에 배포

---
### 커밋 메시지 타입 프리픽스
| 타입 | Gitmoji | 용도 |
| --- | --- | --- |
| `feat:` | ✨ | 새 기능 추가 |
| `fix:` | 🐛 | 버그 수정 |
| `hotfix:` | 🚑 | 긴급 수정 |
| `refactor:` | ♻️ | 동작 변경 없는 리팩터링 |
| `docs:` | 📝 | 문서 변경 |
| `test:` | ✅ | 테스트 추가/수정 |
| `chore:` | 🔧 | 빌드, 설정, 패키지 수정 |

---
## Jira 활용 방식

### 이슈 계층 구조
`에픽(Epic) → 스토리(Story) → 하위 작업(Sub-task)` 3단계로 업무를 분해해 관리

- **에픽**: 기능 단위의 큰 묶음 (예: `반입 규정 검사`)
- **스토리**: 사용자 관점의 기능 (예: `기내·위탁 반입 판정`, `기념품 입국 판정`)
- **하위 작업**: 실제 구현 단위 작업 (예: `[BE] 반입 규정 검사 API 만들기`)

브랜치는 `{type}/{Jira 이슈키}-{작업명}` 형식으로 이슈와 연결 (예: `feat/S15P21D102-58-로그인`)

<!-- Jira 화면 스크린샷 -->

---
## 📦 폴더 구조

```text
checkmate/                  # Spring Boot API 서버
├─ src/main/java/ssafy/checkmate/
│  ├─ domain/
│  │  ├─ member/            # 회원, Google 로그인, 즐겨찾기·자주 쓰는 물건
│  │  ├─ trip/              # 여행 생성/조회, 목적지 자동완성(Google Places), 표지 사진
│  │  ├─ checklist/         # AI 체크리스트 추천, 가방, 인벤토리, 물품 스캔 등록, 귀가 전 점검
│  │  ├─ regulation/        # 기내·위탁 반입 규정 검사, 기념품 입국 판정 (Bedrock RAG)
│  │  └─ weather/           # 여행지 날씨 (Open-Meteo)
│  └─ global/               # auth(JWT) / config / exception / response / storage(S3)
├─ src/test/                 # 단위·통합 테스트
├─ docs/                     # 인증 등 설계 문서
├─ Dockerfile
└─ docker-compose.yml
```

## 📄 문서 / 트러블슈팅

- [인증 설계](docs/auth.md)

## 👥 팀 소개

| 이름 | 역할 |
| --- | --- |
| 조희제 | Backend, Infra |
| 이송제 | FrontEnd, BackEnd |
| 황서현 | FrontEnd |
| 홍진규 | AI, FrontEnd |
| 김민준 | Backend |
| 박민정 | AI |
|  |  |
