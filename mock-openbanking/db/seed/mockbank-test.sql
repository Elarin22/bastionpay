-- 가짜 오픈뱅킹(mock-openbanking) 테스트용 데이터. 기능별 시험 상황을 만들기 위한 파일이다(개발 DB 전용, 운영에는 넣지 않는다).
-- 대상 DB: mockbank_db (3307). 앱을 한 번 띄워 Flyway 로 테이블을 만든 뒤 실행한다(기본 시드 mockbank.sql 을 따로 넣지 않아도 된다). 여러 번 실행해도 같은 결과가 되도록 고정 id 로 갱신한다.
-- 거래일은 실행 시점 기준 상대 날짜다. 휴대폰 번호는 더미(010-0000-00xx)라 문자를 보내지 않고 인증번호를 화면에 보여 준다.
--
--   010-0000-0011  paging@test.com      계좌 2개: "거래내역 많은 통장"(거래 40건 → 거래내역 10건씩 쪽 나누기 시험) + "빈 통장"(거래 0건, 빈 화면 시험)
--   010-0000-0012  rich@test.com        "고액 통장" 잔액 50억 원 → 등급별 이체 한도(VVIP 제한 없음) 시험
--   010-0000-0013  poor@test.com        "용돈 통장" 잔액 1,000원 → 잔액 부족 시험
--   010-0000-0014  lockedbank@test.com  잠긴 고객 → 인증을 통과할 수 없다
--   010-0000-0015  closed@test.com      "해지된 통장"(해지) + "정상 통장" → 받는 계좌가 해지된 경우(받는 계좌를 찾을 수 없음) 시험
-- 받는 계좌 번호(한빛은행 901 / 새솔은행 902): 4001000000201, 4101000000202, 4001000000203, 4001000000204, 4001000000205, 4001000000206(해지), 4101000000207
-- BastionPay 의 db/seed/test-data.sql 의 이체 기록이 가리키는 출금 계좌(핀테크이용번호 ...201)가 이 파일의 "거래내역 많은 통장"이다.
--
-- 이 파일 하나로 시험 환경이 완성된다: 기본 시드(고객·계좌·거래·이용기관 BastionPay)도 함께 넣는다(이미 있으면 같은 값으로 되돌린다 — 기본 계좌의 잔액도 시드 값으로 돌아간다). 본인이 만든 개인 계정은 들어 있지 않다.

-- ===== 기본 더미 데이터 (mockbank.sql 와 같은 내용) =====
-- 가짜 오픈뱅킹(mock-openbanking) 개발용 더미 데이터. 팀원 모두 같은 데이터로 맞추기 위한 파일이다.
-- 대상 DB: mockbank_db (mockbank-mysql, 3307). 개발 DB 전용이며 운영에는 넣지 않는다. 가상의 은행(한빛은행 901, 새솔은행 902)이다.
-- 테스트 계정(test/vip/vvip)의 계좌 잔액은 모두 1억 원 안팎이다. BastionPay 약정계좌(id 1)는 이체 중간 계좌로 5천만 원.
-- 여러 번 실행해도 같은 결과가 되도록 고정 id 기준으로 갱신한다. 거래일은 실행 시점 기준 상대 날짜다.
--
-- 오픈뱅킹 사용자 인증은 휴대폰 문자 인증이다. 더미 고객의 휴대폰 번호(실제로는 쓰이지 않는 번호라 문자를 보내지 않고,
-- 개발 중에는 인증번호를 화면과 서버 로그에 보여 준다):
--   test@test.com 회원 = 010-0000-0002,  vip@test.com = 010-0000-0003,  vvip@test.com = 010-0000-0004
--   (010-0000-0001 은 BastionPay 약정계좌 소유자로 잠겨 있어 인증할 수 없다)
-- 이용기관 BastionPay (BastionPay 의 OPENBANKING_CLIENT_ID / CLIENT_SECRET / ORG_CODE 에 같은 값을 넣는다)
--   client_id: bastionpay-dev-client   client_secret: bastionpay-dev-secret   이용기관코드: M202610001
--   입금이체 암호문구: bastionpay-dev-pass
-- 위 값은 개발용 공개 값이다. 실제 금융결제원 키가 아니다.

