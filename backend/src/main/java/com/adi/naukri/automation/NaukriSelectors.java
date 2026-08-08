package com.adi.naukri.automation;

/**
 * CSS selector constants used by both NaukriAutomator and the Mock Naukri HTML templates.
 *
 * <p><strong>Alignment status (2026-07-16):</strong> the resume + headline selectors below
 * are the ones verified in a live browser session against
 * {@code https://www.naukri.com/mnjuser/profile} and
 * {@code https://www.naukri.com/mnjuser/homepage}. The mock-naukri HTML templates were
 * updated to mirror this same DOM shape so integration tests exercise the same
 * selectors we use in production.</p>
 *
 * <ul>
 *   <li>LOGIN selectors: unchanged (verified against live login page 2026-07-15).</li>
 *   <li>Homepage: real markup {@code <div class="view-profile-wrapper"><a href="/mnjuser/profile">View profile</a></div>}
 *       — see {@link #NAV_PROFILE_WRAPPER}.</li>
 *   <li>Headline: real markup uses a widget titled "Resume headline" whose edit pencil
 *       opens a modal with {@code #resumeHeadlineTxt} textarea and
 *       {@code form[name="resumeHeadlineForm"]} — see {@link #HEADLINE_EDIT_ICON},
 *       {@link #HEADLINE_TEXTAREA}, {@link #HEADLINE_SAVE}.</li>
 *   <li>Resume card: {@code #lazyAttachCV} with {@code .resume-name-inline .truncate.exten}
 *       for the filename, {@code [data-title="download-resume"]} for the download icon,
 *       {@code input#attachCV[type="file"]} for the upload input, and
 *       {@code input.dummyUpload[value="Update resume"]} for the update button — see
 *       {@link #RESUME_CARD} etc.</li>
 *   <li>Success toast: one shared toast {@code .success-message-container} with text
 *       "Profile updated successfully" — fired after any profile save (headline OR resume).</li>
 *   <li>Download URL: real Naukri fires
 *       {@code /cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/&lt;hash&gt;/resume}
 *       from the download-icon click. Captured via {@code Page.waitForResponse} keyed on
 *       {@link #RESUME_DOWNLOAD_URL_PATTERN}. Mock exposes the same path.</li>
 * </ul>
 *
 * Author: Adikarthik Gupta C B
 */
public final class NaukriSelectors {

    private NaukriSelectors() {}

    // ── LOGIN (verified 2026-07-15 against live login page) ──
    public static final String LOGIN_EMAIL        = "#usernameField";
    public static final String LOGIN_PASSWORD     = "#passwordField";
    public static final String LOGIN_SUBMIT       = "#loginForm button[type='submit']";
    public static final String LOGIN_ERROR        = ".commonErrorMsg";
    public static final String DASH_BRAND         = "[data-mock='dash']";

    // ── HOMEPAGE → PROFILE navigation (verified 2026-07-16) ──
    // <div class="view-profile-wrapper"><a href="/mnjuser/profile">View profile</a></div>
    public static final String NAV_PROFILE_WRAPPER = ".view-profile-wrapper a[href='/mnjuser/profile']";
    // Fallback if the wrapper class is missing on some deploys.
    public static final String NAV_PROFILE         = "a[href='/mnjuser/profile']";

    // ── RESUME HEADLINE widget (modal-based, verified 2026-07-16) ──
    // The widget on the profile page has:
    //   <div class="card mt15">
    //     <div class="widgetHead">
    //       <span class="widgetTitle typ-16Bold">Resume headline</span>
    //       <span class="edit icon">editOneTheme</span>
    //     </div>
    //     <div class="widgetCont">
    //       <div class="prefill typ-14Medium"><div>...current text...</div></div>
    //     </div>
    //   </div>
    // Clicking the edit icon opens a modal:
    //   <form name="resumeHeadlineForm">
    //     <textarea id="resumeHeadlineTxt" name="resumeHeadline" maxlength="250">...</textarea>
    //     <a class="cancel-btn">Cancel</a>
    //     <button class="btn-dark-ot" type="submit">Save</button>
    //   </form>
    // Outer wrapper of the resume-headline card. Lazy-loaded on Naukri's
    // React SPA — presence of this element means .widgetHead / .widgetCont
    // children have rendered and are safe to interact with.
    public static final String HEADLINE_LAZY_ANCHOR       = "#lazyResumeHead";
    public static final String HEADLINE_WIDGET_TITLE      = "span.widgetTitle";
    public static final String HEADLINE_WIDGET_TITLE_TEXT = "Resume headline";
    public static final String HEADLINE_EDIT_ICON         = ".widgetHead .edit.icon";
    public static final String HEADLINE_PREFILL_TEXT      = ".widgetCont .prefill";
    public static final String HEADLINE_FORM              = "form[name='resumeHeadlineForm']";
    public static final String HEADLINE_TEXTAREA          = "#resumeHeadlineTxt";
    public static final String HEADLINE_SAVE              = "form[name='resumeHeadlineForm'] button[type='submit']";
    public static final String HEADLINE_CANCEL            = "form[name='resumeHeadlineForm'] a.cancel-btn";

