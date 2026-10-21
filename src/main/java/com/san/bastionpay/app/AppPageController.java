package com.san.bastionpay.app;

import com.san.bastionpay.auth.AuthMember;
import com.san.bastionpay.core.exception.BusinessException;
import com.san.bastionpay.core.exception.ErrorCode;
import com.san.bastionpay.openbanking.OpenBankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 로그인 후 화면(대시보드, 연동 계좌, 가상계좌, 이체). 예시 데이터로 화면 구성을 확인하는 단계라
 * 어떤 값도 저장하지 않는다. 인증은 SecurityConfig 의 anyRequest().authenticated() 가 맡는다.
 */
@Slf4j
@Controller
public class AppPageController {

    private final OpenBankingService openBanking;

    public AppPageController(OpenBankingService openBanking) {
        this.openBanking = openBanking;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AuthMember member, Model model) {
        // 대시보드는 오픈뱅킹으로 연결한 실제 계좌의 합계만 보여 준다. 예금·적금·가상계좌는 아직 연동 전이라 싣지 않는다
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("hideSampleNote", true);
        model.addAttribute("configured", openBanking.isConfigured());
        boolean linked = openBanking.isLinked(member.id());
        if (linked) {
            try {
                model.addAttribute("summary", openBanking.summary(member.id()));
            } catch (BusinessException e) {
                log.warn("dashboard summary failed memberId={} code={} detail={}", member.id(), e.getErrorCode().code(), e.getMessage());
                if (e.getErrorCode() == ErrorCode.OPENBANKING_NOT_LINKED) {
                    linked = false;
                } else {
                    model.addAttribute("error", e.getErrorCode().message());
                }
            }
        }
        model.addAttribute("linked", linked);
        return "app/dashboard";
    }

    @GetMapping("/deposits")
    public String deposits(Model model) {
        model.addAttribute("activeNav", "deposits");
        model.addAttribute("deposits", SampleData.DEPOSITS);
        model.addAttribute("total", SampleData.totalDeposits());
        return "app/deposits";
    }

    @GetMapping("/savings")
    public String savings(Model model) {
        model.addAttribute("activeNav", "savings");
        model.addAttribute("savings", SampleData.SAVINGS);
        model.addAttribute("total", SampleData.totalSavings());
        return "app/savings";
    }

    @GetMapping("/virtual-accounts")
    public String virtualAccounts(Model model) {
        model.addAttribute("activeNav", "virtual");
        model.addAttribute("virtual", SampleData.VIRTUAL);
        model.addAttribute("totalVirtual", SampleData.totalVirtual());
        return "app/virtual-accounts";
    }

    @GetMapping("/virtual-accounts/{id}")
    public String virtualAccountDetail(@PathVariable String id, Model model) {
        var account = SampleData.virtual(id);
        model.addAttribute("activeNav", "virtual");
        model.addAttribute("account", account);
        model.addAttribute("txs", SampleData.transactions(account.balance()).stream().limit(8).toList());
        return "app/virtual-account-detail";
    }
}
