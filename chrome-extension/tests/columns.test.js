import { describe, it, expect } from 'vitest';
import {
  HEADERS, resolveColumns, requireEssentialColumns, missingEssentialFields,
  columnLetter, buildRow,
} from '../lib/columns.js';

describe('resolveColumns', () => {
  it('resolves the canonical header row to A–I order', () => {
    expect(resolveColumns(HEADERS)).toEqual({
      url: 0, title: 1, folder: 2, dateAdded: 3, notes: 4,
      excerpt: 5, cover: 6, id: 7, modified: 8,
    });
  });

  it('resolves reordered columns by header name', () => {
    const header = ['ID', 'URL', 'Modified', 'Date Added', 'Title', 'Folder', 'Notes', 'Excerpt', 'Cover'];
    const map = resolveColumns(header);
    expect(map.id).toBe(0);
    expect(map.url).toBe(1);
    expect(map.modified).toBe(2);
    expect(map.dateAdded).toBe(3);
    expect(map.title).toBe(4);
  });

  it('matches case- and whitespace-insensitively', () => {
    const header = ['url', ' Title ', 'FOLDER', 'date added', 'NOTES', 'excerpt', 'Cover', 'id', 'MODIFIED'];
    const map = resolveColumns(header);
    expect(map.url).toBe(0);
    expect(map.title).toBe(1);
    expect(map.folder).toBe(2);
    expect(map.dateAdded).toBe(3);
    expect(map.id).toBe(7);
    expect(map.modified).toBe(8);
  });

  it('marks absent headers as -1', () => {
    const header = ['URL', 'Title', 'ID', 'Date Added', 'Modified'];
    const map = resolveColumns(header);
    expect(map.folder).toBe(-1);
    expect(map.notes).toBe(-1);
    expect(map.url).toBe(0);
    expect(map.id).toBe(2);
  });

  it('handles empty header row', () => {
    const map = resolveColumns([]);
    for (const v of Object.values(map)) expect(v).toBe(-1);
  });
});

describe('requireEssentialColumns', () => {
  it('passes when url/id/dateAdded/modified are present', () => {
    expect(() => requireEssentialColumns(resolveColumns(HEADERS))).not.toThrow();
  });

  it('passes when only display columns are absent', () => {
    const header = ['URL', 'ID', 'Date Added', 'Modified'];
    expect(() => requireEssentialColumns(resolveColumns(header))).not.toThrow();
  });

  it('throws when an essential column is absent', () => {
    const header = ['URL', 'Title', 'Date Added', 'Modified']; // no ID
    expect(() => requireEssentialColumns(resolveColumns(header))).toThrow(/ID/);
  });

  it('missingEssentialFields lists the absent essentials', () => {
    const header = ['Title', 'Folder']; // no url/id/dateAdded/modified
    expect(missingEssentialFields(resolveColumns(header)).sort())
      .toEqual(['dateAdded', 'id', 'modified', 'url'].sort());
  });
});

describe('columnLetter', () => {
  it('maps 0-based indexes to A1 letters', () => {
    expect(columnLetter(0)).toBe('A');
    expect(columnLetter(7)).toBe('H');
    expect(columnLetter(25)).toBe('Z');
    expect(columnLetter(26)).toBe('AA');
    expect(columnLetter(27)).toBe('AB');
    expect(columnLetter(51)).toBe('AZ');
  });
});

describe('buildRow', () => {
  const record = {
    url: 'https://a.com', title: 'T', folder: 'F', dateAdded: 'd', notes: 'n',
    excerpt: 'e', cover: 'c', id: 'uuid', modified: 'm',
  };

  it('builds a canonical A–I row', () => {
    const map = resolveColumns(HEADERS);
    expect(buildRow(record, map, 9)).toEqual(['https://a.com', 'T', 'F', 'd', 'n', 'e', 'c', 'uuid', 'm']);
  });

  it('places fields at their resolved indexes for a reordered sheet', () => {
    const header = ['ID', 'URL', 'Modified'];
    const map = resolveColumns(header);
    // width 3; only id/url/modified present, other fields have no slot
    expect(buildRow(record, map, 3)).toEqual(['uuid', 'https://a.com', 'm']);
  });

  it('fills unmapped gap positions with empty strings', () => {
    // header where index 1 (Title) exists but the row is sized wider by width
    const header = ['URL', 'ID', 'Modified', 'Date Added'];
    const map = resolveColumns(header);
    const row = buildRow(record, map, 4);
    expect(row).toEqual(['https://a.com', 'uuid', 'm', 'd']);
  });

  it('omits absent fields (no slot) rather than shifting others', () => {
    const header = ['URL', 'Date Added', 'ID', 'Modified'];
    const map = resolveColumns(header);
    const row = buildRow(record, map, 4);
    expect(row).toEqual(['https://a.com', 'd', 'uuid', 'm']);
    expect(row).not.toContain('T'); // title absent from header → dropped
  });
});
