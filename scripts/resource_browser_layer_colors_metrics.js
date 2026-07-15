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
  "resource-browser-layer-colors.html"
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

const screenshotDir = path.join(repoRoot, "run", "screenshots", "browser");
fs.mkdirSync(screenshotDir, { recursive: true });

const fileUrl = `file:///${harnessPath.replace(/\\/g, "/")}`;
const chromeArgs = [
  "--headless=new",
  "--disable-gpu",
  "--disable-background-networking",
  "--allow-file-access-from-files",
  "--window-size=1487,942",
  "--force-device-scale-factor=1",
  "--virtual-time-budget=4000",
];

const domOutput = execFileSync(
  chrome,
  [...chromeArgs, "--dump-dom", fileUrl],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 }
);

const match = domOutput.match(/BROWSER_LAYER_COLORS_METRICS\s+({.*})/);
if (!match) {
  console.error("Browser layer colors metrics payload not found.");
  process.exit(2);
}

const payload = JSON.parse(match[1]);
const screenshotPath = path.join(screenshotDir, "resource-browser-layer-colors-1463x843.png");

execFileSync(
  chrome,
  [...chromeArgs, `--screenshot=${screenshotPath}`, fileUrl],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 }
);

payload.screenshot = path.relative(repoRoot, screenshotPath).replace(/\\/g, "/");
console.log("BROWSER_LAYER_COLORS_METRICS " + JSON.stringify(payload));
