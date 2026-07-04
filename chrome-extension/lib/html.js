import { h, render } from '../vendor/preact.module.js';
import htm from '../vendor/htm.module.js';

export const html = htm.bind(h);
export { h, render };
