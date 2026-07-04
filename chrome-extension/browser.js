// === Bookmark Browser (Preact components) ===

import { html } from './lib/html.js';
import { state, setState } from './lib/state.js';
import { formatDate, sendMessage, showError } from './lib/helpers.js';
import { buildFolderTree, countBookmarks, filterBookmarks, groupByFolder } from './lib/tree.js';

// --- Hover logic ---

let hoverTimer = null;
let scrollListenerAttached = false;

function handleBookmarkHover(bookmark, e) {
  clearTimeout(hoverTimer);
  const rect = e.currentTarget.getBoundingClientRect();
  hoverTimer = setTimeout(() => {
    setState({
      hoveredBookmark: {
        bookmark,
        rect: { top: rect.top, left: rect.left, right: rect.right, bottom: rect.bottom },
      },
    });
  }, 300);
}

function handleBookmarkLeave() {
  clearTimeout(hoverTimer);
  hoverTimer = setTimeout(() => {
    setState({ hoveredBookmark: null });
  }, 100);
}

function ensureScrollDismiss() {
  if (scrollListenerAttached) return;
  const el = document.getElementById('bookmark-browser');
  if (el) {
    el.addEventListener('scroll', () => {
      clearTimeout(hoverTimer);
      if (state.hoveredBookmark) setState({ hoveredBookmark: null });
    }, { passive: true });
    scrollListenerAttached = true;
  }
}

// --- Components ---

function DeleteConfirm({ bookmark, deleting, spreadsheetId }) {
  const label = (bookmark.title || bookmark.url).slice(0, 30);

  async function confirmDelete() {
    setState({ deleting: true });
    try {
      await sendMessage({
        action: 'deleteBookmark',
        spreadsheetId,
        bookmarkId: bookmark.id,
      });
      const updated = state.bookmarks.filter(bk => bk.id !== bookmark.id);
      setState({
        bookmarks: updated,
        folders: [...new Set(updated.map(bk => bk.folder).filter(Boolean))].sort(),
        deleteConfirmId: null,
        deleting: false,
      });
    } catch (err) {
      showError(`Delete failed: ${err.message}`);
      setState({ deleting: false });
    }
  }

  return html`
    <div class="bookmark-item bookmark-confirm">
      <span class="confirm-text">Delete "${label}"?</span>
      <button type="button" class="btn-confirm-delete" disabled=${deleting}
              onClick=${confirmDelete}>Delete</button>
      <button type="button" class="btn-cancel-delete"
              onClick=${() => setState({ deleteConfirmId: null })}>Cancel</button>
    </div>
  `;
}

function BookmarkPopover({ hoveredBookmark }) {
  if (!hoveredBookmark) return null;

  const { bookmark, rect } = hoveredBookmark;
  const popoverWidth = 320;
  const popoverMaxHeight = 400;

  let left = rect.right + 8;
  let top = rect.top;

  if (left + popoverWidth > 676) {
    left = rect.left - popoverWidth - 8;
  }
  if (top + popoverMaxHeight > window.innerHeight) {
    top = window.innerHeight - popoverMaxHeight - 4;
  }
  if (top < 4) top = 4;

  return html`
    <div class="bookmark-popover"
         style=${{ position: 'fixed', top: `${top}px`, left: `${left}px`, width: `${popoverWidth}px`, maxHeight: `${popoverMaxHeight}px` }}
         onMouseEnter=${() => clearTimeout(hoverTimer)}
         onMouseLeave=${handleBookmarkLeave}>
      ${bookmark.cover && html`
        <img src=${bookmark.cover} class="popover-cover" alt="" />
      `}
      <div class="popover-body">
        <div class="popover-title">${bookmark.title || bookmark.url}</div>
        <a href=${bookmark.url} target="_blank" class="popover-url">${bookmark.url}</a>
        ${bookmark.excerpt && html`
          <div class="popover-section">
            <div class="popover-label">Excerpt</div>
            <div class="popover-text">${bookmark.excerpt}</div>
          </div>
        `}
        ${bookmark.notes && html`
          <div class="popover-section">
            <div class="popover-label">Notes</div>
            <div class="popover-text">${bookmark.notes}</div>
          </div>
        `}
        <div class="popover-meta">
          <span>${formatDate(bookmark.dateAdded)}</span>
          ${bookmark.folder && html`<span>${bookmark.folder}</span>`}
        </div>
      </div>
    </div>
  `;
}

