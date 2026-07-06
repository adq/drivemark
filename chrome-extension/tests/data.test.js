import { describe, it, expect } from 'vitest';
import { parseRows, deduplicateBookmarks, deriveFolders, findExistingBookmark, tsMillis } from '../lib/data.js';

const HEADER = ['URL', 'Title', 'Folder', 'Date Added', 'Notes', 'Excerpt', 'Cover', 'ID', 'Modified'];

function makeBookmark(overrides = {}) {
  return {
    url: 'https://example.com',
    title: 'Example',
    folder: 'Dev',
    dateAdded: '2024-01-01T00:00:00Z',
    notes: '',
    excerpt: '',
    cover: '',
    id: 'uuid-1',
    modified: '2024-01-02T00:00:00Z',
    ...overrides,
  };
}

describe('parseRows', () => {
  it('returns empty array for header-only input', () => {
    expect(parseRows([HEADER])).toEqual([]);
  });

  it('returns empty array for completely empty input', () => {
    expect(parseRows([])).toEqual([]);
  });

  it('parses a full 9-column row', () => {
    const row = ['https://a.com', 'Title', 'Folder', '2024-01-01', 'notes', 'excerpt', 'cover.jpg', 'uuid-1', '2024-01-02'];
    const result = parseRows([HEADER, row]);
    expect(result).toEqual([{
      url: 'https://a.com',
      title: 'Title',
      folder: 'Folder',
      dateAdded: '2024-01-01',
      notes: 'notes',
      excerpt: 'excerpt',
      cover: 'cover.jpg',
      id: 'uuid-1',
      modified: '2024-01-02',
    }]);
  });

  it('defaults missing columns to empty string', () => {
    const row = ['https://a.com', 'Title'];
    const result = parseRows([HEADER, row]);
    expect(result[0].folder).toBe('');
    expect(result[0].notes).toBe('');
    expect(result[0].id).toBe('');
    expect(result[0].modified).toBe('');
  });

  it('parses multiple data rows', () => {
    const rows = [
      HEADER,
      ['https://a.com', 'A', '', '', '', '', '', 'id-1', ''],
      ['https://b.com', 'B', '', '', '', '', '', 'id-2', ''],
      ['https://c.com', 'C', '', '', '', '', '', 'id-3', ''],
    ];
    expect(parseRows(rows)).toHaveLength(3);
  });

  it('handles all-empty row (tombstone shape)', () => {
    const row = ['', '', '', '', '', '', '', '', ''];
    const result = parseRows([HEADER, row]);
    expect(result[0].url).toBe('');
    expect(result[0].id).toBe('');
  });

  it('defaults undefined cells to empty string', () => {
    const row = [undefined, undefined];
    const result = parseRows([HEADER, row]);
    expect(result[0].url).toBe('');
    expect(result[0].title).toBe('');
  });
});

describe('deduplicateBookmarks', () => {
  it('returns all rows when IDs are unique', () => {
    const rows = [
      makeBookmark({ id: 'a' }),
      makeBookmark({ id: 'b' }),
      makeBookmark({ id: 'c' }),
    ];
    expect(deduplicateBookmarks(rows)).toHaveLength(3);
  });

  it('keeps the row with the later modified timestamp', () => {
    const rows = [
      makeBookmark({ id: 'a', modified: '2024-01-01T00:00:00Z', title: 'Old' }),
      makeBookmark({ id: 'a', modified: '2024-06-01T00:00:00Z', title: 'New' }),
    ];
    const result = deduplicateBookmarks(rows);
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('New');
  });

  it('keeps the earlier row when its modified is later', () => {
    const rows = [
      makeBookmark({ id: 'a', modified: '2024-06-01T00:00:00Z', title: 'First' }),
      makeBookmark({ id: 'a', modified: '2024-01-01T00:00:00Z', title: 'Second' }),
    ];
    const result = deduplicateBookmarks(rows);
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('First');
  });

  it('falls back to dateAdded when modified is empty', () => {
    const rows = [
      makeBookmark({ id: 'a', modified: '', dateAdded: '2024-01-01T00:00:00Z', title: 'Old' }),
      makeBookmark({ id: 'a', modified: '', dateAdded: '2024-06-01T00:00:00Z', title: 'New' }),
    ];
    const result = deduplicateBookmarks(rows);
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('New');
  });

  it('filters out tombstones (empty URL)', () => {
    const rows = [
      makeBookmark({ id: 'a', url: '' }),
    ];
    expect(deduplicateBookmarks(rows)).toHaveLength(0);
  });

  it('treats whitespace-only URL as tombstone', () => {
    const rows = [
      makeBookmark({ id: 'a', url: '   ' }),
    ];
    expect(deduplicateBookmarks(rows)).toHaveLength(0);
  });

  it('skips rows without an ID', () => {
    const rows = [
      makeBookmark({ id: '', url: 'https://a.com' }),
    ];
    expect(deduplicateBookmarks(rows)).toHaveLength(0);
  });

  it('returns empty when all rows are tombstones', () => {
    const rows = [
      makeBookmark({ id: 'a', url: '', modified: '2024-01-01T00:00:00Z' }),
      makeBookmark({ id: 'a', url: '', modified: '2024-06-01T00:00:00Z' }),
    ];
    expect(deduplicateBookmarks(rows)).toHaveLength(0);
  });

  it('returns single valid row as-is', () => {
    const rows = [makeBookmark({ id: 'a', url: 'https://a.com' })];
    const result = deduplicateBookmarks(rows);
    expect(result).toHaveLength(1);
    expect(result[0].url).toBe('https://a.com');
  });

  it('later row wins on equal timestamps (>= comparison)', () => {
    const rows = [
      makeBookmark({ id: 'a', modified: '2024-01-01T00:00:00Z', title: 'First' }),
      makeBookmark({ id: 'a', modified: '2024-01-01T00:00:00Z', title: 'Second' }),
    ];
    const result = deduplicateBookmarks(rows);
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('Second');
  });

  it('orders mixed-precision timestamps numerically, not lexically', () => {
    // Android legacy zero-millis (Instant.toString drops the fraction) vs a Chrome 3-digit
    // write at the same second. The .500 write is genuinely later and must win. Under the
    // old lexical compare "...56.500Z" < "...56Z" ('.' < 'Z'), so the older row wrongly won.
    const rows = [
      makeBookmark({ id: 'a', modified: '2024-01-01T00:00:56Z', title: 'ZeroMillis' }),
      makeBookmark({ id: 'a', modified: '2024-01-01T00:00:56.500Z', title: 'HalfSecond' }),
    ];
    const result = deduplicateBookmarks(rows);
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('HalfSecond');
  });
});

