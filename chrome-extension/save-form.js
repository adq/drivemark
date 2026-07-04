// === Save Form ===

import { html } from './lib/html.js';
import { state, setState } from './lib/state.js';
import { sendMessage, showStatus } from './lib/helpers.js';

// --- Component ---

export function SaveForm({
  formUrl, formTitle, formExcerpt, formCover, formFolder, formNotes,
  saving, existingBookmark, folders, folderSuggestionsVisible,
  statusText, statusType,
}) {
  const query = formFolder.toLowerCase();
  const matches = folders.filter(c => c.toLowerCase().includes(query));
  const showSuggestions = folderSuggestionsVisible
    && matches.length > 0
    && !(matches.length === 1 && matches[0].toLowerCase() === query);

  return html`
    <div class="panel-save">
      <div class="field">
        <label for="url-input">URL</label>
        <input id="url-input" placeholder="https://example.com" autocomplete="off"
               value=${formUrl}
               onInput=${(e) => setState({ formUrl: e.target.value, formUrlEdited: true })} />
      </div>

      <div class="field">
        <label for="title-input">Title</label>
        <input id="title-input" placeholder="Page title" autocomplete="off"
               value=${formTitle}
               onInput=${(e) => setState({ formTitle: e.target.value })} />
      </div>

      <div class="field">
        <label for="excerpt-input">Excerpt <span class="optional">(optional)</span></label>
        <textarea id="excerpt-input" placeholder="A few lines describing this page" rows="2"
                  value=${formExcerpt}
                  onInput=${(e) => setState({ formExcerpt: e.target.value })} />
      </div>

      <div class="field">
        <label for="cover-input">Cover Image <span class="optional">(optional)</span></label>
        <input id="cover-input" placeholder="https://example.com/image.jpg" autocomplete="off"
               value=${formCover}
               onInput=${(e) => setState({ formCover: e.target.value })} />
      </div>

      <div class="field">
        <label for="folder-input">Folder</label>
        <div class="autocomplete-wrap">
          <input id="folder-input" placeholder="Type or select a folder" autocomplete="off"
                 value=${formFolder}
                 onInput=${(e) => setState({ formFolder: e.target.value, folderSuggestionsVisible: true })}
                 onFocus=${() => setState({ folderSuggestionsVisible: true })}
                 onBlur=${() => setState({ folderSuggestionsVisible: false })} />
          ${showSuggestions && html`
            <div class="suggestions">
              ${matches.map(cat => html`
                <button type="button"
                        onMousedown=${(e) => {
                          e.preventDefault();
                          setState({ formFolder: cat, folderSuggestionsVisible: false });
                        }}>
                  ${cat}
                </button>
              `)}
            </div>
          `}
        </div>
      </div>

      <div class="field">
        <label for="notes-input">Notes <span class="optional">(optional)</span></label>
        <textarea id="notes-input" placeholder="Add notes about this bookmark" rows="2"
                  value=${formNotes}
                  onInput=${(e) => setState({ formNotes: e.target.value })} />
      </div>

      <button class="primary" disabled=${saving}
              onClick=${() => saveBookmark()}>
        ${saving ? 'Saving\u2026' : (existingBookmark ? 'Update Bookmark' : 'Save Bookmark')}
      </button>

      ${statusText && html`
        <div class=${`status-${statusType}`}>${statusText}</div>
      `}
    </div>
  `;
}

// --- Business logic ---

export function prefillForm(existingBookmark, tab) {
  if (existingBookmark) {
    setState({
      formUrl: existingBookmark.url || tab.url,
      formTitle: existingBookmark.title || tab.title,
      formExcerpt: existingBookmark.excerpt || '',
      formCover: existingBookmark.cover || '',
      formFolder: existingBookmark.folder,
      formNotes: existingBookmark.notes,
    });
  } else {
    setState({ formTitle: tab.title || '' });
    sendMessage({ action: 'extractPageMeta', tabId: tab.id })
      .then(meta => {
        const patch = {};
        if (!state.formExcerpt) patch.formExcerpt = meta.excerpt || '';
        if (!state.formCover) patch.formCover = meta.cover || '';
        if (Object.keys(patch).length) setState(patch);
      })
      .catch(() => {});
  }
}

export function resetForm(tab) {
  setState({
    formUrl: tab.url || '',
    formTitle: '',
    formExcerpt: '',
    formCover: '',
    formFolder: '',
    formNotes: '',
    formUrlEdited: false,
    searchQuery: '',
    statusText: null,
    statusType: null,
  });
}

export async function saveBookmark() {
  const url = state.formUrl.trim();
  const title = state.formTitle.trim();
  const folder = state.formFolder.trim();
  if (!folder) {
    showStatus('Please enter a folder.', 'error');
    return;
  }
  const notes = state.formNotes.trim();
  const excerpt = state.formExcerpt.trim();
  const cover = state.formCover.trim();

  setState({ saving: true });

  try {
    if (state.existingBookmark) {
      await sendMessage({
        action: 'updateBookmark',
        spreadsheetId: state.spreadsheetId,
        bookmark: {
          url,
          title: title || state.tabTitle,
          folder,
          dateAdded: state.existingBookmark.dateAdded,
          notes,
          excerpt,
          cover,
          id: state.existingBookmark.id,
        },
      });
      showStatus('Bookmark updated!', 'success');
      setState({
        saving: false,
        existingBookmark: { ...state.existingBookmark, title: title || state.tabTitle, folder, notes, excerpt, cover },
      });
    } else {
      await sendMessage({
        action: 'addBookmark',
        spreadsheetId: state.spreadsheetId,
        bookmark: {
          url,
          title: title || state.tabTitle,
          folder,
          notes,
          excerpt,
          cover,
        },
      });
      showStatus('Bookmark saved!', 'success');
      setState({ saving: false });
    }

    // Refresh from cache
    sendMessage({ action: 'getSheetData', spreadsheetId: state.spreadsheetId, url: state.tabUrl })
      .then(({ folders, bookmarks, existingBookmark }) => setState({
        folders,
        bookmarks,
        existingBookmark,
      }))
      .catch(() => {});
  } catch (err) {
    showStatus(`Save failed: ${err.message}`, 'error');
    setState({ saving: false });
  }
}
