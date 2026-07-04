// === Auth ===

import { html } from './lib/html.js';
import { state, setState } from './lib/state.js';
import { sendMessage, showError } from './lib/helpers.js';

// --- Component ---

export function LoginScreen({ signingIn, onSignIn }) {
  return html`
    <section id="state-login" class="state">
      <p>Sign in with your Google account to save bookmarks to a spreadsheet.</p>
      <button class="primary" disabled=${signingIn}
              onClick=${() => onSignIn(true)}>
        ${signingIn ? 'Signing in\u2026' : 'Sign in with Google'}
      </button>
    </section>
  `;
}

// --- Business logic ---

export async function signIn(interactive) {
  if (interactive) setState({ signingIn: true });

  try {
    await sendMessage({ action: 'authenticate', interactive });

    const stored = await chrome.storage.local.get('spreadsheetId');
    setState({ signingIn: false });

    return { ok: true, spreadsheetId: stored.spreadsheetId || null };
  } catch (err) {
    if (interactive) {
      console.error('[DriveMark] Sign-in error:', err.message);
      const isForbidden = /API error (403|404)/.test(err.message);
      if (isForbidden) {
        await chrome.storage.local.remove(['spreadsheetId', 'sheetCache']);
        setState({ signingIn: false });
        return { ok: true, spreadsheetId: null };
      }
      const isNotSignedIn = /not signed in|sign in/i.test(err.message);
      if (isNotSignedIn) {
        showError('Chrome isn\'t signed into a Google account. Click your profile icon in the top-right of Chrome \u2192 "Sign in to Chrome", then try again.');
      } else {
        showError(`Sign-in failed: ${err.message}`);
      }
      setState({ signingIn: false });
      return { ok: false };
    } else {
      setState({ screen: 'login', signingIn: false });
      return { ok: false };
    }
  }
}

export async function signOut() {
  await sendMessage({ action: 'signOut' });
  setState({
    screen: 'login', spreadsheetId: null,
    bookmarks: [], folders: [], existingBookmark: null,
  });
}
