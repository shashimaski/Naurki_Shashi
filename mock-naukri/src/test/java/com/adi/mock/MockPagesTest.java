package com.adi.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Happy-path MockMvc tests for the mock Naukri server.
 * TDD: tests were written before implementation routes existed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MockPagesTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MockState mockState;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void reset() {
        mockState.reset();
    }

    /** GET /nlogin/login returns 200 with the login form. */
    @Test
    void login_page_ok() throws Exception {
        mvc.perform(get("/nlogin/login"))
                .andExpect(status().isOk());
    }

    /** POST with bad@ email stays on login page with error text. */
    @Test
    void bad_login_stays() throws Exception {
        mvc.perform(post("/nlogin/login")
                        .param("email", "bad@x.com")
                        .param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Wrong credentials")));
    }

    /** POST with valid email → 302 redirect to homepage and MOCK_SESSION cookie set. */
    @Test
    void good_login_redirects() throws Exception {
        mvc.perform(post("/nlogin/login")
                        .param("email", "ok@x.com")
                        .param("password", "pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mnjuser/homepage"))
                .andExpect(cookie().exists("MOCK_SESSION"));
    }

    /** POST with otp@ email → 302 redirect to /otp. */
    @Test
    void otp_login_redirects() throws Exception {
        mvc.perform(post("/nlogin/login")
                        .param("email", "otp@x.com")
                        .param("password", "any"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/otp"));
    }

    /** GET /nlogin/logout clears session cookie and redirects to login page. */
    @Test
    void get_logout_redirects_to_login() throws Exception {
        mvc.perform(get("/nlogin/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/nlogin/login"));
    }

    /**
     * Guard 1 coverage: GET / with a valid session cookie must redirect to the
     * dashboard, mirroring real Naukri. The automator's already-logged-in
     * short-circuit fires the moment page.navigate(baseUrl) lands on a URL
     * matching DASHBOARD_URL.
     */
    @Test
    void root_with_session_redirects_to_dashboard() throws Exception {
        mvc.perform(get("/").cookie(new jakarta.servlet.http.Cookie("MOCK_SESSION", "ok@x.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mnjuser/homepage"));
    }

    /** GET / without a session goes to the login page. */
    @Test
    void root_without_session_redirects_to_login() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/nlogin/login"));
    }

    /**
     * Guard 2 coverage: GET /nlogin/login with a valid session cookie must
     * redirect to /mnjuser/homepage rather than serving the login form. This
     * mirrors real Naukri's server behaviour and is what makes the automator's
     * Guard 2 short-circuit fire when the browser has a persisted session.
     */
    @Test
    void login_get_with_session_redirects_to_dashboard() throws Exception {
        mvc.perform(get("/nlogin/login").cookie(new jakarta.servlet.http.Cookie("MOCK_SESSION", "ok@x.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mnjuser/homepage"));
    }

    /** GET /nlogin/login without a session serves the form as before. */
    @Test
    void login_get_without_session_serves_form() throws Exception {
        mvc.perform(get("/nlogin/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("usernameField")));
    }

    /**
     * Drawer coverage: dashboard.html must contain the real-Naukri logout
     * anchor (a[data-type='logoutLink'] with title='Logout') so the automator's
     * drawer-based logout fallback has something to click.
     */
    @Test
    void dashboard_contains_drawer_logout_anchor() throws Exception {
        String body = mvc.perform(get("/mnjuser/homepage")
                        .cookie(new jakarta.servlet.http.Cookie("MOCK_SESSION", "ok@x.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("data-type=\"logoutLink\"");
        assertThat(body).contains("title=\"Logout\"");
        assertThat(body).contains("class=\"drawer-wrapper\"");
        assertThat(body).contains("class=\"view-profile-wrapper\"");
    }

    /** POST /nlogin/logout still works (backward compat). */
    @Test
    void post_logout_still_works() throws Exception {
        mvc.perform(post("/nlogin/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/nlogin/login"));
    }

    /** Cloud-gateway resume URL returns PDF bytes with attachment header. */
    @Test
    void cloud_gateway_resume_url_returns_pdf() throws Exception {
        mvc.perform(get(
                "/cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/abc123/resume"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("pdf")));
    }

    /** POST /_mock/reset clears state; subsequent GET /_mock/state shows headlineSaveCount == 0. */
    @Test
    void state_reset_works() throws Exception {
        // Drive up the count
        mvc.perform(post("/mnjuser/profile/headline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"Manager\"}"))
                .andExpect(status().isOk());

        // Reset
        mvc.perform(post("/_mock/reset"))
                .andExpect(status().isOk());

        // Verify
        String body = mvc.perform(get("/_mock/state"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> stateMap = json.readValue(body, java.util.Map.class);
        assertThat(((Number) stateMap.get("headlineSaveCount")).intValue()).isEqualTo(0);
    }
}
