import { defineConfig } from 'vite';

const backend = process.env.ACME_API_URL || 'http://localhost:8080';

export default defineConfig({
  root: 'frontend',
  build: { outDir: 'dist', emptyOutDir: true },
  server: {
    host: '127.0.0.1', port: 5173, strictPort: true,
    proxy: { '/api': { target: backend, changeOrigin: false } },
  },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true },
});
