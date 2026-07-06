import { parseRows, deduplicateBookmarks, deriveFolders, findExistingBookmark, tsMillis } from './lib/data.js';
import {
  SHEET_NAME, RANGE_ALL, RANGE_HEADER, HEADERS, LETTER_ID,
  COL_URL, COL_DATE_ADDED, COL_ID, COL_MODIFIED,
} from './lib/columns.js';
import { CLIENT_ID } from './config.js';

const SHEETS_BASE = 'https://sheets.googleapis.com/v4/spreadsheets';
const DRIVE_BASE = 'https://www.googleapis.com/drive/v3/files';

const SCOPES = [
  'https://www.googleapis.com/auth/drive.file',
].join(' ');

// --- Auth helpers ---

function getRedirectUri() {
  return `https://${chrome.runtime.id}.chromiumapp.org/`;
}

async function launchOAuth(interactive) {
  const authUrl = new URL('https://accounts.google.com/o/oauth2/v2/auth');
  authUrl.searchParams.set('client_id', CLIENT_ID);
  authUrl.searchParams.set('redirect_uri', getRedirectUri());
  authUrl.searchParams.set('response_type', 'token');
  authUrl.searchParams.set('scope', SCOPES);
  authUrl.searchParams.set('prompt', interactive ? 'consent' : 'none');

  let redirectUrl;
  try {
    redirectUrl = await chrome.identity.launchWebAuthFlow({
      url: authUrl.toString(),
      interactive,
    });
  } catch (err) {
    console.error('[DriveMark] launchWebAuthFlow failed:', err.message);
    throw err;
  }

  if (!redirectUrl) {
    throw new Error('No redirect URL returned from OAuth flow');
  }

  // Token is in the URL fragment: https://....chromiumapp.org/#access_token=...&expires_in=...
  const fragment = new URL(redirectUrl).hash.slice(1);
  const params = new URLSearchParams(fragment);
  const token = params.get('access_token');
  const expiresIn = parseInt(params.get('expires_in') || '3600', 10);

  if (!token) {
    throw new Error('No access token in OAuth redirect response');
  }

  await chrome.storage.local.set({
    authToken: token,
    authTokenExpiry: Date.now() + (expiresIn - 60) * 1000, // 60s buffer
  });

  return token;
}

async function getAuthToken(interactive = false) {
  const { authToken, authTokenExpiry } = await chrome.storage.local.get(['authToken', 'authTokenExpiry']);

  if (authToken && authTokenExpiry && Date.now() < authTokenExpiry) {
    return authToken;
  }

  // Try silent re-auth first, then interactive
  if (!interactive) {
    try {
      return await launchOAuth(false);
    } catch {
      throw new Error('Not authenticated');
    }
  }

  return await launchOAuth(true);
}

async function signOut() {
  await chrome.storage.local.remove([
    'authToken', 'authTokenExpiry',
    'spreadsheetId', 'sheetCache',
  ]);
  cache.spreadsheetId = null;
  cache.modifiedTime = null;
  cache.bookmarks = null;
}

async function authedFetch(url, options = {}) {
  let token = await getAuthToken(false);

  const makeRequest = (t) => fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${t}`,
      'Content-Type': 'application/json',
    },
  });

  let response = await makeRequest(token);

  if (response.status === 401) {
    // Token rejected — clear it and re-authenticate interactively
    await chrome.storage.local.remove(['authToken', 'authTokenExpiry']);
    token = await launchOAuth(true);
    response = await makeRequest(token);
  }

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API error ${response.status}: ${body}`);
  }

  return response.json();
}

// --- Google Drive API ---

async function listSpreadsheets() {
  const query = encodeURIComponent("mimeType='application/vnd.google-apps.spreadsheet'");
  const fields = encodeURIComponent('files(id,name,modifiedTime)');
  const url = `${DRIVE_BASE}?q=${query}&fields=${fields}&orderBy=modifiedTime desc&pageSize=50`;
  const result = await authedFetch(url);
  return result.files || [];
}

async function getModifiedTime(spreadsheetId) {
  const fields = encodeURIComponent('modifiedTime');
  const url = `${DRIVE_BASE}/${spreadsheetId}?fields=${fields}`;
  const result = await authedFetch(url);
  return result.modifiedTime;
}

// --- Cache (persisted to chrome.storage.local to survive SW restarts) ---

