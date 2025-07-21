import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    dir: './src',
    include: ['**/**.test.?(c|m)[jt]s?(x)'],
    coverage: {
      include: ['src/**'],
    },
    setupFiles: 'src/tests/setup.ts',
    server: {
      deps: {
        inline: ['@opetushallitus/oph-design-system'],
      },
    },
  },
});

