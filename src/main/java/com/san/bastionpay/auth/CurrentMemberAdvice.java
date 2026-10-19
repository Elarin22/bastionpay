package com.san.bastionpay.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 화면에서 헤더가 로그인 상태를 알 수 있게 한다. */
@ControllerAdvice
public class CurrentMemberAdvice {

    @ModelAttribute("currentMember")
    public AuthMember currentMember() {
        return resolve();
    }

    /** 예외 처리 뷰처럼 @ModelAttribute 가 적용되지 않는 곳에서도 쓴다. */
    public static AuthMember resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthMember member) {
            return member;
        }
        return null;
    }
}