const cache = {
  spreadsheetId: null,
  modifiedTime: null,
  bookmarks: null,
};

async function persistCache() {
  if (cache.spreadsheetId && cache.bookmarks) {
    await chrome.storage.local.set({
      sheetCache: {
        spreadsheetId: cache.spreadsheetId,
        modifiedTime: cache.modifiedTime,
        bookmarks: cache.bookmarks,
      },
    });
  }
}

async function restoreCache() {
  if (cache.bookmarks) return;
  const { sheetCache } = await chrome.storage.local.get('sheetCache');
  if (sheetCache) {
    cache.spreadsheetId = sheetCache.spreadsheetId;
    cache.modifiedTime = sheetCache.modifiedTime;
    cache.bookmarks = sheetCache.bookmarks;
  }
}

async function getCachedSheetData(spreadsheetId, url) {
  await restoreCache();
  if (cache.spreadsheetId === spreadsheetId && cache.bookmarks) {
    return {
      bookmarks: cache.bookmarks,
      folders: deriveFolders(cache.bookmarks),
      existingBookmark: findExistingBookmark(cache.bookmarks, url),
    };
  }
  return null;
}

async function backfillIds(spreadsheetId, allRows) {
  const data = [];
  for (let i = 0; i < allRows.length; i++) {
    if (!allRows[i].id) {
      allRows[i].id = crypto.randomUUID();
      data.push({
        range: `${SHEET_NAME}!${LETTER_ID}${i + 2}`, // i+2: header is row 1, data starts at row 2
        values: [[allRows[i].id]],
      });
    }
  }

  if (data.length > 0) {
    const url = `${SHEETS_BASE}/${spreadsheetId}/values:batchUpdate`;
    await authedFetch(url, {
      method: 'POST',
      body: JSON.stringify({ valueInputOption: 'RAW', data }),
    });
  }

  return deduplicateBookmarks(allRows);
}

async function createSpreadsheet(title) {
  const result = await authedFetch(SHEETS_BASE, {
    method: 'POST',
    body: JSON.stringify({ properties: { title } }),
  });
  await ensureHeaders(result.spreadsheetId);
  return { id: result.spreadsheetId, name: result.properties.title };
}

// --- Google Sheets API ---

async function ensureHeaders(spreadsheetId) {
  const range = encodeURIComponent(RANGE_HEADER);
  const url = `${SHEETS_BASE}/${spreadsheetId}/values/${range}`;
  const result = await authedFetch(url);

  if (!result.values || result.values.length === 0) {
    const writeRange = encodeURIComponent(RANGE_HEADER);
    const writeUrl = `${SHEETS_BASE}/${spreadsheetId}/values/${writeRange}?valueInputOption=RAW`;
    await authedFetch(writeUrl, {
      method: 'PUT',
      body: JSON.stringify({
        values: [HEADERS],
      }),
    });
  }
}

async function fetchBookmarks(spreadsheetId) {
  const range = encodeURIComponent(RANGE_ALL);
  const url = `${SHEETS_BASE}/${spreadsheetId}/values/${range}`;
  const result = await authedFetch(url);
  const allRows = parseRows(result.values || []);
  return backfillIds(spreadsheetId, allRows);
}

async function getSheetData(spreadsheetId, url) {
  await restoreCache();
  const modifiedTime = await getModifiedTime(spreadsheetId);

  if (cache.spreadsheetId === spreadsheetId && cache.modifiedTime === modifiedTime && cache.bookmarks) {
    // Cache hit — no Sheets API call needed
    const existing = findExistingBookmark(cache.bookmarks, url);
    return {
      bookmarks: cache.bookmarks,
      folders: deriveFolders(cache.bookmarks),
      existingBookmark: existing,
    };
  }

  // Cache miss — fetch fresh data (1 Sheets API call)
  const bookmarks = await fetchBookmarks(spreadsheetId);
  cache.spreadsheetId = spreadsheetId;
  cache.modifiedTime = modifiedTime;
  cache.bookmarks = bookmarks;
  await persistCache();

  const existing = findExistingBookmark(bookmarks, url);
  return {
    bookmarks,
    folders: deriveFolders(bookmarks),
    existingBookmark: existing,
  };
}

