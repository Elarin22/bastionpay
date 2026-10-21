# 개발용 더미 데이터

팀원이 모두 같은 데이터로 개발하려고 SQL 파일로 관리한다. **개발 DB 전용이며 운영에는 넣지 않는다.**
Flyway 마이그레이션과 달리 앱이 자동으로 실행하지 않는다. MySQL Workbench 나 `mysql` 로 직접 실행한다.
여러 번 실행해도 같은 결과가 되도록 만들어 두었다(이메일·고정 id 기준 갱신).

| 파일 | 대상 DB | 내용 |
|---|---|---|
| `db/seed/member.sql` | `bastionpay_db` (3306) | BastionPay 더미 회원 4명 |
| `mock-openbanking/db/seed/mockbank.sql` | `mockbank_db` (3307) | 가짜 은행 고객·계좌·거래내역, 이용기관(BastionPay) |

실행 순서: 각 DB 에 앱을 한 번 띄워 Flyway 로 테이블을 만든 뒤 → `member.sql` → `mockbank.sql`.

## 더미 회원 (비밀번호는 모두 `12345678A!`)

| 이메일 | 역할 | 등급 |
|---|---|---|
| test@test.com | USER | STANDARD |
| vip@test.com | USER | VIP |
| vvip@test.com | USER | VVIP |
| admin@test.com | ADMIN | STANDARD |

등급은 이제 누적 이체 금액으로 자동 승급하고 3개월 이용하지 않으면 자동 강등한다(`docs/security-events.md` 4-2절). 시드의 VIP·VVIP 등급은 개발용 초기값이며, 석 달 넘게 로그인하지 않으면 정책에 따라 내려간다.
1회 이체 한도는 STANDARD 200만원 / VIP 1,000만원 / VVIP 제한 없음.

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
- 테스트 계정의 계좌 잔액(시드 기준): test 월급 통장 102,350,000 / 생활비 100,870,000, vip 주거래 통장 104,800,000, vvip 자산 관리 통장 125,000,000.
  이체 테스트를 하면 잔액이 바뀌므로, 처음 값으로 되돌리려면 `mockbank.sql` 을 다시 실행한다.
- 이체 테스트에 쓸 받는 계좌: 한빛은행 `1002345678901`(테스터), 새솔은행 `1102839475610`(테스터), 한빛은행 `2003456789012`(브이아이피), 한빛은행 `3004567890123`(브이브이아이피).
  은행과 계좌번호가 모두 맞아야 입금된다.
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
