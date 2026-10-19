package com.san.bastionpay.app;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 이체 입력값. 화면 확인용 단계에서도 서버가 직접 검증한다(금액 검증은 항상 서버에서). */
public record TransferForm(
        @NotBlank(message = "출금 계좌를 선택해 주세요.")
        String fromAccountId,

        @NotBlank(message = "받는 은행을 선택해 주세요.")
        String toBank,

        @NotBlank(message = "받는 계좌번호를 입력해 주세요.")
        @Pattern(regexp = "^[0-9-]{8,20}$", message = "계좌번호는 숫자와 하이픈(-)으로 8~20자까지 입력할 수 있습니다.")
        String toAccount,

        @NotNull(message = "이체 금액을 입력해 주세요.")
        @Min(value = 1, message = "이체 금액은 1원 이상이어야 합니다.")
        @Max(value = 10_000_000, message = "1회 이체 한도는 1,000만원입니다.")
        Long amount,

        @Size(max = 20, message = "받는 분 통장 표시는 20자까지 입력할 수 있습니다.")
        String memo) {
}
