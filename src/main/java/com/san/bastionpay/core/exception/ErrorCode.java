package com.san.bastionpay.core.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류의 상태·코드·사용자용 문구를 한 곳에서 관리한다.
 * message 는 화면에 그대로 보이는 문구만 쓴다. 내부 원인은 BusinessException 의 detail(서버 로그 전용)에 남긴다.
 * 접두어: C 공통, A 계좌·이체, O 오픈뱅킹 (M 회원, T 토큰은 해당 단계에서 추가)
 */
public enum ErrorCode {

    INVALID_INPUT("C001", HttpStatus.BAD_REQUEST, "요청 내용을 확인한 뒤 다시 시도해 주세요."),
    METHOD_NOT_ALLOWED("C002", HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않는 요청 방식입니다. 화면의 버튼이나 링크로 다시 시도해 주세요."),
    UNAUTHORIZED("C003", HttpStatus.UNAUTHORIZED, "이 페이지는 로그인한 뒤에 볼 수 있습니다. 로그인하고 다시 시도해 주세요."),
    FORBIDDEN("C004", HttpStatus.FORBIDDEN, "이 페이지에 접근할 권한이 없습니다. 방금까지 잘 되던 화면이라면 페이지를 새로고침한 뒤 다시 시도해 주세요."),
    NOT_FOUND("C005", HttpStatus.NOT_FOUND, "주소가 바뀌었거나 없는 페이지입니다. 주소를 다시 확인해 주세요."),
    TOO_MANY_REQUESTS("C006", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR("C999", HttpStatus.INTERNAL_SERVER_ERROR,
            "서버에서 문제가 생겼습니다. 잠시 후 다시 시도해 주세요. 계속되면 아래 참조 번호를 전달해 주세요."),

    // 계좌·이체 (타인 계좌 접근도 403 대신 ACCOUNT_NOT_FOUND 로 응답해 존재 여부를 숨긴다)
    ACCOUNT_NOT_FOUND("A001", HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다."),
    INSUFFICIENT_BALANCE("A002", HttpStatus.BAD_REQUEST, "잔액이 부족합니다."),
    DUPLICATE_TRANSFER("A003", HttpStatus.CONFLICT, "이미 처리된 이체 요청입니다."),

    // 오픈뱅킹
    OPENBANKING_API_ERROR("O001", HttpStatus.BAD_GATEWAY, "오픈뱅킹 연동에 문제가 있습니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
