// === Popup entry point ===

import { state, setState, setRenderCallback } from './lib/state.js';
import { sendMessage, showError } from './lib/helpers.js';
import { signIn, signOut, LoginScreen } from './auth.js';
import { PickerScreen, loadSpreadsheetPicker, createNewSpreadsheet } from './spreadsheet-picker.js';
import { SaveForm, prefillForm, resetForm } from './save-form.js';
import { BookmarkBrowser } from './browser.js';
import { html, render as preactRender } from './lib/html.js';

// === Routing glue ===

async function handleSignIn(interactive) {
  const result = await signIn(interactive);
  if (!result.ok) return;
  if (result.spreadsheetId) {
    await loadBookmarkView(result.spreadsheetId);
  } else {
    await loadSpreadsheetPicker();
  }
}

async function selectSpreadsheet(id) {
  await chrome.storage.local.set({ spreadsheetId: id });
  await loadBookmarkView(id);
}

async function handleCreateSheet() {
  const id = await createNewSpreadsheet();
  if (id) await selectSpreadsheet(id);
}

async function loadBookmarkView(spreadsheetId) {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  setState({
    screen: 'main',
    spreadsheetId,
    tabUrl: tab.url,
    tabTitle: tab.title,
    existingBookmark: null,
    folders: [],
    bookmarks: [],
    bookmarksLoading: true,
    searchQuery: '',
  });

  resetForm(tab);

  // Phase 1: Show cached data instantly
  try {
    const cached = await sendMessage({
      action: 'getCachedSheetData', spreadsheetId, url: tab.url,
    });
    if (cached) {
      prefillForm(cached.existingBookmark, tab);
      setState({
        existingBookmark: cached.existingBookmark,
        folders: cached.folders,
        bookmarks: cached.bookmarks,
        bookmarksLoading: false,
      });
    }
  } catch {
    // No cache available, spinner continues
  }

  // Phase 2: Revalidate against Google Drive in background
  try {
    const fresh = await sendMessage({
      action: 'getSheetData', spreadsheetId, url: tab.url,
    });
    prefillForm(fresh.existingBookmark, tab);
    setState({
      existingBookmark: fresh.existingBookmark,
      folders: fresh.folders,
      bookmarks: fresh.bookmarks,
      bookmarksLoading: false,
    });
  } catch (err) {
    const isForbidden = /API error (403|404)/.test(err.message);
    if (isForbidden) {
      await chrome.storage.local.remove(['spreadsheetId', 'sheetCache']);
      showError('Your previously selected spreadsheet is no longer accessible. Please create or choose a DriveMark spreadsheet.');
      await loadSpreadsheetPicker();
    } else {
      setState({ bookmarksLoading: false });
      showError(`Failed to load sheet data: ${err.message}`);
    }
  }
}

// === App root component ===

function App() {
  const s = state;
  const sheetHref = s.spreadsheetId
    ? `https://docs.google.com/spreadsheets/d/${s.spreadsheetId}/edit`
    : '#';
  const showBrowser = s.bookmarks.length > 0 || s.bookmarksLoading;

  return html`
    <header>
      <span class="header-brand">
        <img src="icons/icon128.png" alt="" class="header-logo" />
        <h1>DriveMark</h1>
      </span>
    </header>

    ${s.screen === 'login' && html`
      <${LoginScreen} signingIn=${s.signingIn} onSignIn=${handleSignIn} />
    `}

    ${s.screen === 'picker' && html`
      <${PickerScreen}
        pickerLoading=${s.pickerLoading}
        pickerError=${s.pickerError}
        pickerFiles=${s.pickerFiles}
        creatingSheet=${s.creatingSheet}
        newSheetName=${s.newSheetName}
        onSelect=${selectSpreadsheet}
        onCreateSheet=${handleCreateSheet} />
    `}

    ${s.screen === 'main' && html`
      <section id="state-main" class="state">
        <div class="main-columns">
          <div class="panel-browse">
            ${showBrowser && html`
              <div id="search-wrap">
                <input id="search-input" type="text" placeholder="Search bookmarks\u2026"
                       value=${s.searchQuery}
                       onInput=${(e) => setState({ searchQuery: e.target.value })} />
              </div>
              <div id="bookmark-browser">
                <${BookmarkBrowser}
                  bookmarks=${s.bookmarks}
                  bookmarksLoading=${s.bookmarksLoading}
                  searchQuery=${s.searchQuery}
                  openFolders=${s.openFolders}
                  deleteConfirmId=${s.deleteConfirmId}
                  deleting=${s.deleting}
                  spreadsheetId=${s.spreadsheetId}
                  hoveredBookmark=${s.hoveredBookmark} />
              </div>
            `}
          </div>

          <${SaveForm}
            formUrl=${s.formUrl} formTitle=${s.formTitle}
            formExcerpt=${s.formExcerpt} formCover=${s.formCover}
            formFolder=${s.formFolder} formNotes=${s.formNotes}
            formUrlEdited=${s.formUrlEdited}
            saving=${s.saving}
            existingBookmark=${s.existingBookmark}
            folders=${s.folders}
            folderSuggestionsVisible=${s.folderSuggestionsVisible}
            statusText=${s.statusText}
            statusType=${s.statusType} />
        </div>

        <footer>
          <a href=${sheetHref} target="_blank">Open Spreadsheet</a>
          <span class="footer-actions">
            <button class="link-btn" disabled=${s.cleaningUp} onClick=${async () => {
                setState({ cleaningUp: true });
                try {
                  const result = await sendMessage({ action: 'cleanupSheet', spreadsheetId: s.spreadsheetId });
                  if (result.removed > 0) {
                    showError(null);
                    setState({ statusText: 'Sync complete — removed ' + result.removed + ' old rows', statusType: 'success' });
                    await loadBookmarkView(s.spreadsheetId);
                  } else {
                    setState({ statusText: 'Bookmarks are already in sync', statusType: 'success' });
                  }
                } catch (err) {
                  showError('Sync failed: ' + err.message);
                } finally {
                  setState({ cleaningUp: false });
                }
              }}>${s.cleaningUp ? 'Syncing\u2026' : 'Sync Bookmarks'}</button>
            <button class="link-btn" onClick=${async () => {
              await chrome.storage.local.remove(['spreadsheetId', 'sheetCache']);
              await loadSpreadsheetPicker();
            }}>Choose Spreadsheet</button>
            <button class="link-btn" onClick=${signOut}>Sign Out</button>
          </span>
        </footer>
      </section>
    `}

    ${s.globalError && html`
      <div id="global-error">${s.globalError}</div>
    `}
  `;
}

// === Init ===

function mount() {
  preactRender(html`<${App} />`, document.getElementById('app'));
}

setRenderCallback(mount);
mount();
handleSignIn(false);
