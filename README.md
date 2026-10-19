# 🧳 CheckMate — AI 기반 여행 짐 싸기 도우미

> "뭐 빠뜨렸지?", "이거 가져가도 되나?" — 여행 짐 싸기의 두 가지 고민을 AI로 해결하는 모바일 앱

**담당 역할** — 백엔드 및 인프라

---
## 💡 기획 배경

- 해외여행 출국자 수 2,900만 명(2025년)으로 해외여행이 다시 일상이 되었습니다.
- 여행자 111명 대상 설문 결과, **82%** 가 물품을 챙기지 않아 현지에서 구매한 경험이 있고, **62%** 가 기내 반입·국내 반입 여부를 고민했습니다.
- 짐 체크리스트, 항공 반입 규정, 검역 규정이 모두 흩어져 있어 여행자가 직접 찾아봐야 했고, CheckMate는 이를 **하나의 서비스**로 묶었습니다.

---
## 🚀 주요 기능

| AI 체크리스트 추천 | 내 물건 불러오기 | 실시간 물건 인식 |
| :---: | :---: | :---: |
| <img src="docs/images/feature-checklist.png" width="240"> | <img src="docs/images/feature-my-items.png" width="240"> | <img src="docs/images/feature-scan.png" width="240"> |
| 날씨·목적지·활동을 바탕으로<br>AI가 준비물 체크리스트 추천 | 즐겨찾기·자주 쓴 물건을<br>체크리스트에 바로 추가 | 카메라에 비추면 온디바이스 AI가<br>물건을 인식해 선택한 가방에 담기 |

| 항공 반입 규정 | 반입·검역 판정 | 귀가 전 점검 |
| :---: | :---: | :---: |
| <img src="docs/images/feature-regulation.png" width="240"> | <img src="docs/images/feature-judgement.png" width="240"> | <img src="docs/images/feature-homecoming.png" width="240"> |
| 규정 안내를 통해 물건별<br>위험·제한·허용 확인 | 물품을 촬영하면 AI가<br>반입 가능 여부와 근거를 판정 | 귀가 전, 챙겨 간 짐과 비교해<br>빠뜨린 물건 점검 |

---
## 🛠️ 기술 스택

### Frontend
- React Native, Expo, TypeScript
- Zustand, TanStack Query, NativeWind
- YOLO, TFLite (온디바이스 물체 인식)

### Backend
- Java 21, Spring Boot, Spring Data JPA
- MySQL, H2
- JWT, Google 로그인
- Amazon Bedrock (Knowledge Base, Claude Haiku 4.5), Cohere Rerank

### Infra
- AWS EC2, RDS, S3, VPC, SSM Parameter Store, CloudWatch
- Docker Compose, Nginx, Let's Encrypt
- GitLab CI, Firebase App Distribution, Expo EAS Update

---
## 🏗️ 시스템 아키텍처

<img src="docs/images/architecture.png" width="800">

1. **온디바이스 인식** — 카메라 프레임을 앱 안의 YOLO(TFLite) 모델로 인식해 서버 왕복 없이 물건을 판별
2. **반입 규정 검사(RAG)** — 물품 이름별로 Bedrock Knowledge Base를 검색하고, 검색된 규정을 근거로 Claude Haiku 4.5가 기내/위탁 반입 여부를 판정
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

<img src="docs/images/jira.png" width="700">

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

- [검역 규정 RAG에 Rerank 적용 — 판정 정확도 유지, 입력 토큰 38% 절감](docs/troubleshooting/quarantine-rerank.md)

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
