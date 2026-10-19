package com.san.bastionpay.app;

import com.san.bastionpay.app.SampleData.LinkedAccount;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * 로그인 후 화면(대시보드, 연동 계좌, 가상계좌, 이체). 예시 데이터로 화면 구성을 확인하는 단계라
 * 어떤 값도 저장하지 않는다. 인증은 SecurityConfig 의 anyRequest().authenticated() 가 맡는다.
 */
@Controller
public class AppPageController {

    private static final List<Integer> PERIOD_DAYS = List.of(7, 30, 90);

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // 대시보드는 종류별 합계만 보여 주고, 상세 내역은 각 상세 화면으로 안내한다
        long linked = SampleData.totalLinked();
        long deposits = SampleData.totalDeposits();
        long savings = SampleData.totalSavings();
        long virtual = SampleData.totalVirtual();
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("linkedCount", SampleData.LINKED.size());
        model.addAttribute("depositCount", SampleData.DEPOSITS.size());
        model.addAttribute("savingCount", SampleData.SAVINGS.size());
        model.addAttribute("virtualCount", SampleData.VIRTUAL.size());
        model.addAttribute("totalLinked", linked);
        model.addAttribute("totalDeposits", deposits);
        model.addAttribute("totalSavings", savings);
        model.addAttribute("totalVirtual", virtual);
        model.addAttribute("grandTotal", linked + deposits + savings + virtual);
        return "app/dashboard";
    }

    @GetMapping("/accounts")
    public String accounts(Model model) {
        model.addAttribute("activeNav", "accounts");
        model.addAttribute("linked", SampleData.LINKED);
        model.addAttribute("totalLinked", SampleData.totalLinked());
        return "app/accounts";
    }

    @GetMapping("/accounts/{id}")
    public String accountDetail(@PathVariable String id,
                                @RequestParam(defaultValue = "30") int days,
                                @RequestParam(defaultValue = "all") String kind,
                                Model model) {
        LinkedAccount account = SampleData.linked(id);
        int period = PERIOD_DAYS.contains(days) ? days : 30;
        String filter = List.of("in", "out").contains(kind) ? kind : "all";
        model.addAttribute("activeNav", "accounts");
        model.addAttribute("account", account);
        model.addAttribute("days", period);
        model.addAttribute("kind", filter);
        model.addAttribute("txs", SampleData.transactions(account.balance(), period, filter));
        return "app/account-detail";
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

    /** 연동 계좌 전체(또는 한 계좌)의 거래내역. */
    @GetMapping("/transactions")
    public String transactions(@RequestParam(defaultValue = "all") String account,
                               @RequestParam(defaultValue = "30") int days,
                               @RequestParam(defaultValue = "all") String kind,
                               Model model) {
        String selected = SampleData.LINKED.stream().anyMatch(a -> a.id().equals(account)) ? account : "all";
        int period = PERIOD_DAYS.contains(days) ? days : 30;
        String filter = List.of("in", "out").contains(kind) ? kind : "all";
        model.addAttribute("activeNav", "transactions");
        model.addAttribute("linked", SampleData.LINKED);
        model.addAttribute("account", selected);
        model.addAttribute("days", period);
        model.addAttribute("kind", filter);
        model.addAttribute("txs", SampleData.allTransactions(selected, period, filter));
        return "app/transactions";
    }

    @GetMapping("/connect")
    public String connect(Model model) {
        model.addAttribute("activeNav", "connect");
        return "app/connect";
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

    // ---------- 이체: 입력 → 확인 → 실행 (단계 분리) ----------

    @GetMapping("/transfer")
    public String transferForm(@RequestParam(required = false) String from, Model model) {
        String selected = SampleData.LINKED.stream().anyMatch(a -> a.id().equals(from)) ? from : "a1";
        return transferView(model, new TransferForm(selected, "", "", null, ""), "form");
    }

    @PostMapping("/transfer/confirm")
    public String transferConfirm(@Valid @ModelAttribute("transferForm") TransferForm form,
                                  BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("error", firstMessage(binding));
            return transferView(model, form, "form");
        }
        LinkedAccount from = SampleData.linked(form.fromAccountId());
        if (!SampleData.BANKS.contains(form.toBank())) {
            model.addAttribute("error", "받는 은행을 목록에서 선택해 주세요.");
            return transferView(model, form, "form");
        }
        if (form.amount() > from.available()) {
            model.addAttribute("error", "출금 가능 금액이 부족합니다.");
            return transferView(model, form, "form");
        }
        model.addAttribute("requestId", UUID.randomUUID().toString());
        model.addAttribute("balanceAfter", from.balance() - form.amount());
        model.addAttribute("maskedTo", mask(form.toAccount()));
        return transferView(model, form, "confirm");
    }

    /** 확인 화면에서 "내용 수정하기": 입력한 값을 그대로 채워 입력 화면으로 되돌린다. */
    @PostMapping("/transfer/edit")
    public String transferEdit(@ModelAttribute("transferForm") TransferForm form, Model model) {
        return transferView(model, form, "form");
    }

    @PostMapping("/transfer/execute")
    public String transferExecute(@Valid @ModelAttribute("transferForm") TransferForm form,
                                  BindingResult binding, @RequestParam String requestId, Model model) {
        // 실제 이체 단계에서는 확인 화면의 값을 신뢰하지 않고 서버가 다시 검증·조회한다
        if (binding.hasErrors() || !SampleData.BANKS.contains(form.toBank())
                || form.amount() > SampleData.linked(form.fromAccountId()).available()) {
            model.addAttribute("error", "입력 내용을 다시 확인해 주세요.");
            return transferView(model, form, "form");
        }
        model.addAttribute("requestId", requestId);
        model.addAttribute("maskedTo", mask(form.toAccount()));
        model.addAttribute("balanceAfter", SampleData.linked(form.fromAccountId()).balance() - form.amount());
        return transferView(model, form, "done");
    }

    private String transferView(Model model, TransferForm form, String stage) {
        model.addAttribute("activeNav", "transfer");
        model.addAttribute("stage", stage);
        model.addAttribute("transferForm", form);
        model.addAttribute("linked", SampleData.LINKED);
        model.addAttribute("banks", SampleData.BANKS);
        if (form.fromAccountId() != null && SampleData.LINKED.stream().anyMatch(a -> a.id().equals(form.fromAccountId()))) {
            model.addAttribute("from", SampleData.linked(form.fromAccountId()));
        }
        return "app/transfer";
    }

    /** 숫자 변환 실패(예: 금액에 쉼표·문자) 같은 기본 메시지는 사용자 문구로 바꾼다. */
    private static String firstMessage(BindingResult binding) {
        var error = binding.getAllErrors().get(0);
        if ("typeMismatch".equals(error.getCode())) return "이체 금액은 숫자만 입력해 주세요. (쉼표 없이)";
        return error.getDefaultMessage();
    }

    /** 받는 계좌번호는 화면에 뒤 4자리만 보여 준다. */
    private static String mask(String account) {
        String digits = account.replaceAll("[^0-9]", "");
        return digits.length() <= 4 ? digits : "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }
}
