# BastionPay Graylog 이벤트 16종 전체 공격 트리거 가이드 (v2.0)

본 문서는 Graylog에 등록된 **16종의 BastionPay 전용 탐지 규칙(Event Definitions)**을 개별적으로 검증하기 위한 상세 가이드입니다.

---

## 1. 핵심 원리 및 검증 결과 보고 (중요)

### Q1. GELF의 `_` (밑줄) 필드명이 Graylog 쿼리(`filebeat_*`)와 불일치하지 않는가?
- **아닙니다! Graylog GELF 규격상 100% 정상 일치합니다.**
- **GELF 공식 스펙 (RFC/Graylog 표준)**:
  - GELF 프로토콜에서는 모든 사용자 정의(Additional) 필드의 키 이름이 반드시 밑줄(`_`)로 시작해야 합니다 (예: `_filebeat_app_name`, `_rule`, `_src_ip`).
  - **Graylog는 GELF Input을 수신할 때 맨 앞의 밑줄(`_`)을 자동으로 제거(Strip)하고 인덱스에 저장**합니다.
  - **실제 Graylog 인덱스 확인 결과**:
    - `_filebeat_app_name: bastionpay` → 저장된 필드: `filebeat_app_name: bastionpay`
    - `_filebeat_event_type: LOGIN_FAILURE` → 저장된 필드: `filebeat_event_type: LOGIN_FAILURE`
    - `_rule: snort-portscan` → 저장된 필드: `rule: snort-portscan`
    - `_src_ip: 192.168.3.205` → 저장된 필드: `src_ip: 192.168.3.205`
  - 따라서 Graylog의 `filebeat_app_name:bastionpay AND filebeat_event_type:LOGIN_FAILURE` 쿼리는 **GELF로 주입한 로그와 100% 완벽히 매칭**됩니다.

