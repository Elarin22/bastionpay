package com.san.bastionpay.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 쿠키의 Access Token 을 검증해 SecurityContext 를 채운다.
 * Access Token 이 없거나 만료됐고 Refresh Token 이 있으면 여기서 조용히 회전해 새 쿠키를 내려준다.
 * 빈으로 등록하지 않고 SecurityConfig 에서 직접 생성해 보안 필터 체인에만 넣는다(서블릿 필터 중복 등록 방지).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 같은 요청의 ERROR 디스패치에서 다시 회전하지 않도록 첫 번째 결과를 담아 둔다. */
    private static final String MEMBER_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".MEMBER";

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final AuthCookies cookies;

    public JwtAuthenticationFilter(JwtService jwtService, RefreshTokenService refreshTokens, AuthCookies cookies) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
    }

    /** 에러 페이지 렌더링에서도 로그인 상태(헤더)가 유지되도록 ERROR 디스패치에서도 동작한다. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthMember member = (AuthMember) request.getAttribute(MEMBER_ATTRIBUTE);
        if (member == null) {
            member = authenticate(request, response).orElse(null);
        }
        if (member != null) {
            request.setAttribute(MEMBER_ATTRIBUTE, member);
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    member, null, member.authorities());
            SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.getContextHolderStrategy().setContext(context);
        }
        chain.doFilter(request, response);
    }

    private Optional<AuthMember> authenticate(HttpServletRequest request, HttpServletResponse response) {
        String access = AuthCookies.read(request, AuthCookies.ACCESS_TOKEN);
        if (access != null) {
            Optional<AuthMember> member = jwtService.parse(access);
            if (member.isPresent()) return member;
        }
        String refresh = AuthCookies.read(request, AuthCookies.REFRESH_TOKEN);
        if (refresh == null || !canRefresh(request)) {
            return Optional.empty();
        }
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refresh);
        switch (rotation.outcome()) {
            case ROTATED -> {
                cookies.writeAccessToken(response, jwtService.issueAccessToken(rotation.member()));
                cookies.writeRefreshToken(response, rotation.issued());
                return Optional.of(AuthMember.from(rotation.member()));
            }
            case INVALID -> {
                // 못 쓰는 토큰이 계속 전송되지 않도록 지운다
                cookies.clearAccessToken(response);
                cookies.clearRefreshToken(response);
            }
            case RACE -> { /* 다른 요청이 방금 새 쿠키를 내려줬을 수 있으므로 건드리지 않는다 */ }
        }
        return Optional.empty();
    }

    /**
     * 정적 리소스·로그아웃 요청에서는 회전하지 않는다. 한 화면이 CSS·JS 를 병렬로 받을 때
     * 같은 토큰이 여러 번 쓰이는 것을 줄이고, 로그아웃이 새 토큰을 만들지 않게 한다.
     */
    private static boolean canRefresh(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || path.startsWith("/fonts/") || path.equals("/favicon.ico")
                || path.startsWith("/actuator") || path.equals("/logout"));
    }
}
