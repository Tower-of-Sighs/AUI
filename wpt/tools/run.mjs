#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { mkdir, readFile, readdir, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { captureBrowserSnapshots } from './browser-adapter.mjs';
import { captureAuiSnapshots } from './aui-adapter.mjs';

const TOOL_DIR = path.dirname(fileURLToPath(import.meta.url));
const WPT_DIR = path.resolve(TOOL_DIR, '..');
const DEFAULT_CONFIG = path.join(WPT_DIR, 'config', 'runner.json');
const RESULT_FILE = 'results.json';
const INVENTORY_FILE = 'inventory.json';
const SUMMARY_FILE = 'run-summary.json';
const RESULT_STATUSES = new Set([
  'pass',
  'layout-mismatch',
  'aui-runtime-unsupported',
  'browser-test-failed',
  'infra-blocked',
  'timeout',
  'pending'
]);

function parseArgs(argv) {
  const args = { mode: 'inventory', config: DEFAULT_CONFIG, limit: Infinity, help: false };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--help' || argument === '-h') args.help = true;
    else if (argument.startsWith('--mode=')) args.mode = argument.slice('--mode='.length);
    else if (argument === '--mode') args.mode = argv[++index];
    else if (argument.startsWith('--config=')) args.config = argument.slice('--config='.length);
    else if (argument === '--config') args.config = argv[++index];
    else if (argument.startsWith('--limit=')) args.limit = Number(argument.slice('--limit='.length));
    else if (argument === '--limit') args.limit = Number(argv[++index]);
    else throw new Error(`Unknown argument: ${argument}`);
  }
  if (!['inventory', 'incremental', 'full'].includes(args.mode)) {
    throw new Error(`Unsupported mode: ${args.mode}. Use inventory, incremental, or full.`);
  }
  if (args.limit !== Infinity && (!Number.isInteger(args.limit) || args.limit < 1)) {
    throw new Error('--limit must be a positive integer.');
  }
  return args;
}

function usage() {
  return `Usage: node wpt/tools/run.mjs [--mode inventory|incremental|full] [--limit N] [--config PATH]\n\n`
    + 'inventory: refresh the complete WPT case inventory and progress table.\n'
    + 'incremental: execute changed cases through configured adapters.\n'
    + 'full: execute every case through configured adapters.\n';
}

async function readJson(file, fallback) {
  if (!existsSync(file)) return fallback;
  try {
    return JSON.parse(await readFile(file, 'utf8'));
  } catch (error) {
    throw new Error(`Cannot parse ${file}: ${error.message}`);
  }
}

async function writeJsonAtomic(file, value) {
  await mkdir(path.dirname(file), { recursive: true });
  const temporary = `${file}.${process.pid}.tmp`;
  await writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
  await rename(temporary, file);
}

function resolveConfigPath(configFile, value) {
  return path.resolve(path.dirname(configFile), value);
}

function normalPath(value) {
  return value.split(path.sep).join('/');
}

async function walkHtml(directory, files) {
  const children = await readdir(directory, { withFileTypes: true });
  for (const child of children) {
    const resolved = path.join(directory, child.name);
    if (child.isDirectory()) await walkHtml(resolved, files);
    else if (child.isFile() && ['.html', '.htm', '.xhtml'].includes(path.extname(child.name).toLowerCase())) files.push(resolved);
  }
}

async function mapConcurrent(values, concurrency, mapper) {
  const results = new Array(values.length);
  let nextIndex = 0;
  async function worker() {
    while (nextIndex < values.length) {
      const index = nextIndex++;
      results[index] = await mapper(values[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, values.length) }, worker));
  return results;
}