function BookmarkItem({ bookmark, deleteConfirmId, deleting, spreadsheetId }) {
  if (deleteConfirmId === bookmark.id) {
    return html`<${DeleteConfirm} bookmark=${bookmark} deleting=${deleting}
                                   spreadsheetId=${spreadsheetId} />`;
  }

  return html`
    <div class="bookmark-item"
         onMouseEnter=${(e) => handleBookmarkHover(bookmark, e)}
         onMouseLeave=${handleBookmarkLeave}>
      ${bookmark.cover
        ? html`<img src=${bookmark.cover} class="bookmark-cover" alt="" loading="lazy" />`
        : html`<div class="bookmark-cover-placeholder" />`}
      <div class="bookmark-info">
        <a href=${bookmark.url} target="_blank" class="bookmark-title truncate"
           title=${bookmark.url}>${bookmark.title || bookmark.url}</a>
        ${bookmark.excerpt && html`
          <div class="bookmark-excerpt">${bookmark.excerpt}</div>
        `}
        <span class="bookmark-date">${formatDate(bookmark.dateAdded)}</span>
      </div>
      <button type="button" class="bookmark-delete" title="Delete bookmark"
              onClick=${() => { clearTimeout(hoverTimer); setState({ deleteConfirmId: bookmark.id, hoveredBookmark: null }); }}>${'\u00D7'}</button>
    </div>
  `;
}

function FolderNode({ node, name, path, depth, query, openFolders, deleteConfirmId, deleting, spreadsheetId }) {
  const total = countBookmarks(node);
  if (total === 0) return null;

  const isOpen = query ? true : !!openFolders[path];
  const arrow = isOpen ? '\u25BC' : '\u25B6';

  function toggle() {
    setState({ openFolders: { ...openFolders, [path]: !openFolders[path] } });
  }

  const childNames = Object.keys(node.children).sort();

  return html`
    <div class="folder">
      <button type="button" class="folder-header"
              style=${{ paddingLeft: `${8 + depth * 16}px` }}
              onClick=${toggle}>
        <span class="folder-arrow">${arrow}</span>
        ${' '}${name} <span class="folder-count">(${total})</span>
      </button>
      ${isOpen && html`
        ${childNames.map(child => html`
          <${FolderNode}
            node=${node.children[child]} name=${child}
            path=${path ? `${path}/${child}` : child}
            depth=${depth + 1} query=${query}
            openFolders=${openFolders}
            deleteConfirmId=${deleteConfirmId}
            deleting=${deleting}
            spreadsheetId=${spreadsheetId} />
        `)}
        ${node.bookmarks.length > 0 && html`
          <div class="folder-items" style=${{ paddingLeft: `${22 + depth * 16}px` }}>
            ${node.bookmarks.map(b => html`
              <${BookmarkItem} key=${b.id} bookmark=${b}
                deleteConfirmId=${deleteConfirmId}
                deleting=${deleting}
                spreadsheetId=${spreadsheetId} />
            `)}
          </div>
        `}
      `}
    </div>
  `;
}

// --- Root component ---

export function BookmarkBrowser({
  bookmarks, bookmarksLoading, searchQuery,
  openFolders, deleteConfirmId, deleting, spreadsheetId,
  hoveredBookmark,
}) {
  ensureScrollDismiss();

  if (bookmarksLoading) {
    return html`<div>Loading bookmarks\u2026</div>`;
  }

  if (bookmarks.length === 0) return null;

  const query = searchQuery.toLowerCase();
  const filtered = filterBookmarks(bookmarks, query);

  if (query && filtered.length === 0) {
    return html`<div>No bookmarks match your search.</div>`;
  }

  const groups = groupByFolder(filtered);
  const tree = buildFolderTree(groups);
  const topNames = Object.keys(tree.children).sort();

  return html`
    ${topNames.map(name => html`
      <${FolderNode}
        node=${tree.children[name]} name=${name}
        path=${name} depth=${0} query=${query}
        openFolders=${openFolders}
        deleteConfirmId=${deleteConfirmId}
        deleting=${deleting}
        spreadsheetId=${spreadsheetId} />
    `)}
    <${BookmarkPopover} hoveredBookmark=${hoveredBookmark} />
  `;
}

