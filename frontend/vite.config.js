import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import macrosPlugin from "vite-plugin-babel-macros";

export default defineConfig(() => {
  return {
    build: {
      outDir: "build",
    },
    plugins: [react(), macrosPlugin()],
    test: {
      environment: "jsdom",
      setupFiles: ["./tests/setup.ts"],
      testMatch: ["./tests/**/*.test.tsx"],
      globals: true,
      coverage: {
        provider: "v8",
      },
    },
    define: {
      "process.env": process.env,
       global: 'globalThis',
    },
  };
});
