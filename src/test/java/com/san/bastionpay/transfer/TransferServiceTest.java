package com.san.bastionpay.transfer;

import com.san.bastionpay.core.exception.BusinessException;
import com.san.bastionpay.core.exception.ErrorCode;
import com.san.bastionpay.member.MemberRepository;
import com.san.bastionpay.openbanking.OpenBankingClient;
import com.san.bastionpay.openbanking.OpenBankingClient.Receiver;
import com.san.bastionpay.openbanking.OpenBankingService;
import com.san.bastionpay.openbanking.TransferUnknownException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이체(출금 → 입금, 실패 시 환불)의 상태 처리. 은행 호출은 대체하고 DB 는 실제로 쓴다(롤백).
 * 로컬 MySQL 과 db/seed/member.sql 의 더미 회원이 필요하다.
 */
@SpringBootTest
@Transactional
class TransferServiceTest {

    private static final String FIN = "199900000000000000000002";
    private static final String REQUEST = "11111111-1111-1111-1111-111111111111";

    @Autowired TransferService service;
    @Autowired TransferRequestRepository requests;
    @Autowired MemberRepository members;

    @MockitoBean OpenBankingClient client;
    @MockitoBean OpenBankingService openBanking;

    private long memberId;

    @BeforeEach
    void setUp() {
        memberId = members.findByEmail("test@test.com").orElseThrow().getId();
        var account = new OpenBankingClient.Account(FIN, "월급 통장", "한빛은행", "100-***-8901", "홍*동", true);
        when(openBanking.transferSource(memberId, FIN)).thenReturn(new OpenBankingService.TransferSource("user-token", account));
        when(client.newBankTranId()).thenReturn("M202610001UAAAAAAAA1", "M202610001UAAAAAAAA2", "M202610001UAAAAAAAA3");
        when(client.clientToken()).thenReturn("org-token");
    }

    private TransferService.Outcome run() {
        return service.transfer(memberId, REQUEST, FIN, "901", "2003456789012", 5_000, "용돈");
    }

