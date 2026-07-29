import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';

function runProcess(command, args, options) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { ...options, stdio: 'ignore' });
    const timer = setTimeout(() => {
      if (process.platform === 'win32') {
        const killer = spawn('taskkill', ['/pid', String(child.pid), '/t', '/f'], { stdio: 'ignore' });
        killer.once('exit', () => reject(new Error(`AUI client timed out after ${options.timeout}ms`)));
      } else {
        child.kill('SIGKILL');
        reject(new Error(`AUI client timed out after ${options.timeout}ms`));
      }
    }, options.timeout);
    child.once('error', (error) => { clearTimeout(timer); reject(error); });
    child.once('exit', (code) => {
      clearTimeout(timer);
      if (code === 0) resolve();
      else reject(new Error(`AUI client exited with code ${code}`));
    });
  });
}

async function readClientResults(file) {
  const byId = new Map();
  if (!existsSync(file)) return byId;
  for (const line of (await readFile(file, 'utf8')).split(/\r?\n/)) {
    const [id, status, detail = ''] = line.split('\t');
    if (!id || !status) continue;
    byId.set(id, status === 'pass'
      ? { id, status, snapshot: JSON.parse(Buffer.from(detail, 'base64url').toString('utf8')) }
      : { id, status, reason: detail });
  }
  return byId;
}

async function captureBatch({ repoRoot, cases, output, viewport, batch }) {
  const input = path.join(output, `client-input-${process.pid}-${batch}.tsv`);
  const result = path.join(output, `client-result-${process.pid}-${batch}.tsv`);
  const gradleInput = input.replace(/\\/g, '/');
  const gradleResult = result.replace(/\\/g, '/');
  await writeFile(input, `${cases.map((testCase) => `${testCase.id}\t${testCase.source}`).join('\n')}\n`, 'utf8');
  try {
    const command = process.platform === 'win32' ? 'cmd.exe' : path.join(repoRoot, 'gradlew');
    const args = process.platform === 'win32'
      ? ['/d', '/s', '/c', 'gradlew.bat runWptClient --console plain --no-daemon']
      : ['runWptClient', '--console', 'plain', '--no-daemon'];
    const env = {
      ...process.env,
      AUI_WPT_CLIENT_INPUT: gradleInput,
      AUI_WPT_CLIENT_OUTPUT: gradleResult,
      AUI_WPT_CLIENT_EXIT_ON_FINISH: 'true',
      AUI_WPT_CLIENT_TIMEOUT_SECONDS: '900',
      AUI_WPT_CLIENT_STALL_TIMEOUT_SECONDS: '15',
      AUI_WPT_VIEWPORT_WIDTH: String(viewport.width),
      AUI_WPT_VIEWPORT_HEIGHT: String(viewport.height)
    };
    await runProcess(command, args, { cwd: repoRoot, env, timeout: 960000, windowsHide: true });
    if (!existsSync(result)) throw new Error('AUI client completed without writing its snapshot result.');
    const byId = await readClientResults(result);
    return { byId, error: null };
  } catch (error) {
    const detail = String(error.message ?? error).replace(/\s+/g, ' ').trim().slice(-1200);
    const partial = await readClientResults(result);
    return { byId: partial, error: detail };
  } finally {
    await rm(input, { force: true });
    await rm(result, { force: true });
  }
}

export async function captureAuiSnapshots({ repoRoot, cases, output, viewport, onBatch }) {
  await mkdir(output, { recursive: true });
  const pending = [...cases];
  const completed = new Map();
  let batch = 0;
  while (pending.length > 0) {
    const requested = pending.splice(0, 5000);
    const outcome = await captureBatch({ repoRoot, cases: requested, output, viewport, batch: batch++ });
    const updates = [];
    for (const [id, result] of outcome.byId) {
      completed.set(id, result);
      updates.push(result);
    }
    const missing = requested.filter((testCase) => !outcome.byId.has(testCase.id));
    if (outcome.error && missing.length > 0) {
      const failed = missing.shift();
      const result = { id: failed.id, status: 'timeout', reason: outcome.error };
      completed.set(failed.id, result);
      updates.push(result);
      pending.unshift(...missing);
    } else {
      for (const testCase of missing) {
        const result = { id: testCase.id, status: 'aui-runtime-unsupported', reason: 'client did not report this case' };
        completed.set(testCase.id, result);
        updates.push(result);
      }
    }
    if (onBatch) await onBatch(updates, { completed: completed.size, total: cases.length, pending: pending.length });
  }
  return cases.map((testCase) => completed.get(testCase.id));
}