function classifySource(source, relativePath) {
  const lower = source.toLowerCase();
  const rel = relativePath.toLowerCase();
  const matches = [...source.matchAll(/<link\b[^>]*\brel\s*=\s*["']([^"']*)["'][^>]*>/gi)];
  const linkRelations = matches.flatMap((match) => match[1].toLowerCase().split(/\s+/));
  const harness = /testharness(?:report)?\.js/i.test(source);
  const reftest = linkRelations.includes('match') || linkRelations.includes('mismatch');
  const manual = /(?:^|[\s/-])manual(?:[\s.-]|$)/i.test(rel) || /<meta\b[^>]*name\s*=\s*["']?flags["']?[^>]*content\s*=\s*["'][^"']*manual/i.test(source);
  const serverDependency = /(?:\.sub\.|{{|[?]pipe=|wptserve|testdriver\.js|web-platform\.test)/i.test(source);
  const externalDependency = /<(?:script|img|iframe|source|video|audio)\b[^>]*\bsrc\s*=\s*["']https?:\/\//i.test(source)
    || /<link\b(?=[^>]*\brel\s*=\s*["'][^"']*stylesheet)(?=[^>]*\bhref\s*=\s*["']https?:\/\/)[^>]*>/i.test(source);
  const featurePatterns = [
    ['flex', /\b(?:display\s*:\s*(?:inline-)?flex|flex(?:-|:))/i],
    ['grid', /\b(?:display\s*:\s*(?:inline-)?grid|grid(?:-|:))/i],
    ['position', /\bposition\s*:/i],
    ['overflow', /\boverflow(?:-[xy])?\s*:/i],
    ['sizing', /\b(?:box-sizing|aspect-ratio|min-(?:width|height)|max-(?:width|height))\s*:/i],
    ['transform', /\btransform(?:-origin)?\s*:/i],
    ['writing-modes', /\b(?:writing-mode|direction)\s*:/i],
    ['multicol', /\b(?:columns?|column-(?:count|width|gap|rule))\s*:/i],
    ['table', /\bdisplay\s*:\s*table/i],
    ['contain', /\bcontain(?:-intrinsic-(?:width|height|size))?\s*:/i]
  ];
  const features = featurePatterns.filter(([, pattern]) => pattern.test(source)).map(([feature]) => feature);
  return {
    type: manual ? 'manual' : reftest ? 'reftest' : harness ? 'testharness' : 'layout-page',
    requiresServer: serverDependency,
    hasExternalDependency: externalDependency,
    features
  };
}

async function buildInventory(config, configFile) {
  const corpus = resolveConfigPath(configFile, config.corpus);
  if (!existsSync(corpus)) throw new Error(`WPT corpus is missing: ${corpus}`);
  const files = [];
  for (const directory of config.layoutDirectories) {
    const root = path.join(corpus, directory);
    if (!existsSync(root)) throw new Error(`Configured WPT layout directory is missing: ${root}`);
    await walkHtml(root, files);
  }
  files.sort((left, right) => left.localeCompare(right));
  return mapConcurrent(files, 32, async (file) => {
    const source = await readFile(file, 'utf8');
    const relativePath = normalPath(path.relative(corpus, file));
    return {
      id: relativePath,
      sourceHash: createHash('sha256').update(source).digest('hex'),
      ...classifySource(source, relativePath)
    };
  });
}

function resultForCase(testCase, previous) {
  if (previous?.sourceHash === testCase.sourceHash && RESULT_STATUSES.has(previous.status)) {
    const retained = { ...previous, id: testCase.id, sourceHash: testCase.sourceHash };
    if (retained.browser?.status === 'infra-blocked' && !testCase.requiresServer && !testCase.hasExternalDependency) {
      delete retained.browser;
      delete retained.aui;
      retained.status = 'pending';
      retained.reason = 'dependency-classification-changed';
    }
    return retained;
  }
  return {
    id: testCase.id,
    sourceHash: testCase.sourceHash,
    status: 'pending',
    reason: 'not-run',
    updatedAt: null
  };
}

function markdownProgress({ config, inventory, results, run }) {
  const totals = Object.fromEntries([...RESULT_STATUSES].map((status) => [status, 0]));
  for (const result of results) totals[result.status] = (totals[result.status] ?? 0) + 1;
  const resultsById = new Map(results.map((result) => [result.id, result]));
  const modules = new Map();
  for (const testCase of inventory) {
    const moduleName = testCase.id.split('/').slice(0, 2).join('/');
    const module = modules.get(moduleName) ?? { total: 0, pass: 0, pending: 0, mismatch: 0 };
    module.total += 1;
    const status = resultsById.get(testCase.id)?.status;
    if (status === 'pass') module.pass += 1;
    else if (status === 'layout-mismatch') module.mismatch += 1;
    else module.pending += 1;
    modules.set(moduleName, module);
  }
  const lines = [
    '# AUI WPT Layout Progress',
    '',
    '> Generated by `node wpt/tools/run.mjs`; do not edit by hand.',
    '',
    `- WPT revision: \`${config.wptRevision}\``,
    `- Last inventory: ${run.completedAt}`,
    `- Execution mode: \`${run.mode}\``,
    `- Total cases: ${inventory.length}`,
    '',
    '## Status',
    '',
    '| Status | Cases |',
    '| --- | ---: |',
    ...[...RESULT_STATUSES].map((status) => `| \`${status}\` | ${totals[status] ?? 0} |`),
    '',
    '## Modules',
    '',
    '| WPT module | Total | Pass | Mismatch | Not passed |',
    '| --- | ---: | ---: | ---: | ---: |',
    ...[...modules.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([name, module]) =>
      `| \`${name}\` | ${module.total} | ${module.pass} | ${module.mismatch} | ${module.pending} |`),
    '',
    'Detailed machine-readable results are in ignored `wpt/output/results.json`.'
  ];
  return `${lines.join('\n')}\n`;
}

function compareSnapshots(browser, aui, tolerance = 0.25) {
  const browserNodes = (browser.nodes ?? []).filter((node) => !['style', 'script', 'link'].includes(node.tag));
  const auiNodes = aui.nodes ?? [];
  if (browserNodes.length !== auiNodes.length) return `node-count browser=${browserNodes.length} aui=${auiNodes.length}`;
  for (let index = 0; index < browserNodes.length; index += 1) {
    const left = browserNodes[index];
    const right = auiNodes[index];
    if (left.tag !== right.tag || (left.id ?? '') !== (right.id ?? '')) return `node-${index} identity differs`;
    for (let value = 0; value < 4; value += 1) {
      if (Math.abs(left.rect[value] - right.rect[value]) > tolerance) return `node-${index} rect[${value}] browser=${left.rect[value]} aui=${right.rect[value]}`;
    }
  }
  return null;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    process.stdout.write(usage());
    return;
  }
  const configFile = path.resolve(args.config);
  const config = await readJson(configFile);
  const output = resolveConfigPath(configFile, config.output);
  const progress = resolveConfigPath(configFile, config.progress);
  const inventory = await buildInventory(config, configFile);
  const selected = inventory.slice(0, args.limit);
  const oldResults = await readJson(path.join(output, RESULT_FILE), { cases: [] });
  const oldResultsById = new Map((oldResults.cases ?? []).map((entry) => [entry.id, entry]));
  const results = inventory.map((testCase) => resultForCase(testCase, oldResultsById.get(testCase.id)));
  const run = {
    mode: args.mode,
    completedAt: new Date().toISOString(),
    selectedCases: selected.length,
    totalCases: inventory.length,
    adapters: {
      browser: config.execution?.browserAdapter ?? null,
      aui: config.execution?.auiAdapter ?? null
    }
  };
  const checkpoint = async () => {
    run.completedAt = new Date().toISOString();
    await writeJsonAtomic(path.join(output, RESULT_FILE), {
      schemaVersion: 1,
      wptRevision: config.wptRevision,
      generatedAt: run.completedAt,
      cases: results
    });
    await writeFile(progress, markdownProgress({ config, inventory, results, run }), 'utf8');
  };

  for (const result of results) {
    if (result.browser?.status !== 'pass' || result.aui?.status !== 'pass' || !result.aui.snapshot) continue;
    const browserFile = path.join(output, 'browser', result.browser.snapshot);
    if (!existsSync(browserFile)) continue;
    const browserSnapshot = JSON.parse(await readFile(browserFile, 'utf8'));
    const difference = compareSnapshots(browserSnapshot, result.aui.snapshot);
    result.status = difference == null ? 'pass' : 'layout-mismatch';
    result.reason = difference;
  }

  if (args.mode !== 'inventory') {
    const changed = inventory.filter((testCase) => {
      const previous = oldResultsById.get(testCase.id);
      return previous?.sourceHash !== testCase.sourceHash || previous?.status !== 'pass';
    });
    const requested = (args.mode === 'full' ? inventory : changed).slice(0, args.limit);
    const resultsById = new Map(results.map((result) => [result.id, result]));
    const browserNeeded = requested.filter((testCase) => {
      const status = resultsById.get(testCase.id)?.browser?.status;
      return status == null || status === 'timeout' || status === 'browser-test-failed';
    });
    const browserResults = await captureBrowserSnapshots({
      corpus: resolveConfigPath(configFile, config.corpus),
      cases: browserNeeded,
      output: path.join(output, 'browser'),
      viewport: config.viewport,
      workers: config.execution?.browserWorkers
    });
    for (const browser of browserResults) {
      const result = resultsById.get(browser.id);
      result.browser = browser;
      result.updatedAt = run.completedAt;
      if (browser.status === 'pass') {
        result.status = 'pending';
        result.reason = 'awaiting-aui-snapshot';
      } else {
        result.status = browser.status;
        result.reason = browser.reason;
      }
    }
    const browserPassedCases = requested.filter((testCase) => resultsById.get(testCase.id)?.browser?.status === 'pass')
      .map((testCase) => ({ ...testCase, source: path.join(resolveConfigPath(configFile, config.corpus), testCase.id) }));
    let auiViewport = config.viewport;
    if (browserPassedCases.length > 0) {
      const firstResult = resultsById.get(browserPassedCases[0].id);
      const firstSnapshot = JSON.parse(await readFile(path.join(output, 'browser', firstResult.browser.snapshot), 'utf8'));
      if (Array.isArray(firstSnapshot.viewport) && firstSnapshot.viewport.length >= 2) {
        auiViewport = { width: firstSnapshot.viewport[0], height: firstSnapshot.viewport[1], dpr: firstSnapshot.viewport[2] ?? 1 };
      }
    }
    run.browserExecuted = browserResults.length;
    run.browserPassed = browserResults.filter((result) => result.status === 'pass').length;
    await checkpoint();
    const auiNeededCases = browserPassedCases.filter((testCase) => resultsById.get(testCase.id)?.aui?.status !== 'pass');
    run.auiExecuted = 0;
    const applyAuiResults = async (batchResults) => {
      for (const aui of batchResults) {
        const result = resultsById.get(aui.id);
        result.aui = aui;
        result.updatedAt = run.completedAt;
        if (aui.status !== 'pass') {
          result.status = aui.status;
          result.reason = aui.reason;
          continue;
        }
        const browserSnapshot = JSON.parse(await readFile(path.join(output, 'browser', result.browser.snapshot), 'utf8'));
        const difference = compareSnapshots(browserSnapshot, aui.snapshot);
        result.status = difference == null ? 'pass' : 'layout-mismatch';
        result.reason = difference;
      }
      run.auiExecuted += batchResults.length;
      await checkpoint();
    };
    await captureAuiSnapshots({
      repoRoot: path.resolve(WPT_DIR, '..'), cases: auiNeededCases, output: path.join(output, 'aui'), viewport: auiViewport,
      onBatch: applyAuiResults
    });
  }

  await writeJsonAtomic(path.join(output, INVENTORY_FILE), {
    schemaVersion: 1,
    wptRevision: config.wptRevision,
    generatedAt: run.completedAt,
    cases: inventory
  });
  await writeJsonAtomic(path.join(output, RESULT_FILE), {
    schemaVersion: 1,
    wptRevision: config.wptRevision,
    generatedAt: run.completedAt,
    cases: results
  });
  await writeJsonAtomic(path.join(output, SUMMARY_FILE), run);
  await writeFile(progress, markdownProgress({ config, inventory, results, run }), 'utf8');
  process.stdout.write(`WPT inventory complete: ${inventory.length} cases; progress: ${normalPath(path.relative(process.cwd(), progress))}\n`);
}

main().catch((error) => {
  process.stderr.write(`WPT runner failed: ${error.message}\n`);
  process.exitCode = 1;
});
