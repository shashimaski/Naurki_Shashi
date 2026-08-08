# Real Naukri DOM reference — verified 2026-07-16

**Author:** Adikarthik Gupta C B
**Date verified:** 2026-07-16
**Source:** Live browser session against `https://www.naukri.com/mnjuser/homepage` and `.../mnjuser/profile` with the logged-in user "Arpitha S".

This document records the confirmed shape of every DOM element the automation touches on real Naukri. Every selector in `NaukriSelectors.java` traces back to a fragment here. When Naukri redeploys and something breaks, first check whether the fragment in this file still matches the live page.

The mock-naukri Thymeleaf templates were rewritten on the same date to mirror these shapes, so the integration tests exercise the same selectors used in production.

---

## 1. Homepage — link to profile page

After login, the user lands on `/mnjuser/homepage`. The link into the profile page is wrapped in a distinctive class:

```html
<div class="view-profile-wrapper">
  <a href="/mnjuser/profile">View profile</a>
</div>
```

**Selectors:**
- Primary: `.view-profile-wrapper a[href='/mnjuser/profile']` — `NaukriSelectors.NAV_PROFILE_WRAPPER`
- Fallback: `a[href='/mnjuser/profile']` — `NaukriSelectors.NAV_PROFILE`

---

## 2. Profile page — Resume headline widget

Real markup on `/mnjuser/profile`:

```html
<div class="card mt15">
  <div class="">
    <div class="widgetHead">
      <span class="widgetTitle typ-16Bold">Resume headline</span>
      <span class="edit icon">editOneTheme</span>
    </div>
    <div class="widgetCont">
      <div class="prefill typ-14Medium">
        <div>Total 5.0 years of industrial working experience as a Senior DevOps Engineer
        in the field of Software Configuration Management, Build, Release, and Cloud Operations.</div>
      </div>
    </div>
  </div>
</div>
```

**Selectors:**
- Widget title anchor (text search): `span.widgetTitle` with text `Resume headline` — `HEADLINE_WIDGET_TITLE` + `HEADLINE_WIDGET_TITLE_TEXT`
- Edit pencil: `.widgetHead .edit.icon` — `HEADLINE_EDIT_ICON`
- Rendered text: `.widgetCont .prefill` — `HEADLINE_PREFILL_TEXT`

The widget has **no stable id or data-testid**; the automator locates it by walking from the `span.widgetTitle` with matching text up to the nearest `.card` ancestor, then finding the edit icon inside that card.

### Edit dialog (opened by clicking the pencil)

```html
<form class="s12 lbpadding" name="resumeHeadlineForm">
  <div class="editHeader">
    <div class="mb5"><span class="widgetTitle">Resume headline</span></div>
    <div class="lbl widgetDesc">It is the first thing recruiters notice ...</div>
  </div>
  <div class="input-field s12">
    <div class="free-user-editor">
      <div class="fue__container">
        <div class="fue__text-container">
          <textarea id="resumeHeadlineTxt" name="resumeHeadline"
                    class="fue__text-area" rel="required:resumeHeadline,custom:validChar"
                    maxlength="250" data-length="250"
                    placeholder="Minimum 5 words. Sample headlines: ...">
            Total 5.0 years of industrial working experience as ...
          </textarea>
        </div>
      </div>
      <div class="fue__bottom-container">
        <span class="erLbl fue__err-msg" id="resumeHeadlineTxt_err"></span>
        <div class="fue__chars-count">85 character(s) left</div>
      </div>
    </div>
  </div>
  <div class="row form-actions">
    <div class="action s12">
      <a class="cancel-btn" href="javascript:void(0)">Cancel</a>
      <button class="btn-dark-ot" type="submit">Save</button>
    </div>
  </div>
</form>
```

**Selectors:**
- Form: `form[name='resumeHeadlineForm']` — `HEADLINE_FORM`
- Textarea: `#resumeHeadlineTxt` (also `textarea[name='resumeHeadline']` / `textarea.fue__text-area`) — `HEADLINE_TEXTAREA`
- Save: `form[name='resumeHeadlineForm'] button[type='submit']` — `HEADLINE_SAVE`
- Cancel: `form[name='resumeHeadlineForm'] a.cancel-btn` — `HEADLINE_CANCEL`

**Constraint:** `maxlength="250"` on the textarea. The automator reads this attribute at runtime and, if appending a `.` would overflow, toggles a trailing space instead so the save still fires and Naukri still records an "edited" event.

---

## 3. Profile page — Resume card

