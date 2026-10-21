# 개발용 더미 데이터

팀원이 모두 같은 데이터로 개발하려고 SQL 파일로 관리한다. **개발 DB 전용이며 운영에는 넣지 않는다.**
Flyway 마이그레이션과 달리 앱이 자동으로 실행하지 않는다. MySQL Workbench 나 `mysql` 로 직접 실행한다.
여러 번 실행해도 같은 결과가 되도록 만들어 두었다(이메일·고정 id 기준 갱신).

| 파일 | 대상 DB | 내용 |
|---|---|---|
| `db/seed/member.sql` | `bastionpay_db` (3306) | BastionPay 더미 회원 4명 |
| `mock-openbanking/db/seed/mockbank.sql` | `mockbank_db` (3307) | 가짜 은행 고객·계좌·거래내역, 이용기관(BastionPay) |
| `mock-openbanking/db/seed/mockbank.local.sql` | `mockbank_db` | 개인용(커밋하지 않음, `*.local.sql` 은 gitignore) |

실행 순서: 각 DB 에 앱을 한 번 띄워 Flyway 로 테이블을 만든 뒤 → `member.sql` → `mockbank.sql`.

## 더미 회원 (비밀번호는 모두 `12345678A!`)

| 이메일 | 역할 | 등급 |
|---|---|---|
| test@test.com | USER | STANDARD |
| vip@test.com | USER | VIP |
| vvip@test.com | USER | VVIP |
| admin@test.com | ADMIN | STANDARD |

## 가짜 은행

- 오픈뱅킹 연결의 본인 인증은 **휴대폰 문자 인증**이다. 번호를 입력하면 인증번호가 오고(개발 중에는 문자를 보내지 않고 **화면과 서버 로그에 보여 준다**),
  인증번호를 입력하면 연결할 계좌를 고르는 화면으로 넘어간다.

| BastionPay 회원 | 은행 인증에 쓰는 휴대폰 번호(더미) |
|---|---|
| test@test.com | 010-0000-0002 |
| vip@test.com | 010-0000-0003 |
| vvip@test.com | 010-0000-0004 |
| (BastionPay 약정계좌 소유자, 잠김) | 010-0000-0001 |

- 가상의 은행: 한빛은행(901), 새솔은행(902).
- 이용기관 BastionPay 의 개발용 공개 값 (실제 오픈뱅킹 키가 아니다)

| 항목 | 값 |
|---|---|
| client_id | `bastionpay-dev-client` |
| client_secret | `bastionpay-dev-secret` |
| 이용기관코드 | `M202610001` |
| 입금이체 암호문구 | `bastionpay-dev-pass` |
| redirect URI | `https://localhost:8080/openbanking/callback` |

가짜 서버로 연결하려면 BastionPay 의 `.env` 에 `OPENBANKING_BASE_URL=http://localhost:8090`,
`OPENBANKING_CLIENT_ID`, `OPENBANKING_CLIENT_SECRET`, `OPENBANKING_ORG_CODE`, `OPENBANKING_PASS_PHRASE`(이체에 필요) 를 위 값으로 넣는다.

## 개인용 파일

실제 이메일·계좌번호가 필요하면 `mockbank.local.sql` 처럼 `*.local.sql` 로 만들어 쓴다. 커밋되지 않는다.
