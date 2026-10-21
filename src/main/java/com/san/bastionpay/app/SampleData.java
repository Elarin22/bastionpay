package com.san.bastionpay.app;

import com.san.bastionpay.core.exception.BusinessException;
import com.san.bastionpay.core.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 화면 구성 확인용 예시 데이터. 실제 계좌·거래와 연결되어 있지 않다.
 * 오픈뱅킹 연동(3단계)과 가상계좌·원장(4단계)이 만들어지면 서비스 계층으로 교체한다.
 */
final class SampleData {

    record VirtualAccount(String id, String alias, String maskedNumber, long balance, String status, LocalDate issuedAt) {
    }

    record Tx(LocalDateTime at, String memo, boolean deposit, long amount, long balanceAfter) {
    }

    record Deposit(String id, String bank, String name, String maskedNumber, long principal, String rate,
                   LocalDate startDate, LocalDate maturityDate) {
    }

    record Saving(String id, String bank, String name, String maskedNumber, long monthly, int paid, int total,
                  long balance, String rate, LocalDate maturityDate) {
    }

    static final List<VirtualAccount> VIRTUAL = List.of(
            new VirtualAccount("v1", "여행 자금", "BP-7201-4486-02", 450_000, "사용 중", LocalDate.now().minusDays(41)),
            new VirtualAccount("v2", "모임 회비", "BP-7201-9035-17", 1_120_000, "사용 중", LocalDate.now().minusDays(12)));

    static final List<Deposit> DEPOSITS = List.of(
            new Deposit("d1", "한빛은행", "정기예금 12개월", "120-***-553108", 5_000_000, "3.45",
                    LocalDate.now().minusDays(162), LocalDate.now().plusDays(203)),
            new Deposit("d2", "새솔은행", "새솔 큰만족 예금", "310-****-2284-09", 10_000_000, "3.60",
                    LocalDate.now().minusDays(58), LocalDate.now().plusDays(307)));

    static final List<Saving> SAVINGS = List.of(
            new Saving("s1", "한빛은행", "청년 도약 적금", "130-***-770294", 300_000, 7, 12, 2_100_000, "3.90",
                    LocalDate.now().plusDays(152)),
            new Saving("s2", "푸른저축은행", "목돈 마련 적금", "208-***-114760", 500_000, 4, 24, 2_000_000, "4.10",
                    LocalDate.now().plusDays(610)));

    /** 거래 정의: {일 전, 시, 분, 내용, 부호(+입금/-출금), 금액} */
    private static final Object[][] PATTERN = {
            {0, 9, 12, "편의점", -1, 4_800L}, {1, 18, 40, "점심 식대", -1, 12_000L},
            {2, 8, 5, "교통비 충전", -1, 30_000L}, {3, 14, 22, "친구 정산", +1, 25_000L},
            {4, 11, 30, "구독 서비스", -1, 13_900L}, {6, 9, 0, "급여", +1, 2_850_000L},
            {7, 20, 15, "온라인 쇼핑", -1, 64_500L}, {9, 12, 48, "점심 식대", -1, 9_500L},
            {11, 16, 3, "병원비", -1, 18_000L}, {13, 10, 25, "환급금", +1, 74_000L},
            {15, 19, 51, "저녁 식대", -1, 38_000L}, {18, 13, 7, "공과금", -1, 96_300L},
            {21, 9, 33, "이자", +1, 1_240L}, {24, 15, 44, "도서 구입", -1, 22_000L},
            {27, 11, 11, "친구 정산", +1, 40_000L}, {31, 8, 20, "월세", -1, 500_000L},
            {35, 17, 9, "급여", +1, 2_850_000L}, {41, 12, 2, "보험료", -1, 87_000L},
            {52, 10, 40, "용돈", +1, 100_000L}, {64, 14, 14, "온라인 쇼핑", -1, 45_900L},
            {78, 9, 55, "급여", +1, 2_850_000L}, {85, 18, 28, "여행 경비", -1, 320_000L}};

    private SampleData() {
    }

    static VirtualAccount virtual(String id) {
        return VIRTUAL.stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "virtual account " + id));
    }

    /** 현재 잔액에서 거꾸로 계산해 "거래 후 잔액"을 맞춘 거래내역(최신순). */
    static List<Tx> transactions(long currentBalance) {
        List<Tx> list = new ArrayList<>();
        long balance = currentBalance;
        for (Object[] p : PATTERN) {
            long signed = ((int) p[4]) * (long) p[5];
            LocalDateTime at = LocalDate.now().minusDays((int) p[0]).atTime((int) p[1], (int) p[2]);
            list.add(new Tx(at, (String) p[3], signed > 0, Math.abs(signed), balance));
            balance -= signed;
        }
        return list;
    }

    static List<Tx> transactions(long currentBalance, int days, String kind) {
        LocalDateTime from = LocalDate.now().minusDays(days).atStartOfDay();
        return transactions(currentBalance).stream()
                .filter(t -> !t.at().isBefore(from))
                .filter(t -> switch (kind) {
                    case "in" -> t.deposit();
                    case "out" -> !t.deposit();
                    default -> true;
                })
                .toList();
    }

    static long totalDeposits() {
        return DEPOSITS.stream().mapToLong(Deposit::principal).sum();
    }

    static long totalSavings() {
        return SAVINGS.stream().mapToLong(Saving::balance).sum();
    }

    static long totalVirtual() {
        return VIRTUAL.stream().mapToLong(VirtualAccount::balance).sum();
    }
}
