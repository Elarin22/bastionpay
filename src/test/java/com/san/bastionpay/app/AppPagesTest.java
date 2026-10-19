package com.san.bastionpay.app;

import com.san.bastionpay.auth.AuthMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로그인 후 화면의 접근 제어와 구성(사이드바, 대시보드 요약) 검증. 로컬 MySQL 이 떠 있어야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AppPagesTest {

    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor user() {
        AuthMember m = new AuthMember(1L, "테스터", "USER", "STANDARD");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(m, null, m.authorities()));
    }

    private String page(String path) throws Exception {
        return mvc.perform(get(path).with(user())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void everyAppPageRequiresLogin() throws Exception {
        for (String path : new String[]{"/dashboard", "/accounts", "/accounts/a1", "/deposits", "/savings",
                "/virtual-accounts", "/virtual-accounts/v1", "/transactions", "/transfer", "/connect"}) {
            mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isFound()).andExpect(header().string("Location", "/login?required"));
        }
    }

    @Test
    void dashboardShowsOnlyTotalsAndLinksToDetailPages() throws Exception {
        String html = page("/dashboard");

        assertThat(html).contains("자산 현황", "입출금 계좌", "예금", "적금", "가상계좌", "총 자산", "37,210,700원");
        assertThat(html).contains("href=\"/accounts\"", "href=\"/deposits\"", "href=\"/savings\"", "href=\"/virtual-accounts\"");
        // 상세 내역(최근 거래, 계좌번호)은 대시보드에 두지 않는다
        assertThat(html).doesNotContain("최근 거래", "110-***-482915");
    }

    @Test
    void headerKeepsOnlyFrequentItemsAndMegaMenuHoldsEverything() throws Exception {
        String html = page("/deposits");

        // 헤더 가운데 카테고리는 3개만
        String center = html.substring(html.indexOf("class=\"site-nav-center\""), html.indexOf("class=\"site-actions\""));
        assertThat(center).contains("대시보드", "이체", "거래내역").doesNotContain("예금", "적금", "가상계좌", "오픈뱅킹");

        // 전체 메뉴에는 모든 항목이 카테고리별로 있다
        String mega = html.substring(html.indexOf("id=\"mega-menu\""));
        assertThat(mega).contains("이용", "자산", "연결", "입출금 계좌", "예금", "적금", "가상계좌", "오픈뱅킹 연결", "로그아웃");
        assertThat(mega).contains("href=\"/accounts\"", "href=\"/deposits\"", "href=\"/savings\"",
                "href=\"/virtual-accounts\"", "href=\"/connect\"", "href=\"/transfer\"", "href=\"/transactions\"");
        // 현재 페이지 표시, 사이드바는 더 이상 없다
        assertThat(mega).contains("<a href=\"/deposits\" aria-current=\"page\">");
        assertThat(html).doesNotContain("class=\"sidebar\"");
    }

    @Test
    void megaMenuIsClosedByDefaultAndToggleIsAccessible() throws Exception {
        String html = page("/dashboard");

        assertThat(html).containsPattern("id=\"mega-menu\"[^>]*hidden");
        assertThat(html).contains("aria-controls=\"mega-menu\"", "aria-expanded=\"false\"", "aria-label=\"전체 메뉴\"");
    }

    @Test
    void savingsAndDepositsShowProductDetails() throws Exception {
        assertThat(page("/savings")).contains("청년 도약 적금", "7 / 12회 납입", "3.90%");
        assertThat(page("/deposits")).contains("정기예금 12개월", "3.45%", "만기일");
    }

    @Test
    void transactionsFilterByAccountAndKind() throws Exception {
        String all = page("/transactions");
        String onlyIn = page("/transactions?account=a1&days=90&kind=in");

        // 계좌 선택 목록에는 모든 계좌가 있으므로 표의 계좌 칸만 본다
        String shinsol = "data-label=\"계좌\">새솔은행 생활비";
        assertThat(all).contains("data-label=\"계좌\">한빛은행 급여 통장", shinsol);
        assertThat(onlyIn).contains("data-label=\"계좌\">한빛은행 급여 통장").doesNotContain(shinsol).doesNotContain("class=\"tag\">출금<");
    }

    @Test
    void unknownFilterValuesFallBackToDefaults() throws Exception {
        assertThat(page("/transactions?account=zz&days=9999&kind=zz")).contains("전체 계좌", "최근 1개월");
    }
}
