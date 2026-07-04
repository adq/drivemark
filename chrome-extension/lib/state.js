// === State ===

export const state = {
  screen: 'login',
  spreadsheetId: null,
  tabUrl: null,
  tabTitle: null,
  existingBookmark: null,
  folders: [],
  pickerLoading: false,
  pickerError: null,
  pickerFiles: [],
  saving: false,
  signingIn: false,
  bookmarks: [],
  openFolders: {},
  bookmarksLoading: false,
  searchQuery: '',
  deleteConfirmId: null,
  deleting: false,
  hoveredBookmark: null,
  creatingSheet: false,

  // Form fields (controlled inputs)
  formUrl: '',
  formTitle: '',
  formExcerpt: '',
  formCover: '',
  formFolder: '',
  formNotes: '',
  formUrlEdited: false,

  // Picker input
  newSheetName: '',

  // Folder suggestions
  folderSuggestionsVisible: false,

  // Status / error messages
  statusText: null,
  statusType: null,
  globalError: null,
};

let renderCallback = null;

export function setRenderCallback(fn) {
  renderCallback = fn;
}

export function setState(patch) {
  Object.assign(state, patch);
  if (renderCallback) renderCallback();
}
