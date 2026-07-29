import { createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { availableParallelism } from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);
const SNAPSHOT_ID = '__aui_wpt_browser_snapshot__';
const BATCH_SNAPSHOT_ID = '__aui_wpt_batch_snapshot__';

function chromiumPath() {
  return [
    process.env.CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
  ].find((candidate) => candidate && existsSync(candidate));
}

function contentType(file) {
  return new Map([
    ['.css', 'text/css; charset=utf-8'], ['.html', 'text/html; charset=utf-8'],
    ['.htm', 'text/html; charset=utf-8'], ['.js', 'text/javascript; charset=utf-8'],
    ['.json', 'application/json; charset=utf-8'], ['.svg', 'image/svg+xml'],
    ['.png', 'image/png'], ['.woff', 'font/woff'], ['.woff2', 'font/woff2']
  ]).get(path.extname(file).toLowerCase()) ?? 'application/octet-stream';
}

function probeSource() {
  return `<script>(function(){
function number(value){return Number.isFinite(value)?Number(value.toFixed(4)):null;}
function capture(){
  var nodes=Array.from(document.querySelectorAll('*')).map(function(element,index){
    var rect=element.getBoundingClientRect(), style=getComputedStyle(element);
    return {index:index,tag:element.localName,id:element.id||null,className:typeof element.className==='string'?element.className:null,
      rect:[number(rect.x),number(rect.y),number(rect.width),number(rect.height)],
      scroll:[element.scrollWidth,element.scrollHeight,element.clientWidth,element.clientHeight],
      computed:{display:style.display,position:style.position,boxSizing:style.boxSizing,overflowX:style.overflowX,overflowY:style.overflowY}};
  });
  var output=document.createElement('script'); output.id='${SNAPSHOT_ID}'; output.type='application/json';
  output.textContent=JSON.stringify({viewport:[innerWidth,innerHeight,devicePixelRatio],nodes:nodes}).replace(/</g,'\\u003c');
  document.documentElement.appendChild(output);
}
addEventListener('load',function(){setTimeout(capture,50);},{once:true});
})();</script>`;
}

function injectProbe(source) {
  const probe = probeSource();
  const closingBody = /<\/body\s*>/i;
  return closingBody.test(source) ? source.replace(closingBody, `${probe}</body>`) : `${source}${probe}`;
}

function batchPage(ids, viewport) {
  return `<!doctype html><body><script>
const ids=${JSON.stringify(ids)}, results=[], workers=[]; let cursor=0;
async function run(){while(cursor<ids.length){const id=ids[cursor++], frame=document.createElement('iframe');
frame.style.cssText='position:absolute;left:-10000px;top:0;border:0';frame.width='${viewport.width}';frame.height='${viewport.height}';document.body.appendChild(frame);
try{await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('iframe timeout')),4000);frame.onload=()=>{clearTimeout(timer);setTimeout(resolve,100)};frame.src='/'+id+'?__aui_wpt_probe=1'});
const node=frame.contentDocument.getElementById('${SNAPSHOT_ID}');if(!node)throw new Error('layout probe did not produce a snapshot');
results.push({id,status:'pass',snapshot:JSON.parse(node.textContent)});}catch(error){results.push({id,status:'browser-test-failed',reason:String(error.message||error)});}finally{frame.remove();}}}
for(let i=0;i<Math.min(8,ids.length);i++)workers.push(run());Promise.all(workers).then(()=>{const out=document.createElement('script');out.id='${BATCH_SNAPSHOT_ID}';out.type='application/json';out.textContent=JSON.stringify(results);document.body.appendChild(out)});
</script></body>`;
}

