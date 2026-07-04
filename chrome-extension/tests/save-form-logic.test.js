import { describe, it, expect, beforeEach, vi } from 'vitest';
import { state, setState, setRenderCallback } from '../lib/state.js';

// Mock sendMessage and showStatus before importing save-form
vi.mock('../lib/helpers.js', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    sendMessage: vi.fn(),
    showStatus: vi.fn(),
  };
});

// Must import after vi.mock
const { sendMessage, showStatus } = await import('../lib/helpers.js');
const { prefillForm, resetForm, saveBookmark } = await import('../save-form.js');

const originalState = { ...state };

beforeEach(() => {
  Object.assign(state, originalState);
  setRenderCallback(null);
  sendMessage.mockReset();
  showStatus.mockReset();
});

describe('resetForm', () => {
  it('clears all form fields', () => {
    setState({
      formUrl: 'https://old.com',
      formTitle: 'Old Title',
      formExcerpt: 'old excerpt',
      formCover: 'old cover',
      formFolder: 'Old Folder',
      formNotes: 'old notes',
    });

    resetForm({ url: 'https://tab.com' });

    expect(state.formUrl).toBe('https://tab.com');
    expect(state.formTitle).toBe('');
    expect(state.formExcerpt).toBe('');
    expect(state.formCover).toBe('');
    expect(state.formFolder).toBe('');
    expect(state.formNotes).toBe('');
  });

  it('sets formUrl from tab.url', () => {
    resetForm({ url: 'https://example.com/page' });
    expect(state.formUrl).toBe('https://example.com/page');
  });

  it('defaults formUrl to empty string when tab.url is falsy', () => {
    resetForm({});
    expect(state.formUrl).toBe('');
  });

  it('resets formUrlEdited to false', () => {
    setState({ formUrlEdited: true });
    resetForm({ url: 'https://x.com' });
    expect(state.formUrlEdited).toBe(false);
  });

  it('clears searchQuery', () => {
    setState({ searchQuery: 'old search' });
    resetForm({ url: '' });
    expect(state.searchQuery).toBe('');
  });

  it('clears statusText and statusType', () => {
    setState({ statusText: 'Saved!', statusType: 'success' });
    resetForm({ url: '' });
    expect(state.statusText).toBeNull();
    expect(state.statusType).toBeNull();
  });
});

describe('prefillForm', () => {
  it('populates form from existing bookmark', () => {
    const existing = {
      url: 'https://saved.com',
      title: 'Saved Page',
      excerpt: 'An excerpt',
      cover: 'https://img.com/cover.jpg',
      folder: 'Reading',
      notes: 'Good article',
    };
    const tab = { url: 'https://tab.com', title: 'Tab Title', id: 1 };

    prefillForm(existing, tab);

    expect(state.formUrl).toBe('https://saved.com');
    expect(state.formTitle).toBe('Saved Page');
    expect(state.formExcerpt).toBe('An excerpt');
    expect(state.formCover).toBe('https://img.com/cover.jpg');
    expect(state.formFolder).toBe('Reading');
    expect(state.formNotes).toBe('Good article');
  });

  it('falls back to tab URL when bookmark url is empty', () => {
    const existing = { url: '', title: 'Title', excerpt: '', cover: '', folder: 'F', notes: '' };
    const tab = { url: 'https://tab.com', title: 'Tab', id: 1 };

    prefillForm(existing, tab);
    expect(state.formUrl).toBe('https://tab.com');
  });

  it('falls back to tab title when bookmark title is empty', () => {
    const existing = { url: 'https://x.com', title: '', excerpt: '', cover: '', folder: 'F', notes: '' };
    const tab = { url: 'https://tab.com', title: 'Tab Title', id: 1 };

    prefillForm(existing, tab);
    expect(state.formTitle).toBe('Tab Title');
  });

  it('defaults excerpt and cover to empty string when null', () => {
    const existing = { url: 'https://x.com', title: 'T', excerpt: null, cover: null, folder: 'F', notes: 'n' };
    const tab = { url: '', title: '', id: 1 };

    prefillForm(existing, tab);
    expect(state.formExcerpt).toBe('');
    expect(state.formCover).toBe('');
  });

  it('sets formTitle from tab when no existing bookmark', () => {
    const tab = { url: 'https://new.com', title: 'New Page', id: 1 };
    sendMessage.mockResolvedValue({ excerpt: '', cover: '' });

    prefillForm(null, tab);
    expect(state.formTitle).toBe('New Page');
  });

  it('calls extractPageMeta for new bookmarks', () => {
    const tab = { url: 'https://new.com', title: 'New', id: 42 };
    sendMessage.mockResolvedValue({ excerpt: '', cover: '' });

    prefillForm(null, tab);
    expect(sendMessage).toHaveBeenCalledWith({ action: 'extractPageMeta', tabId: 42 });
  });

  it('populates excerpt/cover from extractPageMeta response', async () => {
    const tab = { url: 'https://new.com', title: 'New', id: 1 };
    sendMessage.mockResolvedValue({ excerpt: 'Meta excerpt', cover: 'https://img.com/og.jpg' });

    prefillForm(null, tab);

    // Let the promise resolve
    await vi.waitFor(() => {
      expect(state.formExcerpt).toBe('Meta excerpt');
      expect(state.formCover).toBe('https://img.com/og.jpg');
    });
  });

  it('does not overwrite user-entered excerpt/cover from meta', async () => {
    const tab = { url: 'https://new.com', title: 'New', id: 1 };
    sendMessage.mockResolvedValue({ excerpt: 'From meta', cover: 'meta.jpg' });

    // User already typed something
    setState({ formExcerpt: 'User typed', formCover: 'user.jpg' });
    prefillForm(null, tab);

    // Wait for the sendMessage promise to settle
    await new Promise(r => setTimeout(r, 0));

    expect(state.formExcerpt).toBe('User typed');
    expect(state.formCover).toBe('user.jpg');
  });

  it('defaults formTitle to empty string when tab.title is falsy', () => {
    const tab = { url: 'https://new.com', title: '', id: 1 };
    sendMessage.mockResolvedValue({ excerpt: '', cover: '' });

    prefillForm(null, tab);
    expect(state.formTitle).toBe('');
  });
});

