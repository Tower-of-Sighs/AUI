const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const repoRoot = path.resolve(__dirname, "..");
const harnessPath = path.join(
  repoRoot,
  "src",
  "main",
  "resources",
  "assets",
  "apricityui",
  "apricity",
  "tests",
  "resource-browser-browser-metrics.html"
);

const chromeCandidates = [
  process.env.CHROME_PATH,
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
].filter(Boolean);

const chrome = chromeCandidates.find((candidate) => fs.existsSync(candidate));
if (!chrome) {
  console.error("No Chrome/Edge executable found. Set CHROME_PATH to a Chromium-compatible browser.");
  process.exit(1);
}

const staticMode = process.argv.includes("--static") || process.env.RESOURCE_BROWSER_STATIC === "1";
const interactionArg = process.argv.find((arg) => arg.startsWith("--interaction="));
const interactionMode = interactionArg
  ? interactionArg.slice("--interaction=".length)
  : (process.env.RESOURCE_BROWSER_INTERACTION || "");
const promptResponseArg = process.argv.find((arg) => arg.startsWith("--prompt-response="));
const promptResponse = promptResponseArg
  ? promptResponseArg.slice("--prompt-response=".length)
  : (process.env.RESOURCE_BROWSER_PROMPT_RESPONSE || "");
const params = new URLSearchParams();
if (staticMode) params.set("static", "1");
if (interactionMode) params.set("interaction", interactionMode);
if (promptResponse) params.set("promptResponse", promptResponse);
const query = params.toString();
const fileUrl = `file:///${harnessPath.replace(/\\/g, "/")}${query ? "?" + query : ""}`;
const output = execFileSync(
  chrome,
  [
    "--headless=new",
    "--disable-gpu",
    "--disable-background-networking",
    "--allow-file-access-from-files",
    // Chrome headless-new treats --window-size as the outer window size on this
    // test environment. 1487x942 yields an inner content viewport of 1463x843,
    // which matches the AUI browser-mode viewport emitted by runClient.
    "--window-size=1487,942",
    "--force-device-scale-factor=1",
    "--virtual-time-budget=4000",
    "--dump-dom",
    fileUrl,
  ],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 }
);

const match = output.match(/BROWSER_RESOURCE_METRICS\s+({.*})/);
if (!match) {
  console.error("Browser metrics payload not found.");
  process.exit(2);
}

const payload = JSON.parse(match[1]);
console.log("BROWSER_RESOURCE_METRICS " + JSON.stringify(payload));