async function startCorpusServer(corpus) {
  const batches = new Map();
  const server = createServer(async (request, response) => {
    try {
      const requestUrl = new URL(request.url, 'http://127.0.0.1');
      const relative = decodeURIComponent(requestUrl.pathname).replace(/^\/+/, '');
      if (relative === '__aui_wpt_batch') {
        const batch = batches.get(requestUrl.searchParams.get('id'));
        if (!batch) throw new Error('unknown batch');
        response.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
        response.end(batchPage(batch.ids, batch.viewport));
        return;
      }
      const file = path.resolve(corpus, relative);
      if (!file.startsWith(`${corpus}${path.sep}`) && file !== corpus) {
        response.writeHead(403).end();
        return;
      }
      const body = await readFile(file);
      const probe = requestUrl.searchParams.get('__aui_wpt_probe') === '1';
      const html = probe && ['.html', '.htm', '.xhtml'].includes(path.extname(file).toLowerCase());
      response.writeHead(200, { 'content-type': contentType(file), 'cache-control': 'no-store' });
      response.end(html ? injectProbe(body.toString('utf8')) : body);
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const address = server.address();
  return {
    server,
    origin: `http://127.0.0.1:${address.port}`,
    registerBatch(ids, viewport) {
      const id = createHash('sha1').update(ids.join('\n')).digest('hex');
      batches.set(id, { ids, viewport });
      return id;
    }
  };
}

async function mapConcurrent(values, concurrency, mapper) {
  const results = new Array(values.length);
  let next = 0;
  async function worker() {
    while (next < values.length) {
      const index = next++;
      results[index] = await mapper(values[index]);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, values.length) }, worker));
  return results;
}

function snapshotFromDom(dom) {
  const pattern = new RegExp(`<script id="${SNAPSHOT_ID}" type="application/json">([\\s\\S]*?)<\\/script>`);
  const match = dom.match(pattern);
  if (!match) throw new Error('layout probe did not produce a snapshot');
  return JSON.parse(match[1]);
}

function batchSnapshotsFromDom(dom) {
  const pattern = new RegExp(`<script id="${BATCH_SNAPSHOT_ID}" type="application/json">([\\s\\S]*?)<\\/script>`);
  const match = dom.match(pattern);
  if (!match) throw new Error('browser batch did not produce results');
  return JSON.parse(match[1]);
}

function workerCount(value) {
  return value === 'auto' || value == null ? Math.max(1, Math.min(4, availableParallelism())) : Math.max(1, Number(value));
}

export async function captureBrowserSnapshots({ corpus, cases, output, viewport, workers }) {
  const chrome = chromiumPath();
  if (!chrome) throw new Error('No Chromium executable found. Set CHROME_PATH.');
  await mkdir(output, { recursive: true });
  const { server, origin, registerBatch } = await startCorpusServer(corpus);
  try {
    const blocked = cases.filter((testCase) => testCase.requiresServer || testCase.hasExternalDependency)
      .map((testCase) => ({ id: testCase.id, status: 'infra-blocked', reason: 'requires-server-or-external-network' }));
    const runnable = cases.filter((testCase) => !testCase.requiresServer && !testCase.hasExternalDependency);
    const batches = [];
    for (let index = 0; index < runnable.length; index += 50) batches.push(runnable.slice(index, index + 50));
    const batchResults = await mapConcurrent(batches, Math.min(4, workerCount(workers)), async (batch) => {
      const batchId = registerBatch(batch.map((testCase) => testCase.id), viewport);
      const url = `${origin}/__aui_wpt_batch?id=${batchId}`;
      try {
        const { stdout } = await execFileAsync(chrome, [
          '--headless=new', '--disable-gpu', '--disable-background-networking', '--disable-default-apps',
          '--force-device-scale-factor=1', `--window-size=${viewport.width},${viewport.height}`,
          '--virtual-time-budget=30000', '--dump-dom', url
        ], { encoding: 'utf8', maxBuffer: 128 * 1024 * 1024, timeout: 45000, windowsHide: true });
        const captured = batchSnapshotsFromDom(stdout);
        const results = [];
        for (const item of captured) {
          if (item.status !== 'pass') { results.push(item); continue; }
          const digest = createHash('sha256').update(JSON.stringify(item.snapshot)).digest('hex');
          const snapshotPath = path.join(output, `${digest}.json`);
          if (!existsSync(snapshotPath)) await writeFile(snapshotPath, `${JSON.stringify(item.snapshot)}\n`, 'utf8');
          results.push({ id: item.id, status: 'pass', reason: null, snapshot: path.basename(snapshotPath), nodes: item.snapshot.nodes.length });
        }
        return results;
      } catch (error) {
        const timedOut = error.killed || error.code === 'ETIMEDOUT';
        return batch.map((testCase) => ({ id: testCase.id, status: timedOut ? 'timeout' : 'browser-test-failed', reason: error.message.slice(0, 500) }));
      }
    });
    return [...blocked, ...batchResults.flat()];
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}
