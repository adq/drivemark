// Sheet schema constants — single source of truth for column layout.
// Columns A–I: URL | Title | Folder | Date Added | Notes | Excerpt | Cover | ID | Modified

export const SHEET_NAME = 'Sheet1';

// Ordered field definitions: stable field key ↔ canonical header text.
// The key is used throughout the code; the header is what we match against the
// actual sheet header row (case-insensitively) to locate each column.
export const FIELDS = [
  { key: 'url', header: 'URL' },
  { key: 'title', header: 'Title' },
  { key: 'folder', header: 'Folder' },
  { key: 'dateAdded', header: 'Date Added' },
  { key: 'notes', header: 'Notes' },
  { key: 'excerpt', header: 'Excerpt' },
  { key: 'cover', header: 'Cover' },
  { key: 'id', header: 'ID' },
  { key: 'modified', header: 'Modified' },
];

// Header row written when initialising a new sheet
export const HEADERS = FIELDS.map(f => f.header);

// Columns the append-only/tombstone sync model depends on. If any is absent from
// a non-empty sheet's header row we refuse to read/write rather than corrupt data.
export const ESSENTIAL_FIELDS = ['url', 'dateAdded', 'id', 'modified'];

// Range strings for the Sheets API. A:I spans the canonical layout; widened columns
// beyond I would need this bumped, but the resolver handles any order within A:I.
export const RANGE_ALL = `${SHEET_NAME}!A:I`;
export const RANGE_HEADER = `${SHEET_NAME}!A1:I1`;

function normalize(text) {
  return (text == null ? '' : String(text)).trim().toLowerCase();
}

/**
 * Resolve each field to its column index by case-insensitive, trimmed match of
 * the header cell text against the canonical header name. Returns an object
 * `{ url, title, ... }` where each value is the 0-based column index, or -1 if
 * that field's header is not present.
 */
export function resolveColumns(headerRow) {
  const normalized = (headerRow || []).map(normalize);
  const colmap = {};
  for (const { key, header } of FIELDS) {
    colmap[key] = normalized.indexOf(normalize(header));
  }
  return colmap;
}

/** Fields whose header is missing from a resolved column map. */
export function missingEssentialFields(colmap) {
  return ESSENTIAL_FIELDS.filter(key => !(colmap[key] >= 0));
}

/**
 * Throw if any essential column is absent. Callers guard read/write paths with
 * this so a malformed sheet surfaces an error instead of silently corrupting rows.
 */
export function requireEssentialColumns(colmap) {
  const missing = missingEssentialFields(colmap);
  if (missing.length > 0) {
    const headers = missing.map(key => FIELDS.find(f => f.key === key).header);
    throw new Error(`Sheet is missing required column(s): ${headers.join(', ')}`);
  }
}

/** Convert a 0-based column index to its A1 column letter (0 → A, 25 → Z, 26 → AA). */
export function columnLetter(index) {
  let n = index;
  let letter = '';
  do {
    letter = String.fromCharCode(65 + (n % 26)) + letter;
    n = Math.floor(n / 26) - 1;
  } while (n >= 0);
  return letter;
}

/**
 * Build an append row array from a bookmark object, placing each field at its
 * resolved column index. The array is sized to the header width so mapped
 * columns land correctly; unmapped positions are filled with ''. Fields whose
 * header is absent (index -1) are simply omitted.
 */
export function buildRow(bookmark, colmap, width) {
  const indices = FIELDS.map(f => colmap[f.key]).filter(i => i >= 0);
  const size = Math.max(width || 0, indices.length ? Math.max(...indices) + 1 : 0);
  const row = new Array(size).fill('');
  for (const { key } of FIELDS) {
    const idx = colmap[key];
    if (idx >= 0) row[idx] = bookmark[key] != null ? bookmark[key] : '';
  }
  return row;
}
