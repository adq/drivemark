/**
 * Build a nested folder tree from a flat { folderPath: bookmarks[] } map.
 * Splits paths on "/" to support nested folders (e.g. "Work/Projects").
 */
export function buildFolderTree(groups) {
  const root = { children: {}, bookmarks: [] };
  for (const [path, bookmarks] of Object.entries(groups)) {
    const parts = path.split('/');
    let node = root;
    for (const part of parts) {
      if (!node.children[part]) {
        node.children[part] = { children: {}, bookmarks: [] };
      }
      node = node.children[part];
    }
    node.bookmarks = bookmarks;
  }
  return root;
}

/** Count all bookmarks in a tree node and its descendants. */
export function countBookmarks(node) {
  let total = node.bookmarks.length;
  for (const child of Object.values(node.children)) {
    total += countBookmarks(child);
  }
  return total;
}

/** Group bookmarks by folder, defaulting to "Unfiled". */
export function groupByFolder(bookmarks) {
  const groups = {};
  for (const b of bookmarks) {
    const folder = b.folder || 'Unfiled';
    if (!groups[folder]) groups[folder] = [];
    groups[folder].push(b);
  }
  return groups;
}

/** Filter bookmarks by a search query across multiple fields. */
export function filterBookmarks(bookmarks, query) {
  if (!query) return bookmarks;
  const q = query.toLowerCase();
  return bookmarks.filter(b =>
    b.title.toLowerCase().includes(q) ||
    b.url.toLowerCase().includes(q) ||
    b.folder.toLowerCase().includes(q) ||
    b.notes.toLowerCase().includes(q) ||
    (b.excerpt && b.excerpt.toLowerCase().includes(q))
  );
}
