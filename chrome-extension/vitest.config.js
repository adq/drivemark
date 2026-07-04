import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['tests/**/*.test.js'],
    coverage: {
      include: ['lib/data.js', 'lib/tree.js', 'lib/helpers.js', 'lib/state.js', 'save-form.js'],
    },
  },
});
