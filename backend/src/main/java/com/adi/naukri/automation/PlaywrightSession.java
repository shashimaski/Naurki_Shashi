package com.adi.naukri.automation;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the Playwright / Chromium lifecycle for a single automation run.
 *
 * <p>Each call to {@link #open(AutomationRunMode)} creates a fresh incognito
 * {@link BrowserContext} and returns a new {@link Page} with downloads enabled
 * and standard timeouts applied. Closing the session tears down all contexts
 * and the browser process.</p>
 *
 * <p>Anti-bot / stealth hardening (all applied at context creation):
 * <ul>
 *   <li>Prefers system Chrome via {@code channel("chrome")} — falls back to bundled Chromium.</li>
 *   <li>Realistic UA, viewport, locale, and timezone matching a Windows 10 Chrome 126 desktop.</li>
 *   <li>Init script patches {@code navigator.webdriver}, plugins, languages, chrome runtime,
 *       permissions API, and WebGL vendor strings before any page script runs.</li>
 *   <li>No persistent user-data-dir — every context is fully ephemeral.</li>
 * </ul>
 * </p>
 *
 * <p>Instantiate once per run and use inside a try-with-resources block:</p>
 * <pre>{@code
 * try (PlaywrightSession s = new PlaywrightSession()) {
 *     Page page = s.open(AutomationRunMode.HEADLESS);
 *     // ... drive the browser ...
 * }
 * }</pre>
 *
 * Author: Adikarthik Gupta C B
 */
public class PlaywrightSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightSession.class);

    /** Default page-load / navigation timeout in milliseconds. */
    public static final int DEFAULT_NAV_TIMEOUT_MS    = 30_000;

    /** Default element interaction timeout in milliseconds. */
    public static final int DEFAULT_ACTION_TIMEOUT_MS = 15_000;

    /**
     * Realistic Windows 10 Chrome 126 UA — matches the viewport + locale set on the context.
     * Avoids the HeadlessChrome token that is the number-one automated-browser giveaway.
     */
    private static final String STEALTH_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36";

    /**
     * Init script injected into every page before any site script runs.
     *
     * <p>Patches the most commonly fingerprinted properties:
     * <ul>
     *   <li>{@code navigator.webdriver} → {@code undefined} (primary headless tell)</li>
     *   <li>{@code navigator.languages} → realistic Indian-English plurality</li>
     *   <li>{@code navigator.plugins} → three PDF-viewer entries present in real Chrome</li>
     *   <li>{@code window.chrome} → runtime stub so detector checks pass</li>
     *   <li>Permissions API → returns {@code Notification.permission} for notifications
     *       instead of the headless hard-coded "denied"</li>
     *   <li>WebGL UNMASKED_VENDOR / RENDERER → stable Intel GPU signature</li>
     * </ul>
     * </p>
     */
    private static final String STEALTH_INIT = """
// Hide the number-one headless giveaway
Object.defineProperty(navigator, 'webdriver', { get: () => undefined });

// Language plurality — headless has [en-US]; real Chrome on en-IN has [en-IN, en-US, en]
Object.defineProperty(navigator, 'languages', { get: () => ['en-IN', 'en-US', 'en'] });

// navigator.plugins — headless returns empty; real Chrome has ~3 PDF viewer entries
Object.defineProperty(navigator, 'plugins', {
  get: () => [
    { name: 'PDF Viewer',     filename: 'internal-pdf-viewer' },
    { name: 'Chrome PDF Viewer', filename: 'internal-pdf-viewer' },
    { name: 'Chromium PDF Viewer', filename: 'internal-pdf-viewer' },
  ]
});

// Chrome runtime — some detectors check window.chrome
if (!window.chrome) {
  window.chrome = { runtime: {} };
}

// Permissions API — headless returns 'denied' for notifications; real Chrome returns 'default'
const _origPermsQuery = window.navigator.permissions && window.navigator.permissions.query;
if (_origPermsQuery) {
  window.navigator.permissions.query = (parameters) =>
    parameters.name === 'notifications'
      ? Promise.resolve({ state: Notification.permission })
      : _origPermsQuery(parameters);
}

// WebGL vendor/renderer — a stable, common Intel GPU signature
try {
  const _getParam = WebGLRenderingContext.prototype.getParameter;
  WebGLRenderingContext.prototype.getParameter = function(p) {
    if (p === 37445) return 'Intel Inc.';
    if (p === 37446) return 'Intel Iris OpenGL Engine';
    return _getParam.call(this, p);
  };
} catch (e) {}
""";

    private final Playwright playwright;
    private Browser browser;
    private AutomationRunMode lastMode;
    private Page lastPage;

    /** {@code true} when system Chrome was found; {@code false} when bundled Chromium is used. */
    private boolean usingSystemChrome = false;

    /** Creates a new session (does NOT launch the browser yet). */
    public PlaywrightSession() {
        this.playwright = Playwright.create();
    }

    /**
     * Returns the most recently opened {@link Page}, or {@code null} if none has been opened yet.
     * Useful in tests where the gate needs access to the browser page.
     */
    public Page lastPage() {
        return lastPage;
    }

    /**
     * Opens (or reuses) the browser and returns a fresh incognito page with stealth context options.
     *
     * <p>Tries system Chrome first ({@code channel("chrome")}); falls back to bundled Chromium
     * if Chrome is not installed. The channel choice is logged once per session.</p>
     *
     * @param mode {@link AutomationRunMode#HEADLESS} for CI / background runs,
     *             {@link AutomationRunMode#HEADED} for visible / manual-login runs.
     * @return a new {@link Page} with stealth init applied, downloads enabled, and timeouts set.
     */
    public Page open(AutomationRunMode mode) {
        // If mode changed (e.g. headless -> headed retry), tear down the old browser
        // so the new one respects the requested visibility setting.
        if (browser != null && lastMode != mode) {
            browser.close();
            browser = null;
        }
        if (browser == null) {
            lastMode = mode;
            browser = launchBrowser(mode);
        }

        BrowserContext ctx = browser.newContext(
            new Browser.NewContextOptions()
                .setAcceptDownloads(true)
                .setUserAgent(STEALTH_UA)
                // 1440x900 matches Naukri's ~1400px max-width content container
                // edge-to-edge. 1920x1080 leaves a wide empty gutter on both sides
                // because Naukri centers its content inside a fixed-width column.
                .setViewportSize(1440, 900)
                .setDeviceScaleFactor(1.0)
                .setLocale("en-IN")
                .setTimezoneId("Asia/Kolkata")
        );
        ctx.setDefaultNavigationTimeout(DEFAULT_NAV_TIMEOUT_MS);
        ctx.setDefaultTimeout(DEFAULT_ACTION_TIMEOUT_MS);

        // Inject stealth script — runs in every page before any site JS
        ctx.addInitScript(STEALTH_INIT);

        log.info("[stealth] context created — channel={} stealthInit=applied UA=Chrome/126",
                usingSystemChrome ? "chrome(system)" : "chromium(bundled)");

        lastPage = ctx.newPage();
        return lastPage;
    }

    /**
     * Closes the browser and the Playwright instance.
     * Safe to call multiple times.
     */
    @Override
    public void close() {
        try {
            if (browser != null) {
                browser.close();
                browser = null;
            }
        } finally {
            playwright.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attempts to launch system Chrome; falls back to bundled Chromium on failure.
     * Records the result in {@link #usingSystemChrome}.
     */
    private Browser launchBrowser(AutomationRunMode mode) {
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                .setHeadless(mode == AutomationRunMode.HEADLESS);
        try {
            Browser b = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(mode == AutomationRunMode.HEADLESS)
                            .setChannel("chrome")
            );
            usingSystemChrome = true;
            log.info("[stealth] browser launched via system Chrome channel");
            return b;
        } catch (Exception e) {
            log.info("[stealth] system Chrome not found ({}); falling back to bundled Chromium",
                    e.getMessage());
            usingSystemChrome = false;
            return playwright.chromium().launch(opts);
        }
    }
}
