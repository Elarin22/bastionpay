package com.san.bastionpay.auth;

import com.san.bastionpay.member.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Slf4j
@Controller
public class AuthController {

    private static final String LOGIN_FAILED = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(Authentication authentication, Model model,
                            @RequestParam(required = false) String required) {
        if (isLoggedIn(authentication)) return "redirect:/dashboard";
        // 보호된 화면에서 EntryPoint 가 보낸 경우. 원래 경로는 받지 않는다(오픈 리다이렉트 방지)
        if (required != null) model.addAttribute("notice", "로그인이 필요한 화면입니다. 로그인한 뒤 이용해 주세요.");
        model.addAttribute("loginForm", new LoginForm("", ""));
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginForm") LoginForm form, BindingResult binding,
                        HttpServletResponse response, Model model) {
        Optional<Member> member = binding.hasErrors()
                ? Optional.empty()
                : authService.authenticate(form.email(), form.password());
        if (member.isEmpty()) {
            log.warn("security_event type=LOGIN_FAILURE");
            model.addAttribute("error", binding.hasErrors() ? "이메일과 비밀번호를 입력해 주세요." : LOGIN_FAILED);
            model.addAttribute("loginForm", new LoginForm(form.email(), ""));   // 비밀번호는 되돌려 주지 않는다
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return "auth/login";
        }
        authService.startSession(member.get(), response);
        log.info("security_event type=LOGIN_SUCCESS memberId={}", member.get().getId());
        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        authService.endSession(request, response);
        return "redirect:/";
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthMember;
    }
}
