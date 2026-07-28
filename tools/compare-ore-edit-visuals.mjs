import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const themeDir = path.join(repoRoot, 'src', 'main', 'resources', 'assets', 'apricityui', 'apricity', 'apricityui', 'theme', 'ore');
const examplePath = path.join(themeDir, 'example.html');
const temporaryPath = path.join(themeDir, `.ore-edit-visual-regression-${process.pid}.html`);
const outputDir = path.join(repoRoot, 'run', 'ore-edit-regression');
const stablePng = path.join(outputDir, 'ore.png');
const editablePng = path.join(outputDir, 'ore-edit.png');
const chrome = [
  process.env.CHROME_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
].find((candidate) => candidate && existsSync(candidate));

if (!chrome) throw new Error('No Chromium executable found. Set CHROME_PATH.');

const screenshotArgs = [
  '--headless=new',
  '--disable-gpu',
  '--disable-background-networking',
  '--allow-file-access-from-files',
  '--window-size=1463,843',
  '--force-device-scale-factor=1',
  '--virtual-time-budget=4000',
];

function screenshot(page, output) {
  execFileSync(chrome, [...screenshotArgs, `--screenshot=${output}`, pathToFileURL(page).href], {
    encoding: 'utf8',
    maxBuffer: 10 * 1024 * 1024,
  });
}

await import('node:fs/promises').then(({ mkdir }) => mkdir(outputDir, { recursive: true }));
const example = await readFile(examplePath, 'utf8');
if (!example.includes('href="ore.css"')) throw new Error('Ore example stylesheet link was not found.');

try {
  await writeFile(temporaryPath, example.replace('href="ore.css"', 'href="ore-edit.css"'), 'utf8');
  screenshot(examplePath, stablePng);
  screenshot(temporaryPath, editablePng);
  const metrics = JSON.parse(execFileSync('python', [path.join(repoRoot, 'scripts', 'aui_compare.py'), stablePng, editablePng], {
    encoding: 'utf8',
  }));
  console.log(`ORE_EDIT_VISUAL_COMPARISON ${JSON.stringify(metrics)}`);
  if (metrics.rms !== 0) {
    throw new Error(`Default ore-edit.css differs from ore.css (RMS ${metrics.rms}).`);
  }
} finally {
  await rm(temporaryPath, { force: true });
}