    @Test
    void successWithdrawsThenDepositsAndStoresOnlyMaskedReceiver() {
        when(client.deposit(any(), any(), any(), anyLong(), any())).thenReturn(new OpenBankingClient.DepositResult("브***피"));

        TransferService.Outcome out = run();

        assertThat(out.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(out.toHolderMasked()).isEqualTo("브***피");
        assertThat(out.replayed()).isFalse();
        InOrder order = inOrder(client);
        order.verify(client).withdraw("user-token", "M202610001UAAAAAAAA1", FIN, 5_000, "BastionPay 이체");
        order.verify(client).clientToken();
        order.verify(client).deposit("org-token", "M202610001UAAAAAAAA2", new Receiver.ByAccount("901", "2003456789012"), 5_000, "용돈");
        TransferRequest saved = requests.findByMemberIdAndRequestId(memberId, REQUEST).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(saved.getToAccountMasked()).isEqualTo("200-***-9012").doesNotContain("2003456789012");
        assertThat(saved.getWithdrawTranId()).isEqualTo("M202610001UAAAAAAAA1");
        assertThat(saved.getDepositTranId()).isEqualTo("M202610001UAAAAAAAA2");
        assertThat(saved.getRefundTranId()).isNull();
    }

    @Test
    void rejectedWithdrawalFailsWithoutTouchingTheReceiver() {
        when(client.withdraw(any(), any(), any(), anyLong(), any())).thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액 부족"));

        assertThatThrownBy(this::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));

        verify(client, never()).clientToken();
        verify(client, never()).deposit(any(), any(), any(), anyLong(), any());
        TransferRequest saved = requests.findByMemberIdAndRequestId(memberId, REQUEST).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE.code());
    }

    @Test
    void unknownWithdrawalResultNeedsReviewAndIsNeverRetriedOrRefunded() {
        when(client.withdraw(any(), any(), any(), anyLong(), any())).thenThrow(new TransferUnknownException("timeout"));

        TransferService.Outcome out = run();

        assertThat(out.status()).isEqualTo(TransferStatus.NEEDS_REVIEW);
        verify(client, never()).clientToken();
        verify(client, never()).deposit(any(), any(), any(), anyLong(), any());
        verify(client, times(1)).withdraw(any(), any(), any(), anyLong(), any());
        assertThat(requests.findByMemberIdAndRequestId(memberId, REQUEST).orElseThrow().getFailureCode()).isEqualTo("WITHDRAW_UNKNOWN");
    }

    @Test
    void rejectedDepositRefundsTheWithdrawnMoneyToTheSender() {
        when(client.deposit(eq("org-token"), eq("M202610001UAAAAAAAA2"), any(Receiver.ByAccount.class), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "받는 계좌 없음"));
        when(client.deposit(eq("org-token"), eq("M202610001UAAAAAAAA3"), any(Receiver.ByFintechUseNum.class), anyLong(), any()))
                .thenReturn(new OpenBankingClient.DepositResult("홍*동"));

        TransferService.Outcome out = run();

        assertThat(out.status()).isEqualTo(TransferStatus.REFUNDED);
        assertThat(out.message()).isEqualTo("받는 계좌를 찾을 수 없습니다.");
        // 환불은 출금했던 내 계좌로, 같은 금액으로, 새 거래고유번호로 한 번만 보낸다
        verify(client).deposit("org-token", "M202610001UAAAAAAAA3", new Receiver.ByFintechUseNum(FIN), 5_000, "BastionPay 이체 취소");
        TransferRequest saved = requests.findByMemberIdAndRequestId(memberId, REQUEST).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TransferStatus.REFUNDED);
        assertThat(saved.getFailureCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND.code());
        assertThat(saved.getRefundTranId()).isEqualTo("M202610001UAAAAAAAA3");
    }

    @Test
    void failedRefundNeedsReviewAndRefundIsTriedOnlyOnce() {
        when(client.deposit(any(), any(), any(Receiver.ByAccount.class), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "받는 계좌 없음"));
        when(client.deposit(any(), any(), any(Receiver.ByFintechUseNum.class), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.OPENBANKING_API_ERROR, "환불 거절"));

        TransferService.Outcome out = run();

        assertThat(out.status()).isEqualTo(TransferStatus.NEEDS_REVIEW);
        verify(client, times(1)).deposit(any(), any(), any(Receiver.ByFintechUseNum.class), anyLong(), any());
        assertThat(requests.findByMemberIdAndRequestId(memberId, REQUEST).orElseThrow().getFailureCode()).isEqualTo("REFUND_FAILED");
    }

    @Test
    void unknownDepositResultNeedsReviewWithoutRefund() {
        when(client.deposit(any(), any(), any(Receiver.ByAccount.class), anyLong(), any())).thenThrow(new TransferUnknownException("timeout"));

        TransferService.Outcome out = run();

        assertThat(out.status()).isEqualTo(TransferStatus.NEEDS_REVIEW);
        // 입금이 됐는지 모르므로 환불하지 않는다(이중으로 돌려줄 수 있다)
        verify(client, never()).deposit(any(), any(), any(Receiver.ByFintechUseNum.class), anyLong(), any());
        assertThat(requests.findByMemberIdAndRequestId(memberId, REQUEST).orElseThrow().getFailureCode()).isEqualTo("DEPOSIT_UNKNOWN");
    }

    @Test
    void orgTokenFailureAfterWithdrawalNeedsReview() {
        when(client.clientToken()).thenThrow(new BusinessException(ErrorCode.OPENBANKING_AUTH_FAILED, "토큰 실패"));

        TransferService.Outcome out = run();

        assertThat(out.status()).isEqualTo(TransferStatus.NEEDS_REVIEW);
        verify(client, never()).deposit(any(), any(), any(), anyLong(), any());
        assertThat(requests.findByMemberIdAndRequestId(memberId, REQUEST).orElseThrow().getFailureCode()).isEqualTo("ORG_TOKEN_FAILED");
    }

    @Test
    void sameRequestIdIsExecutedOnlyOnce() {
        when(client.deposit(any(), any(), any(), anyLong(), any())).thenReturn(new OpenBankingClient.DepositResult("브***피"));

        TransferService.Outcome first = run();
        TransferService.Outcome second = run();

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.status()).isEqualTo(TransferStatus.COMPLETED);
        verify(client, times(1)).withdraw(any(), any(), any(), anyLong(), any());     // 더블클릭·새로고침으로 두 번 출금되지 않는다
        verify(client, times(1)).deposit(any(), any(), any(), anyLong(), any());
        assertThat(requests.count()).isEqualTo(1);
    }

    @Test
    void resendingARejectedRequestIsRefusedWithoutCallingTheBank() {
        when(client.withdraw(any(), any(), any(), anyLong(), any())).thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "x"));
        assertThatThrownBy(this::run).isInstanceOf(BusinessException.class);

        assertThatThrownBy(this::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_TRANSFER));
        verify(client, times(1)).withdraw(any(), any(), any(), anyLong(), any());
    }

    @Test
    void sameRequestIdOfAnotherMemberIsIndependent() {
        when(client.deposit(any(), any(), any(), anyLong(), any())).thenReturn(new OpenBankingClient.DepositResult("브***피"));
        long other = members.findByEmail("vip@test.com").orElseThrow().getId();
        var account = new OpenBankingClient.Account(FIN, "월급 통장", "한빛은행", "100-***-8901", "홍*동", true);
        when(openBanking.transferSource(other, FIN)).thenReturn(new OpenBankingService.TransferSource("other-token", account));

        run();
        TransferService.Outcome second = service.transfer(other, REQUEST, FIN, "901", "2003456789012", 5_000, null);

        assertThat(second.replayed()).isFalse();
        assertThat(requests.count()).isEqualTo(2);
    }

    @Test
    void invalidInputIsRejectedBeforeAnythingIsRecorded() {
        String[][] bad = {{"not-a-uuid", "901", "2003456789012", "5000"}, {REQUEST, "90", "2003456789012", "5000"},
                {REQUEST, "901", "1234", "5000"}, {REQUEST, "901", "20034-5678", "5000"}, {REQUEST, "901", "2003456789012", "0"},
                {REQUEST, "901", "2003456789012", "-5"}};
        for (String[] b : bad) {
            assertThatThrownBy(() -> service.transfer(memberId, b[0], FIN, b[1], b[2], Long.parseLong(b[3]), null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
        assertThatThrownBy(() -> service.transfer(memberId, REQUEST, FIN, "901", "2003456789012", 10_000_001, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_LIMIT_EXCEEDED));

        assertThat(requests.count()).isZero();
        verify(client, never()).withdraw(any(), anyString(), any(), anyLong(), any());
    }

    @Test
    void accountThatMayNotTransferIsRejectedBeforeAnythingIsRecorded() {
        when(openBanking.transferSource(memberId, FIN)).thenThrow(new BusinessException(ErrorCode.TRANSFER_NOT_PERMITTED, "이체 동의 없음"));

        assertThatThrownBy(this::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_NOT_PERMITTED));

        assertThat(requests.count()).isZero();
        verify(client, never()).withdraw(any(), any(), any(), anyLong(), any());
    }
}
