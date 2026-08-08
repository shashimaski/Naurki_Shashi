package com.adi.mock;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * All mock Naukri routes in one controller.
 */
@Controller
public class PageController {

    private static final String SESSION_COOKIE = "MOCK_SESSION";

    private final MockState state;

    public PageController(MockState state) {
        this.state = state;
    }

    // ─── Root ────────────────────────────────────────────────────────────────
    // Real Naukri: GET / returns the marketing homepage AND, if the user has a
    // valid session cookie, redirects to /mnjuser/homepage. Mock mirrors so the
    // automator's Guard-1 already-logged-in short-circuit is testable.

    @GetMapping("/")
    public String rootPage(HttpServletRequest request) {
        if (hasSession(request)) {
            return "redirect:/mnjuser/homepage";
        }
        return "redirect:/nlogin/login";
    }

    // ─── Login ───────────────────────────────────────────────────────────────
    // Real Naukri: if the user visits /nlogin/login with a still-valid session
    // cookie, they're bounced to /mnjuser/homepage instead of seeing the login
    // form. Mock mirrors this so the automator's Guard-2 short-circuit is
    // exercised by integration tests.

    @GetMapping("/nlogin/login")
    public String loginPage(HttpServletRequest request) {
        if (hasSession(request)) {
            return "redirect:/mnjuser/homepage";
        }
        return "login";
    }

    @PostMapping("/nlogin/login")
    public String doLogin(
            @RequestParam String email,
            @RequestParam String password,
            Model model,
            HttpServletResponse response) {

        if (email.startsWith("bad@")) {
            model.addAttribute("error", "Wrong credentials");
            return "login";
        }
        if (email.startsWith("otp@")) {
            return "redirect:/otp";
        }

        Cookie cookie = new Cookie(SESSION_COOKIE, email);
        cookie.setPath("/");
        cookie.setMaxAge(86400);
        response.addCookie(cookie);
        return "redirect:/mnjuser/homepage";
    }

    // ─── OTP ─────────────────────────────────────────────────────────────────

    @GetMapping("/otp")
    public String otpPage() {
        return "otp";
    }

    @PostMapping("/otp")
    public String doOtp(
            @RequestParam(required = false) String otp,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Pick up whatever email cookie may exist or use a placeholder
        String email = resolveEmail(request, "otp-user");
        Cookie cookie = new Cookie(SESSION_COOKIE, email);
        cookie.setPath("/");
        cookie.setMaxAge(86400);
        response.addCookie(cookie);
        return "redirect:/mnjuser/homepage";
    }

    // ─── Dashboard ───────────────────────────────────────────────────────────

    @GetMapping("/mnjuser/homepage")
    public String dashboard(HttpServletRequest request, Model model) {
        if (!hasSession(request)) {
            return "redirect:/nlogin/login";
        }
        return "dashboard";
    }

    // ─── Profile ─────────────────────────────────────────────────────────────

    /**
     * Fixed mock profile hash used in the download URL. Real Naukri uses a
     * per-account hex hash embedded in the resume-download URL:
     *   /cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/&lt;hash&gt;/resume
     * We hardcode a placeholder so integration tests can predict the URL if needed.
     */
    private static final String MOCK_PROFILE_HASH =
            "974fc069c8ff92c2e154c5b3395ef58fcb803ebd6429fd0f5c4f31944917d4da";

    @GetMapping("/mnjuser/profile")
    public String profile(HttpServletRequest request, Model model) {
        if (!hasSession(request)) {
            return "redirect:/nlogin/login";
        }
        model.addAttribute("headline", state.getLastHeadline());
        model.addAttribute("uploadedResumeName", state.getUploadedResumeName());
        model.addAttribute("profileHash", MOCK_PROFILE_HASH);
        return "profile";
    }

    @PostMapping(value = "/mnjuser/profile/headline",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> saveHeadline(@RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value != null) {
            state.setLastHeadline(value);
            state.incrementHeadlineSaveCount();
        }
        return Map.of("ok", true);
    }

    @GetMapping("/mnjuser/profile/resume")
    public ResponseEntity<byte[]> downloadResume() throws IOException {
        return respondWithMockResume();
    }

    /**
     * Real Naukri fires the download from a JS click handler that GETs
     * {@code /cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/<hash>/resume}.
     * Mock exposes the same path so the automation's {@code Page.waitForResponse}
     * regex ({@link com.adi.naukri.automation.NaukriSelectors#RESUME_DOWNLOAD_URL_REGEX})
     * matches against the mock too.
     */
    @GetMapping("/cloudgateway-mynaukri/resman-aggregator-services/v{version}/users/self/profiles/{hash}/resume")
    public ResponseEntity<byte[]> downloadResumeCloudGateway() throws IOException {
        return respondWithMockResume();
    }

    private ResponseEntity<byte[]> respondWithMockResume() throws IOException {
        ClassPathResource res = new ClassPathResource("static/resume.pdf");
        byte[] bytes = res.getInputStream().readAllBytes();
        String filename = state.getUploadedResumeName() != null
                ? state.getUploadedResumeName()
                : "resume.pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @PostMapping(value = "/mnjuser/profile/resume",
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> uploadResume(@RequestParam("file") MultipartFile file) {
        state.setUploadedResumeName(file.getOriginalFilename());
        return Map.of("ok", true);
    }

    // ─── Logout ──────────────────────────────────────────────────────────────
    // Real Naukri's drawer logout link fires a GET. Our automation's
    // direct-navigation path also uses GET. Keep POST for backward compatibility
    // with any callers that submit a form.

    @GetMapping("/nlogin/logout")
    public String logoutGet(HttpServletResponse response) {
        return doLogout(response);
    }

    @PostMapping("/nlogin/logout")
    public String logoutPost(HttpServletResponse response) {
        return doLogout(response);
    }

    private String doLogout(HttpServletResponse response) {
        Cookie cookie = new Cookie(SESSION_COOKIE, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        state.setLoggedOut(true);
        return "redirect:/nlogin/login";
    }

    // ─── Mock control ────────────────────────────────────────────────────────

    @GetMapping(value = "/_mock/state", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getState() {
        return Map.of(
                "lastHeadline", state.getLastHeadline(),
                "uploadedResumeName", state.getUploadedResumeName() != null ? state.getUploadedResumeName() : "",
                "headlineSaveCount", state.getHeadlineSaveCount(),
                "loggedOut", state.isLoggedOut()
        );
    }

    @PostMapping("/_mock/reset")
    @ResponseBody
    public ResponseEntity<Void> reset() {
        state.reset();
        return ResponseEntity.ok().build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean hasSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        return Arrays.stream(cookies)
                .anyMatch(c -> SESSION_COOKIE.equals(c.getName()) && !c.getValue().isBlank());
    }

    private String resolveEmail(HttpServletRequest request, String fallback) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return fallback;
        return Arrays.stream(cookies)
                .filter(c -> SESSION_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(fallback);
    }
}
