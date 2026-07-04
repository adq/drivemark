# Chrome Extension Development

How to set up and run the DriveMark Chrome extension locally as an unpacked extension.

## Prerequisites

- Google Chrome (or Chromium-based browser)
- A Google account
- Access to [Google Cloud Console](https://console.cloud.google.com)
- Node.js + npm (to build the vendored Preact/htm libs)

---

## 1. Create a Google Cloud Project

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Click the project dropdown at the top → **New Project**
3. Name it (e.g. `drivemark-dev`) → **Create**
4. Make sure the new project is selected in the dropdown

### Enable APIs

Go to **APIs & Services → Library** and enable both:

- **Google Sheets API**
- **Google Drive API**

Search for each by name, click into it, and click **Enable**.

---

## 2. Configure the OAuth Consent Screen

Google requires a consent screen before you can create OAuth credentials.

1. Go to **APIs & Services → OAuth consent screen**
2. Choose a user type:
   - **Internal** — if you're on Google Workspace. All members of your org can authenticate immediately, no further setup needed.
   - **External** — if you're using a personal Gmail account. You'll need to add test users (see step 8).
3. Click **Create**
4. Fill in the required fields:
   - **App name**: `DriveMark` (or anything)
   - **User support email**: your email
   - **Developer contact email**: your email
5. Click **Save and Continue**
6. On the **Scopes** page, click **Add or Remove Scopes** and add:
   - `https://www.googleapis.com/auth/drive.file`
7. Click **Update**, then **Save and Continue**
8. On the **Test users** page *(External only)*: click **Add Users** and enter the Google account emails that need access. Skip this if you chose Internal.
9. Click **Save and Continue** through to the summary

> **Why test users?** External apps start in "Testing" mode. Only accounts listed as test users can complete the OAuth flow — everyone else gets a blocked screen. Internal apps don't have this restriction.

---

## 3. Get a Stable Extension ID

Chrome assigns each unpacked extension an ID. By default this ID **changes every time you remove and re-load the extension**, which breaks OAuth credentials tied to the old ID.

To get a stable ID, add a `key` field to `manifest.json`:

### Generate the key

1. Open `chrome://extensions` and enable **Developer mode**
2. Click **Pack extension**
3. For **Extension root directory**, browse to the `drivemark/chrome-extension/` folder
4. Leave **Private key file** blank (first time)
5. Click **Pack Extension**

Chrome creates two files next to the folder:
- `drivemark.crx` — the packed extension (you can delete this)
- `drivemark.pem` — the private key (keep this safe, don't commit it)

Now extract the public key:

```sh
openssl rsa -in drivemark.pem -pubout -outform DER | base64 -w0
```

This outputs a long base64 string. Copy it and add it to `manifest.json`:

```json
{
  "manifest_version": 3,
  "name": "DriveMark",
  "key": "MIIBIjANBgkqh...your_base64_key_here...",
  ...
}
```

Now every time you load the extension unpacked, it gets the same deterministic ID.

### Alternative: skip the key

If you just want to get going quickly, you can skip the `key` field. Just be aware that if the extension ID changes (e.g. you delete and re-load it), you'll need to update the **redirect URI** in your Google Cloud OAuth credential to match the new ID.

---

## 4. Load the Extension

First build the vendored libraries (Preact/htm are loaded from `vendor/`, a gitignored build artifact):

```sh
cd chrome-extension
npm install        # installs deps and builds vendor/ via the postinstall hook
```

Then load it:

1. Open `chrome://extensions`
2. Enable **Developer mode** (toggle in the top-right)
3. Click **Load unpacked** and select the `drivemark/chrome-extension/` folder
4. Copy the **Extension ID** shown under the extension name — a 32-character string like `abcdefghijklmnopqrstuvwxyzabcdef`

You'll need this ID for the next step.

---

## 5. Create OAuth Credentials

1. Back in Google Cloud Console, go to **APIs & Services → Credentials**
2. Click **Create Credentials → OAuth client ID**
3. For **Application type**, select **Web application**
4. Under **Authorized redirect URIs**, click **Add URI** and enter:
   ```
   https://<your-extension-id>.chromiumapp.org/
   ```
   Replace `<your-extension-id>` with the 32-character ID you copied in Step 4.
5. Click **Create**
6. Copy the generated **Client ID** (ends in `.apps.googleusercontent.com`)

> **Note:** You do not need to add Authorized JavaScript origins — only the redirect URI is needed.

---

## 6. Configure and Reload

Create your dev config from the template (`config.js` is gitignored and not committed):

```sh
cp config.example.js config.js
```

Open `config.js` and set `CLIENT_ID` to the client ID you copied — this is the dev config the extension imports directly (`background.js` loads `config.js`):

```js
export const CLIENT_ID = '123456789-abc.apps.googleusercontent.com';
```

(There is also a `config.prod.example.js` → `config.prod.js` for production — see `docs/publication.md`. The publish script swaps prod values into `config.js` when packaging.)

Then reload the extension:

1. Go to `chrome://extensions`
2. Click the **reload icon** (circular arrow) on the DriveMark card

Click the extension icon in the toolbar — you should see the sign-in screen.

---

## Development Workflow

### After editing popup files (popup.html / popup.js / popup.css)

Close and reopen the popup. Changes load automatically.

### After editing background.js or manifest.json

Click the **reload icon** on `chrome://extensions`, then reopen the popup.

### Inspecting the popup

Right-click inside the open popup → **Inspect** to open DevTools.

### Inspecting the service worker

On `chrome://extensions`, click the **Service Worker** link next to DriveMark. This opens DevTools for `background.js` — console logs, network requests, breakpoints all work here.

### Debugging auth failures

If you see "Sign-in failed. Please try again." in the popup, the real error is in the service worker console — the popup only shows the final message.

1. Open `chrome://extensions`
2. Click the **Service Worker** link next to DriveMark
3. Open the **Console** tab in the DevTools window that appears
4. Click "Sign in with Google" in the popup again
5. Read the actual error logged there

Common errors and what they mean:

| Error | Cause | Fix |
|-------|-------|-----|
| `OAuth2 not granted or revoked` | Extension ID doesn't match the Item ID in your OAuth credential | Check the ID at `chrome://extensions` and compare it to the Item ID in Cloud Console → Credentials |
| `invalid_client` | Wrong client ID in `manifest.json` | Re-copy the client ID from Cloud Console |
| `Access blocked: DriveMark has not completed verification` | External app, account not listed as test user | Add your email at Cloud Console → OAuth consent screen → Test users |
| `Error calling getAuthToken` | Chrome doesn't have a Google account signed in | Sign into a Google account at `chrome://settings` |
| `net::ERR_INTERNET_DISCONNECTED` | No network | Check connection |

### Clearing OAuth tokens

If you need to reset auth state during development:

```javascript
// In the service worker DevTools console
chrome.storage.local.remove(['authToken', 'authTokenExpiry'], () => console.log('Auth cleared'));
```

---

## Spreadsheet Setup

The extension auto-creates column headers (`URL`, `Title`, `Folder`, `Date Added`, `Notes`, `Excerpt`, `Cover`, `ID`) on the first save if the sheet is empty. Existing rows without an ID are automatically assigned a UUID when bookmarks are loaded.

The target sheet tab must be named **Sheet1** (the default for new Google Sheets).

---

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| Sign-in popup opens then closes with no token | Redirect URI mismatch | Check the URI in Cloud Console matches `https://<extension-id>.chromiumapp.org/` exactly |
| "redirect_uri_mismatch" error | Redirect URI not registered | Add `https://<extension-id>.chromiumapp.org/` under Authorized redirect URIs in Cloud Console |
| Sign-in popup never opens | `CLIENT_ID` placeholder not replaced | Set `CLIENT_ID` in `config.js` (`cp config.example.js config.js` first) |
| "Failed to obtain auth token" | Account not listed as test user (External app) | Add your email on the OAuth consent screen → Test users |
| Login screen appears every time | Consent screen in testing mode | Add yourself as a test user; or publish the consent screen |
| OAuth prompt blocked / "access denied" | Scopes mismatch | Ensure the scopes in the consent screen match those used in `background.js` |
| "API error 403" on save | API not enabled | Enable Google Sheets API in Cloud Console → APIs & Services → Library |
| Spreadsheet list is empty | Drive API not enabled | Enable Google Drive API in Cloud Console |
| Extension ID changed after reload | No `key` in manifest.json | Either add a `key` (see Step 3) or update the redirect URI in your OAuth credential |
