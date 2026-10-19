package com.san.bastionpay.core.security;

import com.san.bastionpay.core.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 미인증 접근(401) 처리.
 * 화면 이동 요청은 /login 으로 보내고, fetch/htmx 요청은 상태 코드만 돌려준다.
 * 원래 경로를 URL 파라미터로 붙이지 않는다(오픈 리다이렉트 방지). 로그인 후에는 홈으로 간다.
 */
@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        log.warn("security_event type=AUTHENTICATION_REQUIRED method={} path={}",
                request.getMethod(), LogSanitizer.clean(request.getRequestURI()));
        if (isPageRequest(request)) {
            response.sendRedirect(request.getContextPath() + "/login?required");
        } else {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        }
    }

    /** 브라우저의 화면 이동은 Accept 에 text/html 이 들어 있다. fetch 기본값(*&#47;*)과 htmx 는 제외한다. */
    static boolean isPageRequest(HttpServletRequest request) {
        if (request.getHeader("HX-Request") != null) return false;
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) return false;
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
