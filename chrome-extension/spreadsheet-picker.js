// === Spreadsheet Picker ===

import { html } from './lib/html.js';
import { state, setState } from './lib/state.js';
import { sendMessage } from './lib/helpers.js';

// --- Component ---

export function PickerScreen({ pickerLoading, pickerError, pickerFiles, creatingSheet, newSheetName, onSelect, onCreateSheet }) {
  return html`
    <section id="state-picker" class="state">
      <h2>Select a Spreadsheet</h2>
      <div id="picker-create">
        <input type="text" placeholder="New spreadsheet name"
               value=${newSheetName}
               onInput=${(e) => setState({ newSheetName: e.target.value })}
               onKeydown=${(e) => { if (e.key === 'Enter') onCreateSheet(); }} />
        <button class="primary" disabled=${creatingSheet}
                onClick=${onCreateSheet}>
          ${creatingSheet ? 'Creating\u2026' : 'Create'}
        </button>
      </div>
      <div class="picker-divider">or choose a DriveMark spreadsheet</div>
      ${pickerLoading && html`<div id="picker-loading">Loading spreadsheets\u2026</div>`}
      ${pickerError && html`<div id="picker-error">${pickerError}</div>`}
      ${!pickerLoading && html`
        <div id="spreadsheet-list">
          ${pickerFiles.length === 0 && !pickerError
            ? 'No DriveMark spreadsheets found. Create one above to get started.'
            : pickerFiles.map(file => html`
                <button class="sheet-option"
                        title=${`Last modified: ${new Date(file.modifiedTime).toLocaleDateString()}`}
                        onClick=${() => onSelect(file.id)}>
                  ${file.name}
                </button>
              `)
          }
        </div>
      `}
    </section>
  `;
}

// --- Business logic ---

export async function loadSpreadsheetPicker() {
  setState({ screen: 'picker', pickerLoading: true, pickerError: null, pickerFiles: [] });

  try {
    const files = await sendMessage({ action: 'listSpreadsheets' });
    setState({ pickerLoading: false, pickerFiles: files });
  } catch (err) {
    setState({ pickerLoading: false, pickerError: `Failed to load spreadsheets: ${err.message}` });
  }
}

export async function createNewSpreadsheet() {
  const name = (state.newSheetName || '').trim();
  if (!name) return null;

  setState({ creatingSheet: true, pickerError: null });
  try {
    const result = await sendMessage({ action: 'createSpreadsheet', title: name });
    setState({ creatingSheet: false });
    return result.id;
  } catch (err) {
    setState({ creatingSheet: false, pickerError: `Failed to create spreadsheet: ${err.message}` });
    return null;
  }
}
