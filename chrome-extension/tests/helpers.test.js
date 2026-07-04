import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { state, setState, setRenderCallback } from '../lib/state.js';

// We need to set up the chrome global BEFORE importing helpers,
// because sendMessage references chrome.runtime at call time.
globalThis.chrome = {
  runtime: {
    sendMessage: vi.fn(),
    lastError: null,
  },
};

const { formatDate, sendMessage, showStatus, showError } = await import('../lib/helpers.js');

// Snapshot original state for reset
const originalState = { ...state };

beforeEach(() => {
  Object.assign(state, originalState);
  setRenderCallback(null);
  vi.useFakeTimers();
  chrome.runtime.sendMessage.mockReset();
  chrome.runtime.lastError = null;
});

afterEach(() => {
  vi.useRealTimers();
});

describe('formatDate', () => {
  it('formats a valid ISO date string', () => {
    const result = formatDate('2024-01-15T10:30:00Z');
    expect(result).toBeTruthy();
    expect(typeof result).toBe('string');
    // Locale-dependent, but should contain the year
    expect(result).toContain('2024');
  });

  it('returns empty string for empty input', () => {
    expect(formatDate('')).toBe('');
  });

  it('returns empty string for null', () => {
    expect(formatDate(null)).toBe('');
  });

  it('returns empty string for undefined', () => {
    expect(formatDate(undefined)).toBe('');
  });

  it('returns "Invalid Date" for unparseable input', () => {
    // new Date('not-a-date') doesn't throw — it returns Invalid Date
    // toLocaleDateString() on Invalid Date returns 'Invalid Date'
    const result = formatDate('not-a-date');
    expect(result).toBe('Invalid Date');
  });
});

describe('sendMessage', () => {
  it('resolves with response.data on success', async () => {
    chrome.runtime.sendMessage.mockImplementation((msg, cb) => {
      cb({ success: true, data: { id: '123' } });
    });
    const result = await sendMessage({ action: 'test' });
    expect(result).toEqual({ id: '123' });
  });

  it('rejects with lastError.message when chrome.runtime.lastError is set', async () => {
    chrome.runtime.sendMessage.mockImplementation((msg, cb) => {
      chrome.runtime.lastError = { message: 'Extension context invalidated' };
      cb(undefined);
      chrome.runtime.lastError = null;
    });
    await expect(sendMessage({ action: 'test' }))
      .rejects.toThrow('Extension context invalidated');
  });

  it('rejects with response.error when success is false', async () => {
    chrome.runtime.sendMessage.mockImplementation((msg, cb) => {
      cb({ success: false, error: 'Sheet not found' });
    });
    await expect(sendMessage({ action: 'test' }))
      .rejects.toThrow('Sheet not found');
  });

  it('rejects with "Unknown error" when response has no error message', async () => {
    chrome.runtime.sendMessage.mockImplementation((msg, cb) => {
      cb({ success: false });
    });
    await expect(sendMessage({ action: 'test' }))
      .rejects.toThrow('Unknown error');
  });

  it('rejects when response is null', async () => {
    chrome.runtime.sendMessage.mockImplementation((msg, cb) => {
      cb(null);
    });
    await expect(sendMessage({ action: 'test' }))
      .rejects.toThrow('Unknown error');
  });

  it('rejects when response is undefined', async () => {
    chrome.runtime.sendMessage.mockImplementation((msg, cb) => {
      cb(undefined);
    });
    await expect(sendMessage({ action: 'test' }))
      .rejects.toThrow('Unknown error');
  });

  it('passes the message object to chrome.runtime.sendMessage', async () => {
    chrome.runtime.sendMessage.mockImplementation((msg, cb) => {
      cb({ success: true, data: null });
    });
    await sendMessage({ action: 'addBookmark', spreadsheetId: 'abc' });
    expect(chrome.runtime.sendMessage).toHaveBeenCalledWith(
      { action: 'addBookmark', spreadsheetId: 'abc' },
      expect.any(Function),
    );
  });
});

describe('showStatus', () => {
  it('sets statusText and statusType on state', () => {
    showStatus('Saved!', 'success');
    expect(state.statusText).toBe('Saved!');
    expect(state.statusType).toBe('success');
  });

  it('clears status after 3s for success type', () => {
    showStatus('Done', 'success');
    expect(state.statusText).toBe('Done');
    vi.advanceTimersByTime(3000);
    expect(state.statusText).toBeNull();
    expect(state.statusType).toBeNull();
  });

  it('does not auto-clear for error type', () => {
    showStatus('Failed', 'error');
    expect(state.statusText).toBe('Failed');
    vi.advanceTimersByTime(10000);
    expect(state.statusText).toBe('Failed');
    expect(state.statusType).toBe('error');
  });
});

describe('showError', () => {
  it('sets globalError on state', () => {
    showError('Something broke');
    expect(state.globalError).toBe('Something broke');
  });

  it('clears globalError after 5s', () => {
    showError('Oops');
    expect(state.globalError).toBe('Oops');
    vi.advanceTimersByTime(5000);
    expect(state.globalError).toBeNull();
  });
});
