package com.san.bastionpay.auth;

import com.san.bastionpay.member.Member;
import com.san.bastionpay.member.MemberRepository;
import com.san.bastionpay.member.MemberStatus;
import com.san.bastionpay.signup.SignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    /** 없는 이메일도 해시 비교 시간을 쓰게 해 응답 시간으로 계정 존재 여부를 알 수 없게 한다. */
    private final String dummyHash;

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final AuthCookies cookies;

    public AuthService(MemberRepository members, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokens, AuthCookies cookies) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** 실패 사유(없는 계정/틀린 비밀번호/잠긴 계정)는 호출자에게 구분해 주지 않는다. */
    public Optional<Member> authenticate(String email, String password) {
        Optional<Member> found = members.findByEmail(SignupService.normalize(email));
        boolean matches = passwordEncoder.matches(password, found.map(Member::getPasswordHash).orElse(dummyHash));
        return found.filter(m -> matches && m.getStatus() == MemberStatus.ACTIVE);
    }

    /** 로그인·가입 직후: Access Token 과 새 family 의 Refresh Token 을 쿠키로 내려준다. */
    public void startSession(Member member, HttpServletResponse response) {
        cookies.writeAccessToken(response, jwtService.issueAccessToken(member));
        cookies.writeRefreshToken(response, refreshTokens.issue(member));
    }

    /** 로그아웃: 이 브라우저의 Refresh Token 계열을 폐기하고 쿠키를 지운다. */
    public void endSession(HttpServletRequest request, HttpServletResponse response) {
        String refresh = AuthCookies.read(request, AuthCookies.REFRESH_TOKEN);
        if (refresh != null) refreshTokens.revokeFamilyOf(refresh);
        cookies.clearAccessToken(response);
        cookies.clearRefreshToken(response);
    }
}
