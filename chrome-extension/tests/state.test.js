import { describe, it, expect, beforeEach, vi } from 'vitest';
import { state, setState, setRenderCallback } from '../lib/state.js';

// Snapshot the original state keys so we can reset between tests
const originalState = { ...state };

beforeEach(() => {
  Object.assign(state, originalState);
  setRenderCallback(null);
});

describe('setState', () => {
  it('merges a patch into state', () => {
    setState({ screen: 'picker' });
    expect(state.screen).toBe('picker');
  });

  it('does not remove keys not in the patch', () => {
    setState({ screen: 'main' });
    expect(state.spreadsheetId).toBe(null); // untouched
  });

  it('works without a render callback registered', () => {
    expect(() => setState({ screen: 'login' })).not.toThrow();
  });

  it('calls the render callback after merge', () => {
    const cb = vi.fn();
    setRenderCallback(cb);
    setState({ screen: 'main' });
    expect(cb).toHaveBeenCalledOnce();
  });

  it('accumulates multiple patches', () => {
    setState({ screen: 'picker' });
    setState({ spreadsheetId: 'abc' });
    expect(state.screen).toBe('picker');
    expect(state.spreadsheetId).toBe('abc');
  });

  it('triggers callback on every call', () => {
    const cb = vi.fn();
    setRenderCallback(cb);
    setState({ screen: 'a' });
    setState({ screen: 'b' });
    setState({ screen: 'c' });
    expect(cb).toHaveBeenCalledTimes(3);
  });

  it('triggers callback even for empty patch', () => {
    const cb = vi.fn();
    setRenderCallback(cb);
    setState({});
    expect(cb).toHaveBeenCalledOnce();
  });

  it('overwrites existing values', () => {
    setState({ formUrl: 'https://old.com' });
    setState({ formUrl: 'https://new.com' });
    expect(state.formUrl).toBe('https://new.com');
  });
});

describe('setRenderCallback', () => {
  it('replaces a previously set callback', () => {
    const first = vi.fn();
    const second = vi.fn();
    setRenderCallback(first);
    setRenderCallback(second);
    setState({ screen: 'test' });
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledOnce();
  });

  it('clears callback when passed null', () => {
    const cb = vi.fn();
    setRenderCallback(cb);
    setRenderCallback(null);
    setState({ screen: 'test' });
    expect(cb).not.toHaveBeenCalled();
  });
});