    // ── RESUME card (verified 2026-07-16) ──
    //   <div id="lazyAttachCV" class="card mt15">
    //     <div class="attachCV">
    //       <div class="cvPreview">
    //         <div class="resume-name-inline">
    //           <div title="Arpitha S 15.07.2026 yahoo.pdf" class="truncate exten">
    //             Arpitha S 15.07.2026 yahoo.pdf
    //           </div>
    //         </div>
    //         <div class="updateOn">Uploaded on Jul 15, 2026</div>
    //         <span class="icon-wrap" data-title="download-resume">
    //           <i data-title="download-resume">downloadOneTheme</i>
    //         </span>
    //       </div>
    //       <input type="file" id="attachCV" class="fileUpload">
    //       <input type="button" value="Update resume" class="dummyUpload">
    //     </div>
    //   </div>
    public static final String RESUME_CARD           = "#lazyAttachCV";
    public static final String RESUME_FILENAME       = ".resume-name-inline .truncate.exten";
    public static final String RESUME_UPLOADED_ON    = ".updateOn";
    public static final String RESUME_DOWNLOAD       = "[data-title='download-resume']";
    public static final String RESUME_UPLOAD_INP     = "input#attachCV[type='file']";
    public static final String RESUME_UPDATE_BTN     = "input.dummyUpload[value='Update resume']";

    // ── Shared SUCCESS TOAST (fires on the FIRST profile save per session) ──
    //   <div class="success-message-container">
    //     <div class="success-icon-wrapper">...</div>
    //     <span class="success-text">Profile updated successfully</span>
    //   </div>
    public static final String SUCCESS_TOAST       = ".success-message-container";
    public static final String SUCCESS_TOAST_TEXT  = "Profile updated successfully";

    // ── Resume-specific inline SUCCESS MESSAGE (card-scoped, verified 2026-07-17) ──
    // After a successful resume upload, Naukri injects its own success box
    // INSIDE the resume card — this is separate from the shared toast above
    // (which does not fire on 2nd-save-per-session).
    //   <span id="attachCVMsgBox" tabindex="-1">
    //     <div><div class="msgBox success ">
    //       <div class="cnt">
    //         <i class="icon">GreenTick</i>
    //         <p class="head">Success</p>
    //         <p class="msg">Resume has been successfully uploaded.</p>
    //       </div>
    //     </div></div>
    //   </span>
    public static final String RESUME_UPLOAD_MSG_OK      = "#attachCVMsgBox .msgBox.success";
    public static final String RESUME_UPLOAD_MSG_OK_TEXT = "Resume has been successfully uploaded.";

    // ── BLOCKING OVERLAY (Naukri "learning tips" / Pro-upsell layer) ──
    // After the first HEADLINE save, Naukri pops a promotional overlay whose
    // container is <div class="ltCont" tabindex="0">…<div class="ltLayer open"/><div class="pro-card"/></div>.
    // It intercepts pointer events on ALL underlying elements — the next
    // edit-icon click times out unless we tear it down first.
    public static final String OVERLAY_LTLAYER  = ".ltLayer.open";
    public static final String OVERLAY_LTCONT   = ".ltCont";
    public static final String OVERLAY_PROCARD  = ".pro-card";

    // ── RESUME DOWNLOAD URL pattern ──
    // Real: /cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/<hash>/resume
    // Java regex for Page.waitForResponse (matches the URL path, not the whole URL).
    public static final String RESUME_DOWNLOAD_URL_REGEX =
            ".*/resman-aggregator-services/v\\d+/users/self/profiles/[^/?#]+/resume(\\?.*)?$";

    // ── LOGOUT (verified 2026-07-16 against live drawer) ──
    // Real Naukri: logout lives inside a slide-in drawer opened by clicking the
    // user avatar in the top nav. Direct navigation to /nlogin/logout also works
    // and is used as the primary path since it bypasses the drawer entirely.
    //
    // Drawer markup (confirmed):
    //   <div class="drawer-wrapper" ...>
    //     ...
    //     <a class="nI-gNb-list-cta" title="Logout"
    //        rel="external" data-type="logoutLink" role="button">Logout</a>
    //   </div>
    public static final String LOGOUT_URL_PATH     = "/nlogin/logout";
    public static final String LOGOUT_DRAWER       = ".drawer-wrapper";
    public static final String LOGOUT_LINK_DRAWER  = "a[data-type='logoutLink']";
    public static final String LOGOUT_LINK_TITLE   = "a[title='Logout']";
    // Drawer trigger — the user avatar in the top nav. Naukri obfuscates class
    // names across deploys, so we list several candidates. Any of these clicked
    // should cause .drawer-wrapper to appear.
    public static final String[] LOGOUT_DRAWER_TRIGGERS = new String[] {
            "img.nI-gNb-user-img",
            ".nI-gNb-user-img",
            ".nI-gNb-drawer-trigger",
            "[data-testid='user-menu']",
    };
    // Kept for the mock — the mock still exposes a[id='logout'] as a simple fallback.
    public static final String LOGOUT_LINK        = "a#logout";
    public static final String OTP_INPUT          = "#otp";
}
