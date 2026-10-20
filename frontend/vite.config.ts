import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Keep browser API calls same-origin in development while the Java service
// continues to own sessions and authorization on localhost:8080.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
