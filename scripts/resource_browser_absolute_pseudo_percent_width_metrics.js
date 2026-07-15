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
  "absolute-pseudo-percent-width.html"
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
    "--virtual-time-budget=1500",
    "--dump-dom",
    fileUrl,
  ],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 }
);

const match = output.match(/BROWSER_ABSOLUTE_PSEUDO_PERCENT_WIDTH_METRICS\s+({.*})/);
if (!match) {
  console.error("Browser absolute pseudo percent-width metrics payload not found.");
  process.exit(2);
}

console.log("BROWSER_ABSOLUTE_PSEUDO_PERCENT_WIDTH_METRICS " + JSON.stringify(JSON.parse(match[1])));
