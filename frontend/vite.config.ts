import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  base: "./",
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  server: {
    proxy: {
      "/organizations": { target: "https://localhost:61811", secure: false },
      "/orgdirectory": { target: "https://localhost:61811", secure: false },
    },
  },
  build: { outDir: "dist", emptyOutDir: true },
});