```html
<div id="lazyAttachCV" class="card mt15">
  <span id="attachCVMsgBox" tabindex="-1"></span>
  <div class="attachCV">
    <div class="heading row">
      <div class="row main mb0">
        <div class="s12 typ-16Bold">Resume</div>
      </div>
    </div>
    <div>
      <div class="cvPreview">
        <div class="row">
          <div class="col s10">
            <div class="left">
              <div class="resume-name-inline">
                <div title="Arpitha S 15.07.2026 yahoo.pdf" class="truncate exten">
                  Arpitha S 15.07.2026 yahoo.pdf
                </div>
              </div>
              <div class="updateOn typ-14Regular">
                Uploaded on Jul 15, 2026
              </div>
            </div>
          </div>
          <div class="col s2">
            <div class="right">
              <span class="icon-wrap" data-title="download-resume">
                <i class="icon" data-title="download-resume"
                   title="Click here to download your resume">downloadOneTheme</i>
              </span>
              <span class="icon-wrap" data-title="delete-resume">
                <i class="icon" data-title="delete-resume"
                   title="Click here to delete your resume">deleteOneTheme</i>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="row mb0">
      <div class="cvUpload s12">
        <div>
          <div class="uploadCont">
            <div class="uploadBtn">
              <div>
                <!-- Hidden JS-only form used by Naukri's click handler -->
                <form class="download" style="display: none;">
                  <div class="icon-wrap center">
                    <div class="lig-wrap">
                      <button class="blue-text waves-effect waves-teal btn-flat"
                              data-ga-track="spa-event|EditProfile|DownloadResume|Download"
                              type="submit">
                        <i class="ligature-icons">Download</i>
                      </button>
                    </div>
                  </div>
                </form>
              </div>
              <section>
                <div class="action">
                  <div class="uploadContainer">
                    <input type="file" id="attachCV" class="fileUpload waves-effect waves-light btn-large"/>
                  </div>
                  <div>
                    <div id="result"></div>
                    <input type="button" value="Update resume" class="dummyUpload typ-14Bold"/>
                  </div>
                </div>
              </section>
              <ul id="results_resumeParser"></ul>
            </div>
            <div class="format typ-14Medium">Supported Formats: doc, docx, rtf, pdf, upto 2 MB</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
```

**Selectors:**
- Card: `#lazyAttachCV` (also `.attachCV`) — `RESUME_CARD`
- Filename: `.resume-name-inline .truncate.exten` (also `title` attribute for the untruncated form) — `RESUME_FILENAME`
- Uploaded-on: `.updateOn` — `RESUME_UPLOADED_ON`
- Download icon: `[data-title='download-resume']` — `RESUME_DOWNLOAD`
- File input: `input#attachCV[type='file']` — `RESUME_UPLOAD_INP`
- Update button: `input.dummyUpload[value='Update resume']` — `RESUME_UPDATE_BTN`

### How the download actually works

Clicking `[data-title='download-resume']` fires a JS click handler in `mnj_v314.min.js` (function chain: `onClick` → `download` → `downloadResume`). That handler issues an XHR/fetch against:

```
https://www.naukri.com/cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/<hash>/resume
```

Where `<hash>` is a 64-char hex string (per-account, derived server-side). Example URL captured 2026-07-16:

```
https://www.naukri.com/cloudgateway-mynaukri/resman-aggregator-services/v1/users/self/profiles/974fc069c8ff92c2e154c5b3395ef58fcb803ebd6429fd0f5c4f31944917d4da/resume
```

**Regex used to match:**
```
.*/resman-aggregator-services/v\d+/users/self/profiles/[^/?#]+/resume(\?.*)?$
```

`NaukriSelectors.RESUME_DOWNLOAD_URL_REGEX`. The automator installs a `Page.waitForResponse(Predicate<Response>)` immediately before clicking the icon and captures the response body directly — no dependency on the browser's file-download event, no dependency on knowing the profile hash up front.

### Rename convention

Naukri's own filename convention is `<name> DD.MM.YYYY <suffix>.<ext>` (example: `Arpitha S 15.07.2026 yahoo.pdf`). The automator's `ResumeRenamer` detects any existing date pattern (`DD.MM.YYYY`, `DD-MM-YYYY`, `DD/MM/YYYY`, or `YYYY-MM-DD`) and replaces it **in place** with today's date using the same separator, preserving the surrounding filename structure. If no date pattern is found, it falls back to appending `_yyyy-MM-dd` before the extension.

---

## 4. Success toast (shared across all profile edits)

After any successful profile save (headline OR resume), Naukri shows a single shared toast:

```html
<div class="success-message-container">
  <div class="success-icon-wrapper">
    <img class="success-icon" src="//static.naukimg.com/s/0/0/i/collated-landing/desktop/v0/popup4/success.png" alt="success"/>
    <img class="inner-circle" src="//static.naukimg.com/s/0/0/i/collated-landing/desktop/v0/popup4/inner_circle.png" alt="inner-circle"/>
    <img class="outer-circle" src="//static.naukimg.com/s/0/0/i/collated-landing/desktop/v0/popup4/outer_circle.png" alt="outer-circle"/>
  </div>
  <span class="success-text">Profile updated successfully</span>
</div>
```

**Selectors:**
- Container: `.success-message-container` — `SUCCESS_TOAST`
- Expected text: `Profile updated successfully` — `SUCCESS_TOAST_TEXT`

