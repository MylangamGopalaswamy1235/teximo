const fs = require("node:fs");
const path = require("node:path");

const projectRoot = path.resolve(__dirname, "..");
const frontendDirectory = path.join(projectRoot, "frontend");
const outputDirectory = path.join(projectRoot, "dist");
const apiBaseUrl = process.env.TEXIMO_API?.trim();

if (!apiBaseUrl) {
  throw new Error("Set TEXIMO_API in Vercel Project Settings to your deployed backend URL ending in /api.");
}

let parsedApiUrl;
try {
  parsedApiUrl = new URL(apiBaseUrl);
} catch {
  throw new Error("TEXIMO_API must be a valid absolute URL ending in /api.");
}

if (
  parsedApiUrl.protocol !== "https:" ||
  parsedApiUrl.username ||
  parsedApiUrl.password ||
  parsedApiUrl.search ||
  parsedApiUrl.hash ||
  !parsedApiUrl.pathname.replace(/\/+$/, "").endsWith("/api")
) {
  throw new Error("TEXIMO_API must be an HTTPS backend URL ending in /api, without credentials, query parameters, or a fragment.");
}

fs.rmSync(outputDirectory, { recursive: true, force: true });
fs.cpSync(frontendDirectory, outputDirectory, { recursive: true });
fs.writeFileSync(
  path.join(outputDirectory, "js", "config.js"),
  `window.TEXIMO_API = ${JSON.stringify(apiBaseUrl.replace(/\/+$/, ""))};\n`,
  "utf8"
);

console.log(`Built Teximo frontend for ${parsedApiUrl.origin}.`);
