# 웹 스캐너(K1) 및 Suricata IDS 경보 폭주(K3) 취약점 점검 가이드

| 항목 | 내용 |
|---|---|
| 대상 서버 | BastionPay 웹 서버 (`https://192.168.3.16`) |
| 관제/수집 인프라 | pfSense / Suricata IDS, Graylog (`192.168.3.16:9000`), n8n (`192.168.3.12:5678`) |
| 목적 | 보안 관제 자동화 시나리오 중 정찰(Reconnaissance) 및 침투 시도 단계의 탐지·대응 검증 |
| 작성일 | 2026-11-05 |

---

## 1. [K1] 웹 스캐너 경로 탐색 공격 (Web Directory Scanner)

> **공격자 가상 출발지 IP**: `192.168.3.201` (내 PC `.12`와 분리하여 독립 티켓 발행)

### 1-1. 공격 시나리오 및 원리
- **공격 기법**: 공격자가 웹 사이트의 관리자 페이지, 백업 파일, 숨겨진 엔드포인트를 찾기 위해 무작위 사전(Wordlist) 대입 도구(`Gobuster`, `Nikto` 등)를 이용해 고속으로 경로를 찔러보는 무차별 탐색(Brute-force Enumeration) 공격입니다.
- **백엔드 탐지 로그**: 
  - `SecurityConfig.java`의 보안 필터 체인에 따라 미인증 상태에서 비인가/비존재 경로에 접근하면 `HTTP 401`과 함께 `filebeat_event_type: AUTHENTICATION_REQUIRED` 로그가 연속 발생합니다.
- **Graylog K1 탐지 룰**:
  - 조건: `filebeat_source_ip` 기준, **2분(또는 1분) 내 고유 경로(card(request_path)) >= 15건** 이상 발생 시 탐지.
  - 단순 반복 요청(세션 만료 등)과 실제 스캐너 공격을 구분하기 위해 단순 `count()`가 아닌 고유 경로 다양도(`card`)를 집계합니다.

### 1-2. 취약점 점검 명령어

#### 방법 A: Gobuster를 활용한 표준 디렉터리 스캔 (가상 IP `192.168.3.201` 주입)
> BastionPay는 미인증 경로에 대해 `401 (Length: 0)`을 반환하므로, 와일드카드 중단을 방지하기 위해 `-b 401,404` 옵션을 사용하며, `-H "X-Forwarded-For: 192.168.3.201"` 헤더로 출발지 IP를 지정합니다.

```bash
# 1. 16개의 가상 경로 사전 파일 생성
seq -f "scan-check-%g" 1 16 > k1_paths.txt

# 2. Gobuster 실행 (출발지 IP: 192.168.3.201)
gobuster dir -u https://192.168.3.16 -w k1_paths.txt -t 1 -k -b 401,404 -H "X-Forwarded-For: 192.168.3.201"
```

#### 방법 B: 경량 curl 반복문 (가상 IP `192.168.3.201`)
```bash
for i in {1..16}; do
  curl -k -s -o /dev/null -w "/scan-check-${i}: HTTP %{http_code}\n" \
    -H "X-Forwarded-For: 192.168.3.201" \
    "https://192.168.3.16/scan-check-${i}"
done
```

---

## 2. [K3] Suricata IDS 경보 폭주 (Network IDS Signature Alerts)

> **공격자 가상 출발지 IP**: `192.168.3.203` (K1의 `.201`과 완전히 분리되어 쿨다운 없이 연속 실행 가능)

### 2-1. 공격 시나리오 및 원리
- **공격 기법**: 웹 취약점 스캐너나 침투 테스터가 웹 애플리케이션의 입력창, 파라미터 등에 악성 페이로드(SQL Injection, XSS, Path Traversal 등)를 연속 전송하여 침투를 시도하는 행위입니다.
- **네트워크 IDS(Suricata) 탐지 원리**:
  - 네트워크 단에서 트래픽을 감시하던 Suricata가 오픈 시그니처 룰셋(`ET OPEN`)과 매칭하여 `rule: ids-alert` 경보를 생성합니다.
