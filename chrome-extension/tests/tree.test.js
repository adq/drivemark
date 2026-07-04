import { describe, it, expect } from 'vitest';
import { buildFolderTree, countBookmarks, groupByFolder, filterBookmarks } from '../lib/tree.js';

function makeBookmark(overrides = {}) {
  return {
    url: 'https://example.com',
    title: 'Example',
    folder: '',
    notes: '',
    excerpt: '',
    ...overrides,
  };
}

describe('groupByFolder', () => {
  it('returns empty object for empty array', () => {
    expect(groupByFolder([])).toEqual({});
  });

  it('groups bookmarks by their folder', () => {
    const bookmarks = [
      makeBookmark({ folder: 'A', title: 'a1' }),
      makeBookmark({ folder: 'B', title: 'b1' }),
      makeBookmark({ folder: 'A', title: 'a2' }),
    ];
    const groups = groupByFolder(bookmarks);
    expect(Object.keys(groups)).toHaveLength(2);
    expect(groups['A']).toHaveLength(2);
    expect(groups['B']).toHaveLength(1);
  });

  it('defaults falsy folder to "Unfiled"', () => {
    const bookmarks = [
      makeBookmark({ folder: '' }),
      makeBookmark({ folder: null }),
      makeBookmark({ folder: undefined }),
    ];
    const groups = groupByFolder(bookmarks);
    expect(groups['Unfiled']).toHaveLength(3);
  });

  it('handles mix of folders and unfiled', () => {
    const bookmarks = [
      makeBookmark({ folder: 'Work' }),
      makeBookmark({ folder: '' }),
    ];
    const groups = groupByFolder(bookmarks);
    expect(groups['Work']).toHaveLength(1);
    expect(groups['Unfiled']).toHaveLength(1);
  });

  it('groups multiple bookmarks into the same folder', () => {
    const bookmarks = [
      makeBookmark({ folder: 'X', title: '1' }),
      makeBookmark({ folder: 'X', title: '2' }),
      makeBookmark({ folder: 'X', title: '3' }),
    ];
    const groups = groupByFolder(bookmarks);
    expect(groups['X']).toHaveLength(3);
  });
});

describe('buildFolderTree', () => {
  it('returns empty root for empty groups', () => {
    const tree = buildFolderTree({});
    expect(tree.children).toEqual({});
    expect(tree.bookmarks).toEqual([]);
  });

  it('builds a single flat folder', () => {
    const bm = makeBookmark();
    const tree = buildFolderTree({ 'Notes': [bm] });
    expect(tree.children['Notes'].bookmarks).toEqual([bm]);
  });

  it('builds nested folders from slash-separated paths', () => {
    const bm = makeBookmark();
    const tree = buildFolderTree({ 'Work/Projects': [bm] });
    expect(tree.children['Work']).toBeDefined();
    expect(tree.children['Work'].bookmarks).toEqual([]);
    expect(tree.children['Work'].children['Projects'].bookmarks).toEqual([bm]);
  });

  it('builds deeply nested paths', () => {
    const bm = makeBookmark();
    const tree = buildFolderTree({ 'A/B/C': [bm] });
    expect(tree.children['A'].children['B'].children['C'].bookmarks).toEqual([bm]);
    expect(tree.children['A'].bookmarks).toEqual([]);
    expect(tree.children['A'].children['B'].bookmarks).toEqual([]);
  });

  it('shares parent nodes for sibling folders', () => {
    const bm1 = makeBookmark({ title: 'alpha' });
    const bm2 = makeBookmark({ title: 'beta' });
    const tree = buildFolderTree({ 'Work/Alpha': [bm1], 'Work/Beta': [bm2] });
    expect(tree.children['Work'].children['Alpha'].bookmarks).toEqual([bm1]);
    expect(tree.children['Work'].children['Beta'].bookmarks).toEqual([bm2]);
  });

  it('handles root-only single-segment path', () => {
    const bm = makeBookmark();
    const tree = buildFolderTree({ 'Unfiled': [bm] });
    expect(tree.children['Unfiled'].bookmarks).toEqual([bm]);
  });
});

describe('countBookmarks', () => {
  it('counts bookmarks in a leaf node', () => {
    const node = { children: {}, bookmarks: [makeBookmark(), makeBookmark(), makeBookmark()] };
    expect(countBookmarks(node)).toBe(3);
  });

  it('returns 0 for empty node', () => {
    const node = { children: {}, bookmarks: [] };
    expect(countBookmarks(node)).toBe(0);
  });

  it('counts across nested children', () => {
    const node = {
      bookmarks: [makeBookmark()],
      children: {
        A: {
          bookmarks: [makeBookmark(), makeBookmark()],
          children: {
            B: { bookmarks: [makeBookmark()], children: {} },
          },
        },
      },
    };
    expect(countBookmarks(node)).toBe(4);
  });

  it('counts deep nesting with bookmarks at each level', () => {
    const node = {
      bookmarks: [makeBookmark()],
      children: {
        L1: {
          bookmarks: [makeBookmark()],
          children: {
            L2: {
              bookmarks: [makeBookmark()],
              children: {
                L3: { bookmarks: [makeBookmark()], children: {} },
              },
            },
          },
        },
      },
    };
    expect(countBookmarks(node)).toBe(4);
  });
});

describe('filterBookmarks', () => {
  const bookmarks = [
    makeBookmark({ title: 'React Docs', url: 'https://react.dev', folder: 'Dev', notes: 'important', excerpt: 'A guide to React' }),
    makeBookmark({ title: 'GitHub', url: 'https://github.com', folder: 'Work', notes: '', excerpt: 'Code hosting' }),
    makeBookmark({ title: 'News', url: 'https://news.com', folder: 'Media', notes: 'daily check', excerpt: undefined }),
  ];

  it('returns all bookmarks for empty query', () => {
    expect(filterBookmarks(bookmarks, '')).toEqual(bookmarks);
  });

  it('returns all bookmarks for null query', () => {
    expect(filterBookmarks(bookmarks, null)).toEqual(bookmarks);
  });

  it('matches title case-insensitively', () => {
    const result = filterBookmarks(bookmarks, 'react');
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('React Docs');
  });

  it('matches url', () => {
    const result = filterBookmarks(bookmarks, 'github');
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('GitHub');
  });

  it('matches folder', () => {
    const result = filterBookmarks(bookmarks, 'work');
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('GitHub');
  });

  it('matches notes', () => {
    const result = filterBookmarks(bookmarks, 'important');
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('React Docs');
  });

  it('matches excerpt', () => {
    const result = filterBookmarks(bookmarks, 'guide');
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('React Docs');
  });

  it('returns empty array when nothing matches', () => {
    expect(filterBookmarks(bookmarks, 'zzzznotfound')).toEqual([]);
  });

  it('handles undefined excerpt without crashing', () => {
    // The "News" bookmark has excerpt: undefined
    const result = filterBookmarks(bookmarks, 'daily');
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('News');
  });

  it('does not match undefined excerpt field', () => {
    // Query that would only match an excerpt — the News bookmark has undefined excerpt
    const result = filterBookmarks(bookmarks, 'hosting');
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('GitHub');
  });
});