async function addBookmark(spreadsheetId, bookmark) {
  await ensureHeaders(spreadsheetId);
  const range = encodeURIComponent(RANGE_ALL);
  const url = `${SHEETS_BASE}/${spreadsheetId}/values/${range}:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS`;
  const dateAdded = new Date().toISOString();
  const modified = dateAdded;
  const id = crypto.randomUUID();
  const body = {
    values: [[
      bookmark.url,
      bookmark.title,
      bookmark.folder,
      dateAdded,
      bookmark.notes || '',
      bookmark.excerpt || '',
      bookmark.cover || '',
      id,
      modified,
    ]],
  };
  const result = await authedFetch(url, {
    method: 'POST',
    body: JSON.stringify(body),
  });

  // Optimistic cache update
  if (cache.spreadsheetId === spreadsheetId && cache.bookmarks) {
    cache.bookmarks.push({
      url: bookmark.url,
      title: bookmark.title,
      folder: bookmark.folder,
      dateAdded,
      notes: bookmark.notes || '',
      excerpt: bookmark.excerpt || '',
      cover: bookmark.cover || '',
      id,
      modified,
    });
    cache.modifiedTime = await getModifiedTime(spreadsheetId);
    await persistCache();
  }

  return result;
}

async function updateBookmark(spreadsheetId, bookmark) {
  const range = encodeURIComponent(RANGE_ALL);
  const url = `${SHEETS_BASE}/${spreadsheetId}/values/${range}:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS`;
  const modified = new Date().toISOString();
  const body = {
    values: [[
      bookmark.url,
      bookmark.title,
      bookmark.folder,
      bookmark.dateAdded,
      bookmark.notes || '',
      bookmark.excerpt || '',
      bookmark.cover || '',
      bookmark.id,
      modified,
    ]],
  };
  const result = await authedFetch(url, {
    method: 'POST',
    body: JSON.stringify(body),
  });

  // Optimistic cache update: replace entry with matching id
  if (cache.spreadsheetId === spreadsheetId && cache.bookmarks) {
    const idx = cache.bookmarks.findIndex(b => b.id === bookmark.id);
    if (idx !== -1) {
      cache.bookmarks[idx] = {
        url: bookmark.url,
        title: bookmark.title,
        folder: bookmark.folder,
        dateAdded: bookmark.dateAdded,
        notes: bookmark.notes || '',
        excerpt: bookmark.excerpt || '',
        cover: bookmark.cover || '',
        id: bookmark.id,
        modified,
      };
    }
    cache.modifiedTime = await getModifiedTime(spreadsheetId);
    await persistCache();
  }

  return result;
}

async function deleteBookmark(spreadsheetId, bookmarkId) {
  // Find the existing bookmark to preserve dateAdded
  let existing;
  if (cache.spreadsheetId === spreadsheetId && cache.bookmarks) {
    existing = cache.bookmarks.find(b => b.id === bookmarkId);
  }
  if (!existing) {
    const bookmarks = await fetchBookmarks(spreadsheetId);
    existing = bookmarks.find(b => b.id === bookmarkId);
    if (!existing) throw new Error('Bookmark not found');
  }

  // Append a tombstone row (empty URL signals deletion)
  const range = encodeURIComponent(RANGE_ALL);
  const url = `${SHEETS_BASE}/${spreadsheetId}/values/${range}:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS`;
  const modified = new Date().toISOString();
  await authedFetch(url, {
    method: 'POST',
    body: JSON.stringify({
      values: [['', '', '', existing.dateAdded, '', '', '', bookmarkId, modified]],
    }),
  });

  // Optimistic cache update: remove from cache
  if (cache.spreadsheetId === spreadsheetId && cache.bookmarks) {
    cache.bookmarks = cache.bookmarks.filter(b => b.id !== bookmarkId);
    cache.modifiedTime = await getModifiedTime(spreadsheetId);
    await persistCache();
  }
}

// --- Sheet cleanup (removes superseded and tombstone rows) ---