### Q2. 왜 실제 웹 curl 요청보다 GELF 가상 IP 주입이 필수적인가? (치명적 원인 발견)
- 백엔드 소스코드 ([`ClientIpResolver.java:27`](file:///c:/Users/user/Desktop/ALEPH/git_folder/team_project/project_Folder/src/main/java/com/san/bastionpay/audit/ClientIpResolver.java#L27)) 확인 결과:
  ```java
  String remote = request.getRemoteAddr();
  if (remote == null || !props.trustedProxies().contains(remote)) return remote;
  ```
  - 서버의 `trustedProxies` 설정에 테스트 PC(`192.168.3.12`)가 없으면, 백엔드는 **헤더의 `X-Forwarded-For: 192.168.3.2XX`를 보안상 완전히 무시**하고 실제 소켓 주소인 `192.168.3.12`로 강제 기록합니다.
  - 이로 인해 발생하는 심각한 문제:
    1. **쿨다운 충돌**: 모든 테스트가 동일한 `192.168.3.12`로 찍혀 Graylog의 `GroupBy: filebeat_source_ip` 쿨다운(Grace Period, 2~5분)에 걸려 다음 테스트가 전부 무시됩니다.
    2. **관제 PC 자체 차단**: n8n SOAR가 `192.168.3.12`를 차단해버리면 실습 PC가 먹통이 됩니다.
  - **반면 GELF UDP 주입**은 각 테스트마다 독립된 가상 IP(`192.168.3.201` ~ `192.168.3.216`)가 그대로 인덱싱되므로 서로 간섭 없이 16개를 하나씩 안전하게 테스트할 수 있습니다.

### Q3. 13번 (서버 API 인증 실패) 검증 결과
- 백엔드 코드 ([`AdminApiAuth.java:44`](file:///c:/Users/user/Desktop/ALEPH/git_folder/team_project/project_Folder/src/main/java/com/san/bastionpay/admin/AdminApiAuth.java#L44)) 확인:
  - 잘못된 API 키로 `/api/admin/incidents`를 호출하면 백엔드가 실제로 `filebeat_event_type: API_AUTH_FAILED`, `reason_code: BAD_API_KEY` 이벤트를 파일비트로 기록합니다.
  - 스크립트에서는 실제 웹 API 호출과 가상 IP GELF 주입을 동시 실행(Dual-Fire)하여 100% 감지되도록 보강했습니다.

### Q4. 14번 (잠긴 계정) 검증 결과
- `X-API-Key: <YOUR_SECURITY_API_KEY>`는 운영 환경의 정식 키입니다 (`/api/admin/users` 조회 성공으로 확인, `.env`의 `SECURITY_API_KEY` 참조).
- 주의: `member_id: 1`은 개인 계정이라 백엔드 보호로 잠글 수 없습니다. 반드시 **`test@test.com` (Member ID: 2)**을 잠그도록 스크립트에 안전하게 구성되어 있습니다.

### Q5. 16번 (Wazuh 100280 상관분석 경보)
- 이 룰은 Graylog 설정상 `search_within_ms: 300000` (5분), `execute_every_ms: 300000` (5분)입니다.
- 즉, Graylog 스케줄러가 5분 주기로 동작하므로 로그 주입 후 Alert이 표시되기까지 **최대 5분**이 소요될 수 있습니다.

---

## 2. 16종 이벤트 전체 매핑 현황

| 번호 | 이벤트명 | 가상 출발지 IP | 탐지 쿼리 및 임계치 | 테스트 방식 |
|:---:|:---|:---:|:---|:---:|
| **1** | `K1_웹 스캐너 탐지 (Nikto/Gobuster)` | `192.168.3.201` | 60초 내 `AUTHENTICATION_REQUIRED` >= 15 | 실제 웹 HTTP 16회 + GELF 백업 |
| **2** | `K2_Hydra 로그인 브루트포스 탐지` | `192.168.3.202` | 60초 내 `LOGIN_FAILURE` >= 5 | GELF 규격 주입 6회 (ENABLE 필요) |
| **3** | `K3_Suricata IDS 경보 폭주 탐지` | `192.168.3.203` | 30초 내 `rule:ids-alert` >= 5 | GELF IDS 경보 6건 주입 |
| **4** | `K4_저속 브루트포스 탐지 (Low-and-Slow)` | `192.168.3.204` | 1800초 내 `LOGIN_FAILURE` >= 8 | GELF 주입 9회 (ENABLE 필요) |
| **5** | `K5_네트워크 포트스캔 탐지 (Snort NIDS)` | `192.168.3.205` | 60초 내 `rule:snort-portscan` >= 5 | GELF Snort 경보 6건 주입 |
| **6** | `K6_ADMIN 권한 탈취 시도 탐지` | `192.168.3.206` | 60초 내 `ACCESS_DENIED AND INSUFFICIENT_AUTHORITY` >= 1 | GELF 무단 접근 이벤트 주입 |
| **7** | `K6_비정상 ADMIN 권한 승급 침해 탐지` | `192.168.3.207` | 60초 내 `TIER_CHANGED AND NOT (...)` >= 1 | GELF TIER_CHANGED 주입 (SOAR 연동) |
| **8** | `L2_동일 IP 다중 계정 로그인 실패` | `192.168.3.208` | 300초 내 동일 IP `LOGIN_FAILURE` > 4 (>=5) | GELF 다중 계정 로그인 실패 5건 |
| **9** | `S1_반복 로그인 실패` | `192.168.3.209` | 600초 내 동일 IP + 단일 계정 `LOGIN_FAILURE` >= 5 | GELF 단일 타겟 로그인 실패 6건 |
| **10** | `S2_이체 실패 후 환불 완료` | `192.168.3.210` | 60초 내 `TRANSFER_REFUNDED` >= 1 | GELF 이체 환불 이벤트 주입 |
| **11** | `S3_동일 출발지 반복 이체 실패·환불` | `192.168.3.211` | 600초 내 동일 IP `TRANSFER_REFUNDED AND FAILURE` >= 2 | GELF 이체 실패 3건 주입 |
| **12** | `동일 계정 이체 요청 급증` | `192.168.3.212` | 300초 내 동일 회원 `TRANSFER_REQUESTED` >= 5 | GELF 이체 요청 6건 주입 |
| **13** | `서버 API 인증 실패` | `192.168.3.213` | 300초 내 `API_AUTH_FAILED` >= 3 | 실제 웹 curl 4회 + GELF 4건 동시 주입 |
| **14** | `잠긴 계정 로그인 재시도` | `192.168.3.214` | 300초 내 `LOGIN_FAILURE AND ACCOUNT_LOCKED` > 2 (>=3) | 실제 API 계정 사전 잠금 + GELF 주입 |
| **15** | `차단 IP 요청` | `192.168.3.215` | 300초 내 `IP_BLOCKED_REQUEST` >= 2 | 실제 API IP 사전 차단 + GELF 주입 |
| **16** | `차단 IP 후 로그인·이체 시도` | `192.168.3.216` | 300초 내 `wazuh_rule_id:100280 OR 100281` >= 1 | GELF Wazuh 경보 주입 (최대 5분 대기) |

---

## 3. 실행 방법 (Kali Linux / WSL)

```bash
cd /mnt/c/Users/user/Desktop/ALEPH/git_folder/team_project/project_Folder/cjy
chmod +x bastionpay_trigger_all.sh
./bastionpay_trigger_all.sh
```