- **Graylog K3 탐지 룰**:
  - 조건: 동일 `src_ip` 기준, **30초 내 `rule: ids-alert` >= 5건** 누적 시 `HIGH` 등급 보안 인시던트 발생.

### 2-2. 취약점 점검 명령어

#### 1단계: 실제 웹 취약점 페이로드 전송 (가상 IP `192.168.3.203`)
> **⚠️ 환경 제약 사항**:
> 대상 웹 서버가 `https://192.168.3.16` (SSL/TLS 443 암호화) 환경이므로, 중간 pfSense/Suricata 구간에 **SSL Inspection (TLS 복호화)** 또는 리버스 프록시 미러링이 구성되어 있어야 센서가 L7 암호문을 해독하여 시그니처를 잡아냅니다.

```bash
ATTACKER_IP="192.168.3.203"

curl -k -s -o /dev/null -w "[1/5] SQLi 1: HTTP %{http_code}\n" -H "X-Forwarded-For: ${ATTACKER_IP}" "https://192.168.3.16/login?id=1%27%20OR%20%271%27=%271"
curl -k -s -o /dev/null -w "[2/5] Nikto Scanner UA: HTTP %{http_code}\n" -H "X-Forwarded-For: ${ATTACKER_IP}" -A "Mozilla/5.00 (Nikto/2.1.6)" "https://192.168.3.16/login"
curl -k -s -o /dev/null -w "[3/5] SQLMap + UNION: HTTP %{http_code}\n" -H "X-Forwarded-For: ${ATTACKER_IP}" -A "sqlmap/1.7" "https://192.168.3.16/login?user=admin%27%20UNION%20SELECT%201,2,3--"
curl -k -s -o /dev/null -w "[4/5] Path Traversal: HTTP %{http_code}\n" -H "X-Forwarded-For: ${ATTACKER_IP}" --path-as-is "https://192.168.3.16/../../etc/passwd"
curl -k -s -o /dev/null -w "[5/5] XSS Injection: HTTP %{http_code}\n" -H "X-Forwarded-For: ${ATTACKER_IP}" "https://192.168.3.16/login?q=%3Cscript%3Ealert(1)%3C/script%3E"
```

#### 2단계: GELF 표준 프로토콜 기반 모의 이벤트 주입 (가상 IP `192.168.3.203` — 확정 검증용)
> Graylog(SIEM) 상관분석 ➔ n8n(SOAR) ➔ 인시던트 티켓 생성 ➔ 디스코드 실시간 경보 전 과정을 100% 확정 검증하기 위해 표준 GELF UDP 12201 포트로 정형화된 이벤트를 직접 주입합니다.

```bash
# 가상 공격자 IP(192.168.3.203) 명의로 Suricata IDS 경보 5건 즉시 주입
for i in {1..5}; do
  echo -n '{"version":"1.1","host":"suricata","short_message":"Suricata IDS Alert","_rule":"ids-alert","_src_ip":"192.168.3.203"}' | nc -u -w1 192.168.3.16 12201
done
```

---

## 3. 사후 정리 및 상태 초기화 명령어
테스트 후 생성된 티켓을 일괄 종료하고 계정/IP 잠금 상태를 초기화할 때 사용합니다.

```bash
# 1. 관리자 API 키 변수 선언
export ADMIN_API_KEY="N8vQ3pL7xR2mK9cD"

# 2. 열린 티켓 일괄 종료
curl -k -s -X POST "https://192.168.3.16/api/admin/incidents/close" \
  -H "X-API-Key: ${ADMIN_API_KEY}" -H "Content-Type: application/json" \
  -d '{"all_open": true}'

# 3. 테스트 계정 잠금 해제 (필요 시)
curl -k -s -X POST "https://192.168.3.16/api/admin/unlock" \
  -H "X-API-Key: ${ADMIN_API_KEY}" -H "Content-Type: application/json" \
  -d '{"member_id": 1}'
```

