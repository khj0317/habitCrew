# HabitCrew 🌱

> 크루와 함께 매일 인증하고, 빼먹으면 벌금 포인트가 쌓이는 습관 인증 서비스

## 기술 스택

| 영역 | 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4, Spring Security (JWT), JPA, Flyway |
| 데이터 | PostgreSQL 17, Redis 7, S3 호환 사진 저장소 (3단계에서 추가) |
| 프론트엔드 | React 19, TypeScript, Vite, TanStack Query, Tailwind CSS 4 |
| 테스트 | JUnit 5, Testcontainers (실제 PostgreSQL·Redis 컨테이너로 통합 테스트) |

## 실행 방법

1. Docker Desktop을 켭니다.
2. `dev.cmd`를 더블클릭합니다. (또는 `npm install` 후 `npm run dev`)
3. 브라우저에서 http://localhost:5180 을 엽니다.

| 주소 | 용도 |
|---|---|
| http://localhost:5180 | 웹 앱 |
| http://localhost:8090/swagger-ui.html | API 문서 |

다른 프로젝트와 동시에 켜도 충돌하지 않도록 포트를 따로 씁니다: PostgreSQL 5434, Redis 6381, 백엔드 8090, 웹 5180.

백엔드 테스트: `npm run test:api`

로컬 확인용 테스트 계정 (로컬 DB에만 있음): `demo@habitcrew.test` / `Habit1234`

## 개발 단계

- [x] **1단계: 기반**: 모노레포, Docker Compose, 회원가입·로그인 (JWT + 리프레시 토큰 회전)
- [ ] **2단계: 그룹**: 크루 생성, 초대 코드, 멤버 관리
- [ ] **3단계: 인증**: 사진 업로드, 피드, 좋아요·댓글
- [ ] **4단계: 정산**: 자정 정산 스케줄러, 벌금 포인트, 연속 인증 일수
- [ ] **5단계: 랭킹·알림**: Redis 랭킹, 달력 잔디, PWA, 웹 푸시
- [ ] **6단계: 품질**: 테스트 보강, 시드 데이터, 성능 측정

## 설계 메모

### 인증 (1단계)
- **액세스 토큰**(30분)은 브라우저 메모리에만 둡니다. localStorage에 두면 XSS 공격으로 탈취될 수 있기 때문입니다.
- **리프레시 토큰**(14일)은 `HttpOnly; SameSite=Strict; Path=/api/auth` 쿠키로만 주고받습니다. JavaScript가 읽을 수 없고, 다른 사이트에서 시작된 요청에는 실리지 않습니다.
- 리프레시 토큰은 Redis에 **SHA-256 해시로 저장**하고, 쓸 때 `GETDEL`로 꺼내면서 지웁니다. 같은 토큰을 두 번 쓰면 두 번째는 실패합니다(토큰 회전).
- 프론트에서 여러 요청이 동시에 401을 받아도 **재발급 요청은 한 번만** 보내도록 Promise를 공유합니다. React StrictMode에서 effect가 두 번 실행돼도 토큰이 꼬이지 않는 이유이기도 합니다.
- 같은 이메일로 **5번 틀리면 10분 잠금**됩니다(Redis `INCR` + TTL). 가입되지 않은 이메일도 같은 시간만큼 비밀번호를 비교해서, 응답 속도로 가입 여부를 알아낼 수 없게 했습니다.
- 이메일 중복은 애플리케이션에서 먼저 검사하고, 동시에 가입 요청이 와서 검사를 통과하더라도 **DB 유니크 제약**에서 한 번 더 막습니다.
