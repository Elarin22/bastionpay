# bastionpay 체크리스트

기준 문서: `~/Downloads/DESIGN.md` (v0.2, 2026-10-18) · 프로젝트명: **bastionpay** (확정) · 패키지 `com.san.bastionpay`

## 0. 요약 (현재 상태)
- Boot 4.1.1 / Java 21 / Gradle Groovy / Jar / YAML — 설계와 일치
- Initializr 11개 의존성 모두 반영됨 (+ flyway-mysql, thymeleaf-extras-springsecurity6)
- HikariCP: 의존성 O (spring-boot-starter-jdbc 경유), 설정은 이번에 추가
- 소스는 `BastionpayApplication` 뿐 → core/도메인 미구현

## 1. 의존성
### 1-1. 설계 4-1 (확정 11개)
- [x] Lombok
- [x] Configuration Processor
- [x] DevTools (developmentOnly)
- [x] Web (`starter-webmvc`, Boot 4 명칭)
- [x] Thymeleaf
- [x] Security
- [x] Data JPA
- [x] MySQL Driver
- [x] Flyway (`starter-flyway` + `flyway-mysql` 12.4.0)
- [x] Validation
- [x] Actuator
### 1-2. 이미 포함된 추가분
- [x] `thymeleaf-extras-springsecurity6:3.1.5.RELEASE` (Security 7과 sec:authorize 동작은 실행 시 확인 필요)
- [x] 각종 `*-test` 스타터 (security-test 포함)
### 1-3. 기능 구현 시 하나씩 추가 (4-2)
- [ ] JWT: `jjwt` vs `oauth2-resource-server` (미결정, 1단계)
- [ ] `thymeleaf-layout-dialect`
- [ ] `logstash-logback-encoder` (5단계 전)
- [ ] `spring-data-envers` (2단계 이후)
- [ ] `bucket4j` core (MVP 이후)
- [ ] `resilience4j` (Boot 4 호환 확인)
- [ ] `bcprov-jdk18on` (Argon2 선택 시만)
- [ ] Testcontainers (MySQL)
### 1-4. 빌드 플러그인 (4-3)
- [ ] OWASP Dependency-Check
- [ ] SpotBugs + Find Security Bugs
- [ ] JaCoCo

## 2. HikariCP
- [x] 클래스패스에 포함 (starter-data-jpa → starter-jdbc → HikariCP, Boot 기본 풀)
- [x] `application.yaml`에 풀 설정 추가 (pool-name, max 10, min-idle 2, max-lifetime 30분 등)
- [x] dev에서 `leak-detection-threshold: 20000`
- [ ] 실제 기동 후 로그에서 `bastionpay-hikari - Start completed` 확인

## 3. 설정 파일
- [x] `application.yaml` 공통 (open-in-view false, ddl-auto validate, Flyway, 에러 노출 차단, Actuator health만)
- [x] `application-dev.yaml` (Mac, 로컬 MySQL)
- [x] `application-lab.yaml` (Windows)
- [x] `.env.example`
- [x] `spring.profiles.default: dev`
- [ ] 로컬 MySQL(Docker) 기동 + DB/계정 생성 (런타임 DML / 마이그레이션 DDL 분리)
- [ ] 환경변수 주입: IntelliJ Run Config (`DB_PASSWORD`, `DB_MIGRATION_PASSWORD`, `JWT_SECRET`, `OPENBANKING_*`)
- [ ] 로컬 HTTPS (mkcert) 후 `server.ssl.*` 추가 — 현재 미설정이라 `server.port` 8443이어도 HTTP로 뜸
- [ ] Boot 4.x에서 `server.error.*` 프로퍼티명 유효한지 확인 (컴파일/기동 시)
- [ ] `management.server.port` 분리 (SecurityConfig 이후)

## 4. .gitignore
- [x] IDE/빌드 기본
- [x] `.env`, `.env.*`(예외 `.env.example`), `application-*-secret.yml`, `*.pem`, `*.jks`, `*.p12`, `*.key`
- [x] 로그, `.DS_Store`

## 5. 개발 로드맵
- [ ] 1단계: Security + JWT 쿠키, 회원가입/로그인
- [ ] 2단계: JPA 엔티티, Flyway `V1__init.sql`
- [ ] 3단계: 오픈뱅킹 연동
- [ ] 4단계: 가상계좌·원장, 동시성·멱등성
- [ ] 5단계: Windows 이동, Graylog/Wazuh/n8n/pfSense
### core (설계 15)
- [ ] SecurityConfig (anyRequest authenticated, ERROR 디스패치 허용, CSRF 쿠키, 헤더)
- [ ] WebMvcConfig, MdcLoggingFilter, RequestTimingInterceptor
- [ ] ErrorCode / BusinessException / GlobalExceptionHandler / CustomErrorAttributes
- [ ] CustomAuthenticationEntryPoint / CustomAccessDeniedHandler
- [ ] MaskingUtil, `templates/error/*`

## 6. 미결정
- [ ] JWT 라이브러리
- [ ] BCrypt vs Argon2
- [ ] 5단계 앱 실행 방식, 네트워크 구간
- [ ] MVP 범위 최종 확정

## 7. 주의
- 설계 문서는 `application.yml`로 적었지만 프로젝트는 `.yaml` 사용 중 (동작 동일)
- `application.yaml`의 `JWT_SECRET`, `OPENBANKING_CLIENT_*`는 기본값 없음. 해당 설정을 바인딩하는 코드가 생기면 환경변수 없이 기동 실패
- 현재 상태에서 앱 기동 시 DB 접속 필요 (`DB_PASSWORD` 등 + MySQL 실행)