describe('saveBookmark', () => {
  beforeEach(() => {
    // Set up minimal required state
    setState({
      spreadsheetId: 'sheet-123',
      tabUrl: 'https://example.com',
      tabTitle: 'Example Page',
      formUrl: 'https://example.com',
      formTitle: 'Example Page',
      formFolder: 'Dev',
      formNotes: '',
      formExcerpt: '',
      formCover: '',
      existingBookmark: null,
    });
  });

  it('shows error when folder is empty', async () => {
    setState({ formFolder: '' });
    await saveBookmark();
    expect(showStatus).toHaveBeenCalledWith('Please enter a folder.', 'error');
    expect(sendMessage).not.toHaveBeenCalled();
  });

  it('shows error when folder is whitespace only', async () => {
    setState({ formFolder: '   ' });
    await saveBookmark();
    expect(showStatus).toHaveBeenCalledWith('Please enter a folder.', 'error');
  });

  it('sets saving to true during save', async () => {
    let savingDuringSend = false;
    sendMessage.mockImplementationOnce(() => {
      savingDuringSend = state.saving;
      return Promise.resolve({});
    });
    // Second call is the refresh — just resolve
    sendMessage.mockImplementationOnce(() => Promise.resolve({ folders: [], bookmarks: [], existingBookmark: null }));

    await saveBookmark();
    expect(savingDuringSend).toBe(true);
  });

  it('sends addBookmark for new bookmarks', async () => {
    sendMessage.mockResolvedValue({});

    await saveBookmark();

    expect(sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      action: 'addBookmark',
      spreadsheetId: 'sheet-123',
      bookmark: expect.objectContaining({
        url: 'https://example.com',
        title: 'Example Page',
        folder: 'Dev',
      }),
    }));
  });

  it('sends updateBookmark for existing bookmarks', async () => {
    setState({
      existingBookmark: {
        id: 'uuid-1',
        dateAdded: '2024-01-01T00:00:00Z',
      },
    });
    sendMessage.mockResolvedValue({});

    await saveBookmark();

    expect(sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      action: 'updateBookmark',
      bookmark: expect.objectContaining({
        id: 'uuid-1',
        dateAdded: '2024-01-01T00:00:00Z',
      }),
    }));
  });

  it('shows success status after adding', async () => {
    sendMessage.mockResolvedValue({});
    await saveBookmark();
    expect(showStatus).toHaveBeenCalledWith('Bookmark saved!', 'success');
  });

  it('shows success status after updating', async () => {
    setState({ existingBookmark: { id: 'x', dateAdded: '2024-01-01' } });
    sendMessage.mockResolvedValue({});
    await saveBookmark();
    expect(showStatus).toHaveBeenCalledWith('Bookmark updated!', 'success');
  });

  it('sets saving to false after successful save', async () => {
    sendMessage.mockResolvedValue({});
    await saveBookmark();
    expect(state.saving).toBe(false);
  });

  it('shows error status on failure', async () => {
    sendMessage.mockRejectedValue(new Error('Network error'));
    await saveBookmark();
    expect(showStatus).toHaveBeenCalledWith('Save failed: Network error', 'error');
  });

  it('sets saving to false on failure', async () => {
    sendMessage.mockRejectedValue(new Error('fail'));
    await saveBookmark();
    expect(state.saving).toBe(false);
  });

  it('trims whitespace from all fields', async () => {
    setState({
      formUrl: '  https://example.com  ',
      formTitle: '  My Title  ',
      formFolder: '  Dev  ',
      formNotes: '  some notes  ',
      formExcerpt: '  excerpt  ',
      formCover: '  cover.jpg  ',
    });
    sendMessage.mockResolvedValue({});

    await saveBookmark();

    const bookmark = sendMessage.mock.calls[0][0].bookmark;
    expect(bookmark.url).toBe('https://example.com');
    expect(bookmark.title).toBe('My Title');
    expect(bookmark.folder).toBe('Dev');
    expect(bookmark.notes).toBe('some notes');
    expect(bookmark.excerpt).toBe('excerpt');
    expect(bookmark.cover).toBe('cover.jpg');
  });

  it('falls back to tabTitle when form title is empty', async () => {
    setState({ formTitle: '', tabTitle: 'Tab Fallback' });
    sendMessage.mockResolvedValue({});

    await saveBookmark();

    const bookmark = sendMessage.mock.calls[0][0].bookmark;
    expect(bookmark.title).toBe('Tab Fallback');
  });

  it('does not send when folder validation fails', async () => {
    setState({ formFolder: '' });
    await saveBookmark();
    expect(sendMessage).not.toHaveBeenCalled();
    expect(state.saving).not.toBe(true);
  });
});
