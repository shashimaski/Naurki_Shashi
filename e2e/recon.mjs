/**
 * DOM reconnaissance script for naukri.com/nlogin/login.
 * Dumps element attributes and outerHTML needed to update NaukriSelectors.java.
 *
 * Author: Adikarthik Gupta C B
 *
 * Run from e2e dir:
 *   node recon.mjs > F:\views\g\Naukri\.superpowers\sdd\naukri-recon-output.txt 2>&1
 */
import { chromium } from '@playwright/test';

const TARGET_URL = 'https://www.naukri.com/nlogin/login';
const WAIT_TIMEOUT = 20000;

async function getAttrs(locator, attrs) {
  const result = {};
  for (const attr of attrs) {
    try {
      result[attr] = await locator.getAttribute(attr);
    } catch {
      result[attr] = null;
    }
  }
  return result;
}

async function safeOuterHTML(locator) {
  try {
    return await locator.evaluate(el => el.outerHTML);
  } catch (e) {
    return `[ERROR getting outerHTML: ${e.message}]`;
  }
}

(async () => {
  console.log('=== NAUKRI LOGIN PAGE DOM RECON ===');
  console.log(`Target: ${TARGET_URL}`);
  console.log(`Timestamp: ${new Date().toISOString()}`);
  console.log('');

  const browser = await chromium.launch({ headless: false, slowMo: 100 });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
  });
  const page = await context.newPage();

  try {
    console.log(`[NAV] Navigating to ${TARGET_URL} ...`);
    await page.goto(TARGET_URL, { waitUntil: 'domcontentloaded', timeout: 30000 });
    console.log(`[NAV] DOMContentLoaded. Current URL: ${page.url()}`);

    // Wait for networkidle
    try {
      await page.waitForLoadState('networkidle', { timeout: 15000 });
      console.log('[NAV] networkidle reached');
    } catch {
      console.log('[NAV] networkidle timed out, continuing anyway');
    }

    // Wait for the form to hydrate - try multiple heuristics
    let formFound = false;
    const emailHeuristics = [
      'input[placeholder*="Email"]',
      'input[placeholder*="email"]',
      'input[placeholder*="Username"]',
      'input[placeholder*="username"]',
      'input[type="email"]',
      'input[type="text"][placeholder]',
    ];

    for (const heuristic of emailHeuristics) {
      try {
        await page.waitForSelector(heuristic, { timeout: 5000 });
        console.log(`[FORM] Form hydrated, found selector: ${heuristic}`);
        formFound = true;
        break;
      } catch {
        console.log(`[FORM] Heuristic not found: ${heuristic}`);
      }
    }

    if (!formFound) {
      console.log('[FORM] WARNING: No email input found with standard heuristics. Dumping all inputs:');
      const allInputs = await page.locator('input').all();
      for (let i = 0; i < allInputs.length; i++) {
        const html = await safeOuterHTML(allInputs[i]);
        console.log(`  input[${i}]: ${html}`);
      }
    }

    // ── EMAIL INPUT ─────────────────────────────────────────────────────────
    console.log('');
    console.log('=== EMAIL INPUT ===');
    const emailLocators = [
      page.locator('input[placeholder*="Enter Email"]').first(),
      page.locator('input[placeholder*="Enter email"]').first(),
      page.locator('input[placeholder*="Email ID"]').first(),
      page.locator('input[placeholder*="email"]').first(),
      page.locator('input[placeholder*="Username"]').first(),
      page.locator('input[type="email"]').first(),
    ];

    let emailEl = null;
    for (const loc of emailLocators) {
      try {
        const count = await loc.count();
        if (count > 0) {
          emailEl = loc;
          console.log(`Found email input with: ${await loc.evaluate(el => el.placeholder || el.type)}`);
          break;
        }
      } catch { /* skip */ }
    }

    if (emailEl) {
      const html = await safeOuterHTML(emailEl);
      console.log(`outerHTML: ${html}`);
      const attrs = await getAttrs(emailEl, ['id', 'name', 'type', 'placeholder', 'class', 'autocomplete']);
      console.log(`id:          ${attrs.id}`);
      console.log(`name:        ${attrs.name}`);
      console.log(`type:        ${attrs.type}`);
      console.log(`placeholder: ${attrs.placeholder}`);
      console.log(`class:       ${attrs.class}`);
      if (attrs.id) {
        console.log(`suggested css: #${attrs.id}`);
      } else if (attrs.name) {
        console.log(`suggested css: input[name='${attrs.name}']`);
      } else if (attrs.placeholder) {
        console.log(`suggested css: input[placeholder*="${attrs.placeholder.substring(0, 20)}"]`);
      }
    } else {
      console.log('EMAIL INPUT NOT FOUND with any standard locator');
    }

    // ── PASSWORD INPUT ───────────────────────────────────────────────────────
    console.log('');
    console.log('=== PASSWORD INPUT ===');
    const passwordLocators = [
      page.locator('input[placeholder*="Enter Password"]').first(),
      page.locator('input[placeholder*="Password"]').first(),
      page.locator('input[type="password"]').first(),
    ];

    let passwordEl = null;
    for (const loc of passwordLocators) {
      try {
        const count = await loc.count();
        if (count > 0) {
          passwordEl = loc;
          break;
        }
      } catch { /* skip */ }
    }

    if (passwordEl) {
      const html = await safeOuterHTML(passwordEl);
      console.log(`outerHTML: ${html}`);
      const attrs = await getAttrs(passwordEl, ['id', 'name', 'type', 'placeholder', 'class']);
      console.log(`id:          ${attrs.id}`);
      console.log(`name:        ${attrs.name}`);
      console.log(`type:        ${attrs.type}`);
      console.log(`placeholder: ${attrs.placeholder}`);
      console.log(`class:       ${attrs.class}`);
      if (attrs.id) {
        console.log(`suggested css: #${attrs.id}`);
      } else if (attrs.name) {
        console.log(`suggested css: input[name='${attrs.name}']`);
      } else if (attrs.placeholder) {
        console.log(`suggested css: input[placeholder*="${attrs.placeholder.substring(0, 20)}"]`);
      }
    } else {
      console.log('PASSWORD INPUT NOT FOUND with any standard locator');
    }

    // ── LOGIN BUTTON ─────────────────────────────────────────────────────────
    console.log('');
    console.log('=== LOGIN BUTTON ===');
    const buttonLocators = [
      page.getByRole('button', { name: /^login$/i }),
      page.locator('button[type="submit"]').first(),
      page.locator('button').filter({ hasText: /^login$/i }).first(),
      page.locator('button').filter({ hasText: /login/i }).first(),
    ];

    let buttonEl = null;
    for (const loc of buttonLocators) {
      try {
        const count = await loc.count();
        if (count > 0) {
          buttonEl = loc.first();
          break;
        }
      } catch { /* skip */ }
    }

    if (buttonEl) {
      const html = await safeOuterHTML(buttonEl);
      console.log(`outerHTML: ${html}`);
      const attrs = await getAttrs(buttonEl, ['id', 'type', 'class']);
      const text = await buttonEl.textContent();
      console.log(`id:    ${attrs.id}`);
      console.log(`type:  ${attrs.type}`);
      console.log(`class: ${attrs.class}`);
      console.log(`text:  ${text?.trim()}`);
      if (attrs.id) {
        console.log(`suggested css: #${attrs.id}`);
      } else if (attrs.type === 'submit') {
        console.log(`suggested css: button[type='submit']`);
      } else {
        console.log(`suggested css: button:has-text("Login") (Playwright locator)`);
      }
    } else {
      console.log('LOGIN BUTTON NOT FOUND');
    }

    // ── WRAPPING FORM ────────────────────────────────────────────────────────
    console.log('');
    console.log('=== WRAPPING FORM ===');
    const formLocator = page.locator('form').first();
    try {
      const formCount = await formLocator.count();
      if (formCount > 0) {
        const formAttrs = await getAttrs(formLocator, ['id', 'action', 'method', 'class']);
        console.log(`id:     ${formAttrs.id}`);
        console.log(`action: ${formAttrs.action}`);
        console.log(`method: ${formAttrs.method}`);
        console.log(`class:  ${formAttrs.class}`);
        // Only dump partial outerHTML to avoid huge SPA forms
        const formInner = await formLocator.evaluate(el => {
          const clone = el.cloneNode(false);
          return clone.outerHTML.substring(0, 300);
        });
        console.log(`outerHTML (opening tag only): ${formInner}`);
      } else {
        console.log('FORM NOT FOUND');
      }
    } catch (e) {
      console.log(`FORM ERROR: ${e.message}`);
    }

    // ── ERROR ATTEMPT ────────────────────────────────────────────────────────
    console.log('');
    console.log('=== WRONG CREDENTIALS ATTEMPT ===');
    console.log('Filling bad credentials and clicking Login...');

    try {
      if (emailEl) {
        await emailEl.fill('bad@nowhere.example');
        await page.waitForTimeout(300);
      }
      if (passwordEl) {
        await passwordEl.fill('bad-password');
        await page.waitForTimeout(300);
      }
      if (buttonEl) {
        await buttonEl.click();
        console.log('Clicked Login button. Waiting 6s for error to appear...');
        await page.waitForTimeout(6000);
      }
    } catch (e) {
      console.log(`Fill/click error: ${e.message}`);
    }

    console.log(`URL after failed attempt: ${page.url()}`);

    // Look for error/invalid text elements
    console.log('');
    console.log('=== ERROR/INVALID ELEMENTS AFTER FAILED LOGIN ===');

    // Check for visible error elements
    const errorPatterns = [
      '[class*="error"]',
      '[class*="Error"]',
      '[class*="invalid"]',
      '[class*="alert"]',
      '[id*="error"]',
      '[id*="Error"]',
      '[role="alert"]',
      '.errMsg',
      '.error-msg',
      '.err-msg',
    ];

    let errFound = false;
    for (const pat of errorPatterns) {
      try {
        const els = await page.locator(pat).all();
        for (const el of els) {
          const visible = await el.isVisible();
          if (!visible) continue;
          const text = await el.textContent();
          if (!text || text.trim() === '') continue;
          if (/invalid|incorrect|wrong|error|fail|sorry|doesn't match/i.test(text)) {
            const elHtml = await safeOuterHTML(el);
            const attrs = await getAttrs(el, ['id', 'class']);
            console.log(`Pattern: ${pat}`);
            console.log(`text: ${text.trim()}`);
            console.log(`id:   ${attrs.id}`);
            console.log(`class: ${attrs.class}`);
            console.log(`outerHTML: ${elHtml.substring(0, 400)}`);
            console.log('---');
            errFound = true;
          }
        }
      } catch { /* skip */ }
    }

    if (!errFound) {
      console.log('No error elements matching /invalid|incorrect|wrong|error|fail/ found after attempt.');
      console.log('Dumping all visible elements with non-empty text that may be error messages:');
      // Try a broader approach: any span/div/p whose visible text contains error keywords
      try {
        const spans = await page.locator('span, div, p').all();
        let dumped = 0;
        for (const s of spans) {
          if (dumped >= 20) break;
          try {
            const visible = await s.isVisible();
            if (!visible) continue;
            const text = await s.textContent();
            if (!text) continue;
            if (/invalid|incorrect|wrong|error|fail|sorry|doesn't match/i.test(text) && text.trim().length < 200) {
              const html = await safeOuterHTML(s);
              const attrs = await getAttrs(s, ['id', 'class']);
              console.log(`text: ${text.trim()}`);
              console.log(`id:   ${attrs.id}`);
              console.log(`class: ${attrs.class}`);
              console.log(`outerHTML: ${html.substring(0, 300)}`);
              console.log('---');
              dumped++;
            }
          } catch { /* skip */ }
        }
        if (dumped === 0) {
          console.log('Still no error text found.');
        }
      } catch (e) {
        console.log(`Broad scan error: ${e.message}`);
      }
    }

    // All inputs dump (final state)
    console.log('');
    console.log('=== ALL INPUTS ON PAGE (FINAL STATE) ===');
    try {
      const allInputs = await page.locator('input').all();
      console.log(`Total inputs found: ${allInputs.length}`);
      for (let i = 0; i < Math.min(allInputs.length, 10); i++) {
        const html = await safeOuterHTML(allInputs[i]);
        console.log(`  input[${i}]: ${html.substring(0, 300)}`);
      }
    } catch (e) {
      console.log(`Error dumping inputs: ${e.message}`);
    }

  } catch (topErr) {
    console.log(`\n=== FATAL ERROR ===`);
    console.log(`${topErr.message}`);
    console.log(`${topErr.stack}`);
  } finally {
    await browser.close();
    console.log('');
    console.log('=== RECON COMPLETE ===');
  }
})();
