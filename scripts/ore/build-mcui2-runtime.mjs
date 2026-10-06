import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

const upstreamRoot = resolve(process.argv[2] ?? '');
const outputRoot = resolve(process.argv[3] ?? '');
const expectedCommit = 'd3344a6cec68ce97eb990c125ed3e050c69d7d4c';

if (process.argv.length !== 4 || !existsSync(resolve(upstreamRoot, 'package.json'))) {
  throw new Error('Usage: node build-mcui2-runtime.mjs <upstream-root> <new-output-directory>');
}
if (existsSync(outputRoot)) throw new Error(`Output directory must not exist: ${outputRoot}`);
const actualCommit = execFileSync('git', ['-C', upstreamRoot, 'rev-parse', 'HEAD'], {
  encoding: 'utf8',
}).trim();
if (actualCommit !== expectedCommit) {
  throw new Error(`Expected mcui-oreui ${expectedCommit}, found ${actualCommit}`);
}

const publicComponents = readFileSync(resolve(upstreamRoot, 'src/generated/public-components.ts'), 'utf8');
const componentNames = [...publicComponents.matchAll(/^export \{ default as (Mc\w+) \}/gm)]
  .map((match) => match[1]);
if (componentNames.length !== 69 || !componentNames.includes('McSkinViewer')) {
  throw new Error('The pinned public component registry no longer has its expected 69 entries');
}

const { build } = await import(pathToFileURL(resolve(upstreamRoot, 'node_modules/vite/dist/node/index.js')).href);
const { default: vue } = await import(pathToFileURL(resolve(upstreamRoot, 'node_modules/@vitejs/plugin-vue/dist/index.mjs')).href);

const excludeSkinViewer = {
  name: 'aui-exclude-unused-skin-viewer',
  enforce: 'pre',
  transform(source, id) {
    const path = id.split('?')[0].replaceAll('\\', '/');
    if (path.endsWith('/src/generated/public-components.ts')) {
      const lines = source.split(/\r?\n/);
      const retained = lines.filter((line) => !line.includes('McSkinViewer'));
      if (lines.length - retained.length !== 4) throw new Error('SkinViewer exports changed upstream');
      return retained.join('\n');
    }
    if (path.endsWith('/src/generated/component-registry.ts')) {
      const withoutImport = source.replace(', McSkinViewer,', ',');
      const withoutProperty = withoutImport.replace(/^\s*McSkinViewer,\r?\n/m, '');
      if (withoutProperty === source || withoutProperty.includes('McSkinViewer')) {
        throw new Error('SkinViewer registry changed upstream');
      }
      return withoutProperty;
    }
  },
};

const adaptVisualGallery = {
  name: 'aui-adapt-upstream-visual-gallery',
  enforce: 'pre',
  transform(source, id) {
    if (id.includes('?') || !id.replaceAll('\\', '/').endsWith('/tests/e2e/fixture/src/VisualGallery.vue')) {
      return;
    }
    source = source.replaceAll('\r\n', '\n');
    if ([...source.matchAll(/data-gallery-component=/g)].length !== 69) {
      throw new Error('Upstream visual gallery no longer covers all 69 components');
    }
    const start = source.indexOf('        <mc-skin-viewer\n');
    const end = source.indexOf('        />', start);
    if (start < 0 || end < 0) throw new Error('SkinViewer gallery section changed upstream');
    const withoutViewer = source.slice(0, start) + source.slice(end + '        />'.length);
    const adapted = withoutViewer
      .replace("import { usePop } from '../../../../src'", '')
      .replace('const pop = usePop()', 'const pop = McUIVue.usePop()')
      .replace(/const skinData =\s*\r?\n\s*'[^']+'\s*\r?\n/, '')
      .replace('69 public components', '68 public components')
      .replace(/\.visual-gallery \.mc-skin-viewer \{\s*margin: 0 auto;\s*\}/, '');
    if ([...adapted.matchAll(/data-gallery-component=/g)].length !== 68
        || adapted.includes('McSkinViewer') || adapted.includes('skinData')) {
      throw new Error('Visual gallery SkinViewer exclusion is incomplete');
    }
    return adapted;
  },
};

async function bundle(entry, globalName, fileName, plugins = [], outDir = outputRoot) {
  await build({
    configFile: false,
    root: upstreamRoot,
    logLevel: 'error',
    plugins,
    build: {
      lib: { entry: resolve(upstreamRoot, entry), name: globalName, formats: ['iife'], fileName },
      outDir,
      emptyOutDir: false,
      cssCodeSplit: false,
      sourcemap: false,
      assetsInlineLimit: 100_000_000,
      rollupOptions: { external: ['vue'], output: { exports: 'named', globals: { vue: 'Vue' } } },
    },
  });
}

await bundle('src/index.ts', 'McUIVue', 'mcui-oreui.aui', [excludeSkinViewer, vue()]);
for (const [entry, globalName, fileName] of [
  ['src/icons/normal.ts', 'McUINormalIcons', 'mcui-icons-normal.aui'],
  ['src/icons/key.ts', 'McUIKeyIcons', 'mcui-icons-key.aui'],
  ['src/icons/x.ts', 'McUIXIcons', 'mcui-icons-x.aui'],
  ['src/sounds/default.ts', 'McUIDefaultSounds', 'mcui-sounds-default.aui'],
]) {
  await bundle(entry, globalName, fileName);
}
await bundle('tests/e2e/fixture/src/VisualGallery.vue', 'McUIVisualGallery', 'gallery.aui',
  [adaptVisualGallery, vue()], resolve(outputRoot, 'gallery'));

const fonts = ['Minecraft-Ten.otf', 'Minecraft-Seven.otf',
  'Minecraft-Five.otf', 'Minecraft-Five-Bold.otf'];
mkdirSync(resolve(outputRoot, 'fonts'));
for (const font of fonts) {
  copyFileSync(resolve(upstreamRoot, 'src/assets/fonts', font), resolve(outputRoot, 'fonts', font));
}
const fontStyles = readFileSync(resolve(upstreamRoot, 'src/styles/fonts.css'), 'utf8')
  .replaceAll('../assets/fonts/', 'fonts/');
writeFileSync(resolve(outputRoot, 'fonts.css'), fontStyles);

const runtime = readFileSync(resolve(outputRoot, 'mcui-oreui.aui.iife.js'), 'utf8');
const stylesheet = readFileSync(resolve(outputRoot, 'style.css'), 'utf8');
const gallery = readFileSync(resolve(outputRoot, 'gallery/gallery.aui.iife.js'), 'utf8');
const retainedNames = componentNames.filter((name) => name !== 'McSkinViewer');
if (runtime.includes('McSkinViewer') || retainedNames.some((name) => !runtime.includes(name))) {
  throw new Error('The runtime does not export exactly the expected components');
}
if (stylesheet.includes('.mc-skin-viewer')) throw new Error('SkinViewer CSS was bundled');
if (gallery.includes('McSkinViewer') || !gallery.includes('McCheckbox')) {
  throw new Error('The visual gallery does not match the 68-component package');
}
console.log(`Built mcui-oreui ${actualCommit}: ${retainedNames.length} components, CSS, gallery and optional icons/sounds/fonts`);