describe('tsMillis', () => {
  it('parses an ISO timestamp to epoch millis', () => {
    expect(tsMillis('1970-01-01T00:00:01.000Z')).toBe(1000);
  });

  it('treats zero-millis and explicit 3-digit forms of the same instant as equal', () => {
    expect(tsMillis('2024-01-01T00:00:56Z')).toBe(tsMillis('2024-01-01T00:00:56.000Z'));
  });

  it('returns -Infinity for blank or unparseable input', () => {
    expect(tsMillis('')).toBe(-Infinity);
    expect(tsMillis(null)).toBe(-Infinity);
    expect(tsMillis('not-a-date')).toBe(-Infinity);
  });
});

describe('deriveFolders', () => {
  it('returns empty array for empty input', () => {
    expect(deriveFolders([])).toEqual([]);
  });

  it('returns unique folders sorted alphabetically', () => {
    const bookmarks = [
      makeBookmark({ folder: 'Zebra' }),
      makeBookmark({ folder: 'Alpha' }),
    ];
    expect(deriveFolders(bookmarks)).toEqual(['Alpha', 'Zebra']);
  });

  it('deduplicates folders', () => {
    const bookmarks = [
      makeBookmark({ folder: 'Dev' }),
      makeBookmark({ folder: 'Dev' }),
    ];
    expect(deriveFolders(bookmarks)).toEqual(['Dev']);
  });

  it('skips empty and whitespace-only folders', () => {
    const bookmarks = [
      makeBookmark({ folder: '' }),
      makeBookmark({ folder: '   ' }),
      makeBookmark({ folder: 'Keep' }),
    ];
    expect(deriveFolders(bookmarks)).toEqual(['Keep']);
  });

  it('skips null/undefined folders', () => {
    const bookmarks = [
      makeBookmark({ folder: null }),
      makeBookmark({ folder: undefined }),
    ];
    expect(deriveFolders(bookmarks)).toEqual([]);
  });

  it('trims whitespace from folder names', () => {
    const bookmarks = [makeBookmark({ folder: '  Dev  ' })];
    expect(deriveFolders(bookmarks)).toEqual(['Dev']);
  });
});

describe('findExistingBookmark', () => {
  const bookmarks = [
    makeBookmark({ id: '1', url: 'https://example.com', title: 'Example', folder: 'Dev', notes: 'note', dateAdded: '2024-01-01', excerpt: 'ex', cover: 'img.jpg', modified: '2024-02-01' }),
  ];

  it('returns null for null URL', () => {
    expect(findExistingBookmark(bookmarks, null)).toBeNull();
  });

  it('returns null for empty string URL', () => {
    expect(findExistingBookmark(bookmarks, '')).toBeNull();
  });

  it('finds an exact URL match and returns 7 fields', () => {
    const result = findExistingBookmark(bookmarks, 'https://example.com');
    expect(result).toEqual({
      id: '1',
      title: 'Example',
      folder: 'Dev',
      notes: 'note',
      dateAdded: '2024-01-01',
      excerpt: 'ex',
      cover: 'img.jpg',
    });
  });

  it('matches case-insensitively', () => {
    const result = findExistingBookmark(bookmarks, 'HTTPS://EXAMPLE.COM');
    expect(result).not.toBeNull();
    expect(result.id).toBe('1');
  });

  it('trims whitespace from search URL', () => {
    const result = findExistingBookmark(bookmarks, '  https://example.com  ');
    expect(result).not.toBeNull();
  });

  it('returns null when no match found', () => {
    expect(findExistingBookmark(bookmarks, 'https://no-match.com')).toBeNull();
  });

  it('returns the first match when duplicates exist', () => {
    const dupes = [
      makeBookmark({ id: 'first', url: 'https://dup.com' }),
      makeBookmark({ id: 'second', url: 'https://dup.com' }),
    ];
    const result = findExistingBookmark(dupes, 'https://dup.com');
    expect(result.id).toBe('first');
  });

  it('does not crash on bookmark with null URL', () => {
    const withNull = [makeBookmark({ url: null, id: 'x' })];
    expect(findExistingBookmark(withNull, 'https://test.com')).toBeNull();
  });
});
