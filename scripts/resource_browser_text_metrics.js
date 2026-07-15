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
  "resource-browser-text-metrics.html"
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

const fileUrl = `file:///${harnessPath.replace(/\\/g, "/")}`;
const output = execFileSync(
  chrome,
  [
    "--headless=new",
    "--disable-gpu",
    "--disable-background-networking",
    "--allow-file-access-from-files",
    "--window-size=1487,942",
    "--force-device-scale-factor=1",
    "--virtual-time-budget=4000",
    "--dump-dom",
    fileUrl,
  ],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 }
);

const match = output.match(/BROWSER_TEXT_METRICS\s+(\[.*\])/);
if (!match) {
  console.error("Browser text metrics payload not found.");
  process.exit(2);
}

const payload = JSON.parse(match[1]);
console.log("BROWSER_TEXT_METRICS " + JSON.stringify(payload));
