# Publishing DriveMark (Public)

How to publish DriveMark to the **public** Chrome Web Store so any Google account can install and use it.

> Distributing privately to a single Google Workspace domain instead? That path is simpler (Internal consent screen, no OAuth verification) — see the note at the end of the [Costs](#costs) section.

## Prerequisites

- A Google account to register as a Chrome Web Store developer
- The DriveMark source code, ready to package (see [chrome-extension-development.md](chrome-extension-development.md))
- A configured Google Cloud project with OAuth credentials
- A **privacy policy URL** and a **homepage URL** on a domain you can verify (required for OAuth verification — see step 2)

---

## Costs

Publishing and running DriveMark publicly is effectively free — there are **no per-user charges**.

| Item | Cost |
|------|------|
| Chrome Web Store developer registration | **$5 USD, one-time** (per developer account, ever) |
| OAuth consent screen + sign-in | Free |
| OAuth verification (required for public/External apps) | **Free** — see below |
| Drive API + Sheets API usage | Free — rate-limited by quota, not billed per call |
| Google Cloud project | Free (no billing account needed for these APIs) |
| **Per user** | **$0** |

**Why verification is free here:** DriveMark uses only the `https://www.googleapis.com/auth/drive.file` scope (per-file access — the app only sees files it creates or the user opens with it). That's a **sensitive** scope, which requires Google's OAuth *brand verification* (a free review of your app name, logo, and privacy policy). It is **not** a **restricted** scope, so it avoids the annual third-party **CASA security assessment** that broad Drive scopes (`drive`, `drive.readonly`) trigger — that assessment can cost thousands. Staying on `drive.file` is what keeps this free.

The only real "cost" of going public is **effort and time**: OAuth verification is a review process that can take several days to a few weeks, and until it's approved the app is capped at 100 users (see step 2).

> **Private-to-your-org alternative:** If you only need people on **one Google Workspace domain** to use DriveMark, set the consent screen to **Internal** instead of External. Internal apps skip OAuth verification entirely and have no 100-user cap — the only cost is the same one-time $5 Web Store fee. That's the cheapest path, but the extension is then limited to your domain.

---

## 1. Register as a Chrome Web Store Developer

1. Go to the [Chrome Web Store Developer Dashboard](https://chrome.google.com/webstore/devconsole)
2. Sign in with the Google account that should own the listing
3. Pay the one-time **$5 USD** registration fee
4. Complete the identity verification steps

> **Tip:** Use a shared team account or alias if you want the listing owned by the team rather than an individual — the listing owner is hard to change later.

---

## 2. Configure and Publish the OAuth Consent Screen

Public distribution requires an **External** consent screen that has passed OAuth verification.

### Set it to External

1. Go to [Google Cloud Console](https://console.cloud.google.com) → **APIs & Services → OAuth consent screen**
2. Under **User Type**, select **External**
3. Fill in the required app information:
   - **App name**, **user support email**
   - **App logo** (required for verification)
   - **Application home page** and **privacy policy URL** (must be on a domain you verify)
   - **Authorized domains** — add the domain(s) your homepage/privacy policy live on
   - **Scopes** — confirm only `https://www.googleapis.com/auth/drive.file` is listed
4. Save

### Understand the 100-user cap

While the app is in **Testing** status, only accounts you add as **test users** can sign in (max 100), and their refresh tokens expire after 7 days. This is fine for a beta but not for public release.

### Submit for verification

1. On the OAuth consent screen page, click **Publish App** to move it to **In production**
2. Because `drive.file` is a sensitive scope, Google will prompt you to **Submit for verification**
3. Provide what Google asks for — typically:
   - Verified ownership of your authorized domain(s) (via Search Console)
   - A working privacy policy and homepage
   - A justification for the `drive.file` scope
   - Sometimes a short YouTube video demonstrating the OAuth flow
4. Wait for approval (days to weeks). Once approved, the 100-user cap and the 7-day token expiry are lifted and anyone can sign in.

> You can upload to the Web Store (step 4) and add test users in parallel while verification is pending — you don't have to wait for verification to start testing.

---

## 3. Prepare the Package

### Verify the production CLIENT_ID

`config.prod.js` is gitignored — create it from the template if you haven't already (`cp config.prod.example.js config.prod.js`). Confirm `CLIENT_ID` is set to your production OAuth client ID. The publish script (`npm run package`) automatically swaps `config.prod.js` into the ZIP as `config.js`.

If you haven't created a production credential yet:

1. Go to **APIs & Services → Credentials** in Google Cloud Console
2. **Create Credentials → OAuth client ID** → **Chrome extension**
3. For **Item ID**, enter the extension ID assigned by the Chrome Web Store after your first upload (you'll come back to this step)
4. Copy the new client ID into `config.prod.js`

> **Note:** The Chrome Web Store extension ID differs from your local unpacked extension ID. You'll need to update the OAuth credential after the first upload — see step 5.

### Create the ZIP

Run from `chrome-extension/` (the script rebuilds `vendor/` first, so `npm install` must have been run). The script also strips the `manifest.json` `key` field automatically — the Web Store assigns its own key and extension ID on upload.

```sh
npm run package                       # package current version
npm run package -- --bump patch       # bump & package
```

Or manually (run `npm install` first so `vendor/` exists, and remove the `key` field from `manifest.json` yourself):

```sh
zip -r drivemark.zip \
  manifest.json \
  background.js \
  popup.html \
  popup.js \
  popup.css \
  auth.js \
  browser.js \
  save-form.js \
  spreadsheet-picker.js \
  lib/ \
  vendor/ \
  icons/
```

Do **not** include `docs/`, `tests/`, `package.json`, `.git/`, `.omc/`, or any `.pem` key files.

---

## 4. Upload to the Chrome Web Store

1. Open the [Developer Dashboard](https://chrome.google.com/webstore/devconsole)
2. Click **New Item**
3. Upload `drivemark.zip`
4. Fill in the listing details:
   - **Description**: "Save and sync bookmarks across devices using Google Sheets."
   - **Category**: Productivity
   - **Language**: English
   - **Screenshots**: at least one 1280x800 or 640x400 screenshot of the extension popup
   - **Privacy practices**: declare the `drive.file` usage and link your privacy policy (the Web Store has its own privacy disclosure form, separate from the OAuth consent screen)
5. Under **Visibility**, select **Public**

   The extension will appear in public Chrome Web Store search once approved.

6. Click **Submit for Review**

> **Review times:** Public extensions go through Chrome Web Store review (separate from OAuth verification). This typically takes a few days but can be longer for the first submission.

---

## 5. Update the OAuth Credential

After the first upload, the Chrome Web Store assigns a permanent extension ID.

1. Find the new extension ID in the Developer Dashboard (shown on the listing page)
2. Go to **Google Cloud Console → APIs & Services → Credentials**
3. If you created a **Chrome extension** type credential in step 3, update the **Item ID** to match the Web Store extension ID
4. If you're using a **Web application** type credential, update the **Authorized redirect URI** to:
   ```
   https://<webstore-extension-id>.chromiumapp.org/
   ```
5. Update `CLIENT_ID` in `config.prod.js` if you created a new credential
6. Re-upload the updated ZIP (with a new version number — see section 6)

---

## 6. Updating the Extension

To push a new version:

1. Bump the `version` in `manifest.json` (e.g. `"1.0.0"` → `"1.1.0"`), or use `npm run package -- --bump patch`
2. Rebuild the ZIP (see step 3)
3. Open the Developer Dashboard → click on the DriveMark listing
4. Click **Package** → **Upload new package**
5. Upload the new ZIP
6. Click **Submit for Review**

Chrome auto-updates installed extensions within a few hours of the new version being approved. Note: adding a **new OAuth scope** later re-triggers OAuth verification, but shipping normal code updates does not.

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| Sign-in shows "app isn't verified" warning | OAuth verification not yet approved | Users can click **Advanced → Go to DriveMark (unsafe)** to proceed while verification is pending; the warning disappears once verified |
| Only some accounts can sign in | Consent screen still in **Testing** | Publish the app and finish verification (step 2); testing mode caps you at 100 listed test users |
| Users prompted to approve every time / token expires weekly | Consent screen in "Testing" mode | Publish the consent screen to production (step 2) — testing-mode refresh tokens expire after 7 days |
| "invalid_client" after publishing | CLIENT_ID doesn't match the Web Store extension ID | Update the OAuth credential's Item ID or redirect URI (see step 5) |
| Verification stuck / rejected | Missing privacy policy, unverified domain, or unclear scope justification | Ensure the homepage + privacy policy are live on a Search-Console-verified domain and clearly explain why DriveMark needs `drive.file` |
