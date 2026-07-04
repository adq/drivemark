import {
  COL_URL, COL_TITLE, COL_FOLDER, COL_DATE_ADDED,
  COL_NOTES, COL_EXCERPT, COL_COVER, COL_ID, COL_MODIFIED,
} from './columns.js';

/** Parse raw sheet rows (array of arrays) into bookmark objects. Skips header row. */
export function parseRows(rows) {
  return rows.slice(1).map((row) => ({
    url: row[COL_URL] || '',
    title: row[COL_TITLE] || '',
    folder: row[COL_FOLDER] || '',
    dateAdded: row[COL_DATE_ADDED] || '',
    notes: row[COL_NOTES] || '',
    excerpt: row[COL_EXCERPT] || '',
    cover: row[COL_COVER] || '',
    id: row[COL_ID] || '',
    modified: row[COL_MODIFIED] || '',
  }));
}

/**
 * Deduplicate bookmarks by ID, keeping the row with the latest timestamp.
 * Filters out tombstones (empty URL = deleted).
 */
export function deduplicateBookmarks(parsedRows) {
  const byId = new Map();
  for (const row of parsedRows) {
    if (!row.id) continue;
    const existing = byId.get(row.id);
    if (!existing) {
      byId.set(row.id, row);
    } else {
      const existingTs = existing.modified || existing.dateAdded || '';
      const currentTs = row.modified || row.dateAdded || '';
      if (currentTs >= existingTs) {
        byId.set(row.id, row);
      }
    }
  }
  // Filter out tombstones (empty URL = deleted)
  return [...byId.values()].filter(b => b.url.trim() !== '');
}

/** Extract unique, sorted folder names from bookmarks. */
export function deriveFolders(bookmarks) {
  const cats = new Set();
  for (const b of bookmarks) {
    if (b.folder && b.folder.trim()) cats.add(b.folder.trim());
  }
  return [...cats].sort();
}

/** Find an existing bookmark by normalized URL match. */
export function findExistingBookmark(bookmarks, url) {
  if (!url) return null;
  const normalized = url.trim().toLowerCase();
  for (const b of bookmarks) {
    if (b.url && b.url.trim().toLowerCase() === normalized) {
      return {
        id: b.id,
        title: b.title,
        folder: b.folder,
        notes: b.notes,
        dateAdded: b.dateAdded,
        excerpt: b.excerpt,
        cover: b.cover,
      };
    }
  }
  return null;
}
