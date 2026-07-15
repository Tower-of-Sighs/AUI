const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const repoRoot = path.resolve(__dirname, "..");
const fixturePath = process.env.RESOURCE_BROWSER_FONT_RASTER_DOC_PATH || "resource-browser-font-raster.html";
const screenshotName = process.env.RESOURCE_BROWSER_FONT_RASTER_SCREENSHOT || "resource-browser-font-raster-1463x843-dsf-aui.png";
const harnessPath = path.join(
  repoRoot,
  "src",
  "main",
  "resources",
  "assets",
  "apricityui",
  "apricity",
  "tests",
  fixturePath
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

const scale = process.env.RESOURCE_BROWSER_DSF || "1.74982911825017";
const fileUrl = `file:///${harnessPath.replace(/\\/g, "/")}`;
const chromeArgs = [
  "--headless=new",
  "--disable-gpu",
  "--disable-background-networking",
  "--allow-file-access-from-files",
  "--window-size=1463,843",
  `--force-device-scale-factor=${scale}`,
  "--virtual-time-budget=4000",
];

const domOutput = execFileSync(
  chrome,
  [...chromeArgs, "--dump-dom", fileUrl],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 }
);

const match = domOutput.match(/BROWSER_FONT_RASTER_METRICS\s+({.*})/);
if (!match) {
  console.error("Browser font raster metrics payload not found.");
  process.exit(2);
}

const payload = JSON.parse(match[1]);
const screenshotPath = path.join(screenshotDir, screenshotName);

execFileSync(
  chrome,
  [...chromeArgs, `--screenshot=${screenshotPath}`, fileUrl],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 }
);

payload.screenshot = path.relative(repoRoot, screenshotPath).replace(/\\/g, "/");
console.log("BROWSER_FONT_RASTER_METRICS " + JSON.stringify(payload));
