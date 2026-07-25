// === Icons ===
//
// SVG markup lives in icons/*.svg and is read at startup rather than hand-inlined
// in JS. The file stays the single source of truth, and because the markup ends up
// as real inline SVG the glyph can inherit `currentColor` — neither an <img> nor a
// CSS mask can do that, so hover and focus colour states would otherwise be lost.
//
// Icons are fetched once and cached; `iconMarkup` is synchronous so components can
// stay plain render functions with no async or hooks.

const cache = new Map();

export async function loadIcons(names) {
  await Promise.all(names.map(async name => {
    const res = await fetch(chrome.runtime.getURL(`icons/${name}.svg`));
    if (!res.ok) throw new Error(`Failed to load icon "${name}": ${res.status}`);
    cache.set(name, await res.text());
  }));
}

// Returns '' until the icon has loaded, so a render before loadIcons() resolves
// draws an empty box rather than throwing.
export function iconMarkup(name) {
  return cache.get(name) || '';
}