async function cleanupSheet(spreadsheetId) {
  const range = encodeURIComponent(RANGE_ALL);
  const url = `${SHEETS_BASE}/${spreadsheetId}/values/${range}`;
  const result = await authedFetch(url);
  const rows = result.values || [];
  if (rows.length <= 1) return { removed: 0 };

  const dataRows = rows.slice(1); // skip header
  // Group by ID, track which raw indexes are superseded
  const byId = new Map();
  for (let i = 0; i < dataRows.length; i++) {
    const row = dataRows[i];
    const id = row[COL_ID] || '';
    if (!id) continue;
    const modified = tsMillis(row[COL_MODIFIED] || row[COL_DATE_ADDED] || ''); // fall back to dateAdded
    const sheetRow = i + 2; // 1-based (header = row 1)

    if (!byId.has(id)) {
      byId.set(id, { bestIndex: sheetRow, bestTs: modified, allIndexes: [sheetRow] });
    } else {
      const entry = byId.get(id);
      entry.allIndexes.push(sheetRow);
      if (modified >= entry.bestTs) {
        entry.bestTs = modified;
        entry.bestIndex = sheetRow;
      }
    }
  }

  const rowsToDelete = [];
  for (const [, entry] of byId) {
    for (const idx of entry.allIndexes) {
      if (idx !== entry.bestIndex) {
        rowsToDelete.push(idx); // superseded row
      }
    }
    // If the best row is a tombstone (empty URL), delete it too
    const bestRow = dataRows[entry.bestIndex - 2];
    if (bestRow && (!bestRow[COL_URL] || bestRow[COL_URL].toString().trim() === '')) {
      rowsToDelete.push(entry.bestIndex);
    }
  }

  if (rowsToDelete.length === 0) return { removed: 0 };

  // Sort descending so deletions don't shift indexes
  rowsToDelete.sort((a, b) => b - a);

  const metaUrl = `${SHEETS_BASE}/${spreadsheetId}?fields=sheets(properties(sheetId,title))`;
  const meta = await authedFetch(metaUrl);
  const sheet = meta.sheets.find(s => s.properties.title === SHEET_NAME);
  const sheetId = sheet ? sheet.properties.sheetId : 0;

  const requests = rowsToDelete.map(rowIdx => ({
    deleteDimension: {
      range: {
        sheetId,
        dimension: 'ROWS',
        startIndex: rowIdx - 1, // 0-based
        endIndex: rowIdx,
      },
    },
  }));

  const batchUrl = `${SHEETS_BASE}/${spreadsheetId}:batchUpdate`;
  await authedFetch(batchUrl, {
    method: 'POST',
    body: JSON.stringify({ requests }),
  });

  // Invalidate cache so next fetch gets clean data
  cache.bookmarks = null;
  cache.modifiedTime = null;
  await persistCache();

  return { removed: rowsToDelete.length };
}

// --- Page meta extraction ---

async function extractPageMeta(tabId) {
  try {
    const results = await chrome.scripting.executeScript({
      target: { tabId },
      func: () => {
        const getMeta = (selectors) => {
          for (const sel of selectors) {
            const el = document.querySelector(sel);
            if (el) {
              const val = el.getAttribute('content');
              if (val && val.trim()) return val.trim();
            }
          }
          return '';
        };
        return {
          excerpt: getMeta([
            'meta[property="og:description"]',
            'meta[name="description"]',
            'meta[name="twitter:description"]',
          ]),
          cover: getMeta([
            'meta[property="og:image"]',
            'meta[name="twitter:image"]',
          ]),
        };
      },
    });
    return results[0]?.result || { excerpt: '', cover: '' };
  } catch {
    return { excerpt: '', cover: '' };
  }
}

// --- Message handler ---

async function handleMessage(message) {
  switch (message.action) {
    case 'authenticate':
      return await getAuthToken(message.interactive ?? false);
    case 'listSpreadsheets':
      return await listSpreadsheets();
    case 'getCachedSheetData':
      return await getCachedSheetData(message.spreadsheetId, message.url);
    case 'getSheetData':
      return await getSheetData(message.spreadsheetId, message.url);
    case 'addBookmark':
      return await addBookmark(message.spreadsheetId, message.bookmark);
    case 'updateBookmark':
      return await updateBookmark(message.spreadsheetId, message.bookmark);
    case 'deleteBookmark':
      return await deleteBookmark(message.spreadsheetId, message.bookmarkId);
    case 'createSpreadsheet':
      return await createSpreadsheet(message.title);
    case 'cleanupSheet':
      return await cleanupSheet(message.spreadsheetId);
    case 'extractPageMeta':
      return await extractPageMeta(message.tabId);
    case 'signOut':
      return await signOut();
    default:
      throw new Error(`Unknown action: ${message.action}`);
  }
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message)
    .then(data => sendResponse({ success: true, data }))
    .catch(err => sendResponse({ success: false, error: err.message }));
  return true;
});
