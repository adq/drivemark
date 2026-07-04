// === Helpers ===

import { setState } from './state.js';

export function sendMessage(message) {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage(message, response => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message));
        return;
      }
      if (!response || !response.success) {
        reject(new Error(response?.error || 'Unknown error'));
        return;
      }
      resolve(response.data);
    });
  });
}

export function showStatus(text, type) {
  setState({ statusText: text, statusType: type });
  if (type === 'success') {
    setTimeout(() => setState({ statusText: null, statusType: null }), 3000);
  }
}

export function showError(text) {
  setState({ globalError: text });
  setTimeout(() => setState({ globalError: null }), 5000);
}

export function formatDate(iso) {
  if (!iso) return '';
  try { return new Date(iso).toLocaleDateString(); }
  catch { return iso; }
}