The automator waits for the toast to become visible (indicating server-confirmed save), then waits for it to hide again before proceeding to the next save. That second wait prevents a stale-toast false positive on the following save's toast-wait.

---

## 5. Logout — drawer + direct URL

Real Naukri: logout lives inside a slide-in **drawer** opened by clicking the user avatar in the top nav.

### Drawer markup (once opened)

```html
<div class="drawer-wrapper" style="padding: 52px 30px 30px; overflow-y: auto;">
  <button type="button" aria-label="Close" class="close">
    <i class="naukicon cross ni-gnb-icn ni-gnb-icn-cross-drawer"></i>
  </button>
  <div class="nI-gNb-drawer__expand">
    <div class="nI-gNb-de__profile">
      <div class="nI-gNb-profile-details">
        <div class="nI-gNb-info__heading">
          <span class="nI-gNb-info__heading-name">Arpitha S</span>
        </div>
        <div class="nI-gNb-info__sub-heading">Senior DevOps Engineer at ValueLabs</div>
      </div>
    </div>
    ...
    <div class="nI-gNb-de__list">
      <div class="nI-gNb-list-item">
        <a href="..." class="nI-gNb-list-cta"><span class="ni-gnb-icn ni-gnb-icn-blog"></span>Naukri Blog</a>
      </div>
      <div class="nI-gNb-list-item">
        <a href="..." class="nI-gNb-list-cta"><span class="ni-gnb-icn ni-gnb-icn-settings"></span>Settings</a>
      </div>
      <div class="nI-gNb-list-item">
        <a href="..." class="nI-gNb-list-cta"><span class="ni-gnb-icn ni-gnb-icn-faqs"></span>FAQs</a>
      </div>
      <div class="nI-gNb-list-item">
        <a class="nI-gNb-list-cta" title="Logout" rel="external"
           data-type="logoutLink" role="button" tabindex="0">
          <span class="ni-gnb-icn ni-gnb-icn-logout"></span>Logout
        </a>
      </div>
    </div>
  </div>
</div>
```

**Selectors:**
- Drawer wrapper: `.drawer-wrapper` — `LOGOUT_DRAWER`
- Logout anchor (primary): `a[data-type='logoutLink']` — `LOGOUT_LINK_DRAWER`
- Logout anchor (fallback): `a[title='Logout']` — `LOGOUT_LINK_TITLE`
- Drawer trigger candidates (`LOGOUT_DRAWER_TRIGGERS`):
  - `img.nI-gNb-user-img`
  - `.nI-gNb-user-img`
  - `.nI-gNb-drawer-trigger`
  - `[data-testid='user-menu']`

### Primary strategy — direct navigation

The automator prefers a **direct GET navigation** to `/nlogin/logout` (mock and real both accept this) so it doesn't have to hunt for the top-nav avatar (whose class names Naukri obfuscates aggressively). If the direct navigation somehow doesn't land on the login page, the drawer path runs as a fallback.

`NaukriSelectors.LOGOUT_URL_PATH = "/nlogin/logout"`.

### After logout — close the browser

The automator explicitly closes the `BrowserContext` at the end of the LOGOUT step (`currentContextRef.getAndSet(null).close()`), so the browser window is gone by the time the LOGOUT StepResult is emitted. The orchestrator's `PlaywrightSession` closes the context again in its own finally block — the second close is a no-op.

---

## 6. Login (unchanged, verified 2026-07-15)

Kept here for completeness since the older recon note lived in `.superpowers/sdd/naukri-recon-output.txt`.

```html
<form id="loginForm">
  <input id="usernameField" type="text" placeholder="Enter Email ID / Username"/>
  <input id="passwordField" type="password" placeholder="Enter Password"/>
  <button type="submit">Login</button>
</form>

<!-- Shown on bad-credentials attempt -->
<div class="col s12 commonErrorMsg">Something went wrong. Please try again.</div>
```

**Selectors:**
- Email: `#usernameField` — `LOGIN_EMAIL`
- Password: `#passwordField` — `LOGIN_PASSWORD`
- Submit: `#loginForm button[type='submit']` — `LOGIN_SUBMIT`
- Error: `.commonErrorMsg` — `LOGIN_ERROR`

---

## Change log

- **2026-07-16** — Full alignment pass. Homepage → View profile wrapper, modal-based "Resume headline" edit, `#lazyAttachCV` resume card, `[data-title='download-resume']` icon, sniffed `resman-aggregator-services` download URL, single shared `.success-message-container` toast, drawer-based `[data-type='logoutLink']` logout with direct-nav preferred path. Mock templates rewritten to mirror. `ResumeRenamer` gains smart-date preservation.
- **2026-07-15** — Initial recon of LOGIN selectors (`#usernameField`, `#passwordField`, `#loginForm`, `.commonErrorMsg`).

---

*Built by Adikarthik Gupta C B — 2026-07-16.*