INSERT INTO bank_customer (id, user_seq_no, login_id, password_hash, name, phone, status, created_at) VALUES
    (1, '1100000001', 'inst-bastionpay', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', 'BastionPay 약정', '01000000001', 'LOCKED', NOW(6)),
    (2, '1100000002', 'test@test.com', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', '테스터', '01000000002', 'ACTIVE', NOW(6)),
    (3, '1100000003', 'vip@test.com', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', '브이아이피', '01000000003', 'ACTIVE', NOW(6)),
    (4, '1100000004', 'vvip@test.com', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', '브이브이아이피', '01000000004', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE login_id = VALUES(login_id), password_hash = VALUES(password_hash), name = VALUES(name), phone = VALUES(phone), status = VALUES(status);

INSERT INTO bank_account (id, customer_id, fintech_use_num, bank_code_std, bank_name, account_num, account_num_masked,
                          account_alias, account_holder_name, account_type, balance_amt, status, version, created_at) VALUES
    (1, 1, '199900000000000000000001', '901', '한빛은행', '9001000000001', '900-***-0001', 'BastionPay 약정계좌', 'BastionPay 약정', '1', 50000000, 'ACTIVE', 0, NOW(6)),
    (2, 2, '199900000000000000000002', '901', '한빛은행', '1002345678901', '100-***-8901', '월급 통장', '테스터', '1', 102350000, 'ACTIVE', 0, NOW(6)),
    (3, 2, '199900000000000000000003', '902', '새솔은행', '1102839475610', '110-***-5610', '생활비', '테스터', '1', 100870000, 'ACTIVE', 0, NOW(6)),
    (4, 3, '199900000000000000000004', '901', '한빛은행', '2003456789012', '200-***-9012', '주거래 통장', '브이아이피', '1', 104800000, 'ACTIVE', 0, NOW(6)),
    (5, 4, '199900000000000000000005', '901', '한빛은행', '3004567890123', '300-***-0123', '자산 관리 통장', '브이브이아이피', '1', 125000000, 'ACTIVE', 0, NOW(6))
ON DUPLICATE KEY UPDATE bank_name = VALUES(bank_name), account_alias = VALUES(account_alias),
                        account_holder_name = VALUES(account_holder_name), balance_amt = VALUES(balance_amt), status = VALUES(status);

INSERT INTO bank_transaction (id, account_id, tran_at, inout_type, tran_type, print_content, tran_amt, after_balance_amt, branch_name, created_at) VALUES
    (15, 2, NOW(6) - INTERVAL 60 DAY, 'IN', '이체', '초기 입금', 100000000, 100400000, '본점', NOW(6)),
    (16, 3, NOW(6) - INTERVAL 60 DAY, 'IN', '이체', '초기 입금', 100000000, 100000000, '본점', NOW(6)),
    (17, 4, NOW(6) - INTERVAL 60 DAY, 'IN', '이체', '초기 입금', 100000000, 100500000, '본점', NOW(6)),
    (18, 5, NOW(6) - INTERVAL 60 DAY, 'IN', '이체', '초기 입금', 100000000, 122500000, '본점', NOW(6)),
    (1, 2, NOW(6) - INTERVAL 40 DAY, 'IN', '급여', '9월 급여', 3200000, 103600000, '본점', NOW(6)),
    (2, 2, NOW(6) - INTERVAL 33 DAY, 'OUT', '이체', '월세', 700000, 102900000, '본점', NOW(6)),
    (3, 2, NOW(6) - INTERVAL 21 DAY, 'OUT', '카드', '카드 대금', 480000, 102420000, '본점', NOW(6)),
    (4, 2, NOW(6) - INTERVAL 9 DAY, 'OUT', '이체', '관리비', 120000, 102300000, '본점', NOW(6)),
    (5, 2, NOW(6) - INTERVAL 3 DAY, 'IN', '이체', '용돈', 50000, 102350000, '본점', NOW(6)),
    (6, 3, NOW(6) - INTERVAL 30 DAY, 'IN', '이체', '생활비 이체', 1000000, 101000000, '본점', NOW(6)),
    (7, 3, NOW(6) - INTERVAL 12 DAY, 'OUT', '카드', '마트', 86500, 100913500, '본점', NOW(6)),
    (8, 3, NOW(6) - INTERVAL 5 DAY, 'OUT', '카드', '교통비', 43500, 100870000, '본점', NOW(6)),
    (9, 4, NOW(6) - INTERVAL 28 DAY, 'IN', '급여', '급여', 5500000, 106000000, '본점', NOW(6)),
    (10, 4, NOW(6) - INTERVAL 15 DAY, 'OUT', '이체', '적금 이체', 500000, 105500000, '본점', NOW(6)),
    (11, 4, NOW(6) - INTERVAL 7 DAY, 'OUT', '카드', '카드 대금', 700000, 104800000, '본점', NOW(6)),
    (12, 5, NOW(6) - INTERVAL 45 DAY, 'IN', '이체', '배당금', 3000000, 125500000, '본점', NOW(6)),
    (13, 5, NOW(6) - INTERVAL 20 DAY, 'OUT', '이체', '투자 이체', 2000000, 123500000, '본점', NOW(6)),
    (14, 5, NOW(6) - INTERVAL 4 DAY, 'IN', '이체', '임대 수입', 1500000, 125000000, '본점', NOW(6))
ON DUPLICATE KEY UPDATE tran_at = VALUES(tran_at), print_content = VALUES(print_content),
                        tran_amt = VALUES(tran_amt), after_balance_amt = VALUES(after_balance_amt);

INSERT INTO oauth_client (id, client_id, client_secret_hash, name, org_code, enabled, escrow_account_id, transfer_pass_phrase_hash, created_at) VALUES
    (1, 'bastionpay-dev-client', '$2y$12$1QbM0NudHOXU9zjvMIgo2.v4RkXgW8nRJplX3U95aXqSFSAdIKJ8u', 'BastionPay', 'M202610001', 1, 1, '$2y$12$RbZEuXsPtVma3I2NmPWTB.TUx1VBptZkXFhHWD.FyTc38TTqdHDpu', NOW(6))
ON DUPLICATE KEY UPDATE client_secret_hash = VALUES(client_secret_hash), name = VALUES(name), org_code = VALUES(org_code),
                        enabled = VALUES(enabled), escrow_account_id = VALUES(escrow_account_id),
                        transfer_pass_phrase_hash = VALUES(transfer_pass_phrase_hash);

INSERT INTO oauth_client_redirect_uri (id, client_pk, redirect_uri) VALUES
    (1, 1, 'https://localhost:8080/openbanking/callback')
ON DUPLICATE KEY UPDATE redirect_uri = VALUES(redirect_uri);

-- ===== 테스트용 데이터 =====
INSERT INTO bank_customer (id, user_seq_no, login_id, password_hash, name, phone, status, created_at) VALUES
    (201, '1100000201', 'paging@test.com', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', '페이징 테스터', '01000000011', 'ACTIVE', NOW(6)),
    (202, '1100000202', 'rich@test.com', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', '고액 보유자', '01000000012', 'ACTIVE', NOW(6)),
    (203, '1100000203', 'poor@test.com', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', '잔액 부족', '01000000013', 'ACTIVE', NOW(6)),
    (204, '1100000204', 'lockedbank@test.com', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', '잠긴 은행 고객', '01000000014', 'LOCKED', NOW(6)),
    (205, '1100000205', 'closed@test.com', '$2a$12$yykgl2dDzclW5V0pj5LkXed5g58ewuqpR3ViMO56IjLbiwoM5oj/m', '해지 계좌 보유', '01000000015', 'ACTIVE', NOW(6))
ON DUPLICATE KEY UPDATE login_id = VALUES(login_id), password_hash = VALUES(password_hash), name = VALUES(name), phone = VALUES(phone), status = VALUES(status);

INSERT INTO bank_account (id, customer_id, fintech_use_num, bank_code_std, bank_name, account_num, account_num_masked,
                          account_alias, account_holder_name, account_type, balance_amt, status, version, created_at) VALUES
    (201, 201, '199900000000000000000201', '901', '한빛은행', '4001000000201', '400-***-0201', '거래내역 많은 통장', '페이징테스터', '1', 23517200, 'ACTIVE', 0, NOW(6)),
    (202, 201, '199900000000000000000202', '902', '새솔은행', '4101000000202', '410-***-0202', '빈 통장', '페이징테스터', '1', 0, 'ACTIVE', 0, NOW(6)),
    (203, 202, '199900000000000000000203', '901', '한빛은행', '4001000000203', '400-***-0203', '고액 통장', '고액보유자', '1', 5000000000, 'ACTIVE', 0, NOW(6)),
    (204, 203, '199900000000000000000204', '901', '한빛은행', '4001000000204', '400-***-0204', '용돈 통장', '잔액부족', '1', 1000, 'ACTIVE', 0, NOW(6)),
    (205, 204, '199900000000000000000205', '901', '한빛은행', '4001000000205', '400-***-0205', '잠긴 고객 통장', '잠긴고객', '1', 100000, 'ACTIVE', 0, NOW(6)),
    (206, 205, '199900000000000000000206', '901', '한빛은행', '4001000000206', '400-***-0206', '해지된 통장', '해지계좌', '1', 0, 'CLOSED', 0, NOW(6)),
    (207, 205, '199900000000000000000207', '902', '새솔은행', '4101000000207', '410-***-0207', '정상 통장', '해지계좌', '1', 500000, 'ACTIVE', 0, NOW(6))
ON DUPLICATE KEY UPDATE bank_name = VALUES(bank_name), account_alias = VALUES(account_alias), account_holder_name = VALUES(account_holder_name),
                        balance_amt = VALUES(balance_amt), status = VALUES(status);

-- 거래내역: 이 계좌들의 이체로 생긴 것이 아닌 기존 시험 거래만 지우고 다시 넣는다(이체로 생긴 거래는 남긴다)
DELETE FROM bank_transaction WHERE account_id IN (201, 202, 203, 204, 205, 206, 207) AND transfer_item_id IS NULL;

INSERT INTO bank_transaction (account_id, tran_at, inout_type, tran_type, print_content, tran_amt, after_balance_amt, branch_name, created_at) VALUES
    (201, NOW(6) - INTERVAL 85 DAY - INTERVAL 1310 MINUTE, 'OUT', '이체', '관리비', 86500, 32000, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 82 DAY - INTERVAL 941 MINUTE, 'OUT', '카드', '마트', 32000, 0, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 80 DAY - INTERVAL 1193 MINUTE, 'IN', '급여', '급여', 3200000, 3200000, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 78 DAY - INTERVAL 546 MINUTE, 'OUT', '카드', '교통비', 4500, 3195500, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 76 DAY - INTERVAL 1378 MINUTE, 'IN', '이체', '환급', 3200000, 6395500, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 74 DAY - INTERVAL 1102 MINUTE, 'OUT', '카드', '병원', 8900, 6386600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 71 DAY - INTERVAL 1046 MINUTE, 'OUT', '이체', '통신비', 12000, 6374600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 69 DAY - INTERVAL 471 MINUTE, 'OUT', '카드', '교통비', 700000, 5674600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 67 DAY - INTERVAL 252 MINUTE, 'IN', '급여', '급여', 300000, 5974600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 65 DAY - INTERVAL 998 MINUTE, 'IN', '이체', '환급', 850000, 6824600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 63 DAY - INTERVAL 884 MINUTE, 'IN', '이체', '용돈', 120000, 6944600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 61 DAY - INTERVAL 1149 MINUTE, 'OUT', '카드', '마트', 32000, 6912600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 58 DAY - INTERVAL 302 MINUTE, 'OUT', '이체', '관리비', 120000, 6792600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 56 DAY - INTERVAL 311 MINUTE, 'IN', '이체', '용돈', 850000, 7642600, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 54 DAY - INTERVAL 985 MINUTE, 'OUT', '이체', '월세', 8900, 7633700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 52 DAY - INTERVAL 503 MINUTE, 'OUT', '카드', '병원', 4500, 7629200, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 50 DAY - INTERVAL 1160 MINUTE, 'IN', '이체', '배당금', 1200000, 8829200, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 47 DAY - INTERVAL 36 MINUTE, 'IN', '급여', '급여', 50000, 8879200, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 45 DAY - INTERVAL 1244 MINUTE, 'OUT', '카드', '카페', 32000, 8847200, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 43 DAY - INTERVAL 883 MINUTE, 'IN', '급여', '급여', 850000, 9697200, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 41 DAY - INTERVAL 1028 MINUTE, 'IN', '이체', '배당금', 850000, 10547200, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 39 DAY - INTERVAL 1182 MINUTE, 'OUT', '카드', '병원', 4500, 10542700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 37 DAY - INTERVAL 760 MINUTE, 'OUT', '이체', '통신비', 480000, 10062700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 34 DAY - INTERVAL 182 MINUTE, 'IN', '이체', '배당금', 50000, 10112700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 32 DAY - INTERVAL 378 MINUTE, 'IN', '급여', '급여', 3200000, 13312700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 30 DAY - INTERVAL 828 MINUTE, 'IN', '이체', '환급', 3200000, 16512700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 28 DAY - INTERVAL 689 MINUTE, 'IN', '이체', '환급', 850000, 17362700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 26 DAY - INTERVAL 115 MINUTE, 'IN', '급여', '급여', 50000, 17412700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 23 DAY - INTERVAL 641 MINUTE, 'OUT', '이체', '관리비', 4500, 17408200, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 21 DAY - INTERVAL 223 MINUTE, 'OUT', '카드', '병원', 4500, 17403700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 19 DAY - INTERVAL 1243 MINUTE, 'OUT', '카드', '카페', 480000, 16923700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 17 DAY - INTERVAL 400 MINUTE, 'IN', '이체', '환급', 3200000, 20123700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 15 DAY - INTERVAL 23 MINUTE, 'IN', '이체', '환급', 3200000, 23323700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 13 DAY - INTERVAL 552 MINUTE, 'OUT', '이체', '관리비', 700000, 22623700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 10 DAY - INTERVAL 734 MINUTE, 'IN', '이체', '환급', 300000, 22923700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 8 DAY - INTERVAL 777 MINUTE, 'OUT', '이체', '월세', 120000, 22803700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 6 DAY - INTERVAL 1236 MINUTE, 'OUT', '이체', '통신비', 120000, 22683700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 4 DAY - INTERVAL 1088 MINUTE, 'IN', '이체', '용돈', 850000, 23533700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 2 DAY - INTERVAL 942 MINUTE, 'OUT', '이체', '월세', 12000, 23521700, '본점', NOW(6)),
    (201, NOW(6) - INTERVAL 0 DAY - INTERVAL 128 MINUTE, 'OUT', '카드', '마트', 4500, 23517200, '본점', NOW(6)),
    (203, NOW(6) - INTERVAL 60 DAY - INTERVAL 1242 MINUTE, 'OUT', '카드', '마트', 12000, 3000164000, '본점', NOW(6)),
    (203, NOW(6) - INTERVAL 48 DAY - INTERVAL 625 MINUTE, 'IN', '이체', '용돈', 1000000000, 4000164000, '본점', NOW(6)),
    (203, NOW(6) - INTERVAL 36 DAY - INTERVAL 104 MINUTE, 'OUT', '카드', '마트', 32000, 4000132000, '본점', NOW(6)),
    (203, NOW(6) - INTERVAL 24 DAY - INTERVAL 103 MINUTE, 'OUT', '이체', '통신비', 12000, 4000120000, '본점', NOW(6)),
    (203, NOW(6) - INTERVAL 12 DAY - INTERVAL 197 MINUTE, 'OUT', '이체', '월세', 120000, 4000000000, '본점', NOW(6)),
    (203, NOW(6) - INTERVAL 0 DAY - INTERVAL 1338 MINUTE, 'IN', '이체', '배당금', 1000000000, 5000000000, '본점', NOW(6)),
    (204, NOW(6) - INTERVAL 20 DAY - INTERVAL 978 MINUTE, 'OUT', '이체', '관리비', 32000, 787500, '본점', NOW(6)),
    (204, NOW(6) - INTERVAL 10 DAY - INTERVAL 876 MINUTE, 'OUT', '카드', '마트', 86500, 701000, '본점', NOW(6)),
    (204, NOW(6) - INTERVAL 0 DAY - INTERVAL 749 MINUTE, 'OUT', '카드', '병원', 700000, 1000, '본점', NOW(6)),
    (205, NOW(6) - INTERVAL 15 DAY - INTERVAL 797 MINUTE, 'OUT', '카드', '마트', 32000, 132000, '본점', NOW(6)),
    (205, NOW(6) - INTERVAL 0 DAY - INTERVAL 1046 MINUTE, 'OUT', '이체', '관리비', 32000, 100000, '본점', NOW(6)),
    (207, NOW(6) - INTERVAL 30 DAY - INTERVAL 472 MINUTE, 'OUT', '이체', '통신비', 8900, 467800, '본점', NOW(6)),
    (207, NOW(6) - INTERVAL 20 DAY - INTERVAL 1139 MINUTE, 'OUT', '카드', '병원', 8900, 458900, '본점', NOW(6)),
    (207, NOW(6) - INTERVAL 10 DAY - INTERVAL 1352 MINUTE, 'OUT', '이체', '통신비', 8900, 450000, '본점', NOW(6)),
    (207, NOW(6) - INTERVAL 0 DAY - INTERVAL 1132 MINUTE, 'IN', '이체', '배당금', 50000, 500000, '본점', NOW(6));
