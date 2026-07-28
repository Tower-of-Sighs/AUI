import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const orePath = new URL('../src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore.css', import.meta.url);
const editPath = new URL('../src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore-edit.css', import.meta.url);
const literalPattern = /#[0-9a-fA-F]{3,8}|rgba?\([^)]*\)|(?<![\w.-])-?\d+(?:\.\d+)?px\b/g;

function tokenFor(literal) {
  if (literal.endsWith('px')) {
    const value = literal.slice(0, -2);
    const tokenValue = value.startsWith('-') ? `negative-${value.slice(1)}` : value;
    return `--ore-edit-size-${tokenValue.replace('.', '_')}`;
  }
  if (literal.startsWith('#')) {
    return `--ore-edit-color-${literal.slice(1).toLowerCase()}`;
  }
  const hash = createHash('sha1').update(literal).digest('hex').slice(0, 10);
  return `--ore-edit-alpha-${hash}`;
}

const source = await readFile(orePath, 'utf8');
const mediaQueries = [];
const protectedMedia = source.replace(/@media\s*\([^)]*\)\s*\{/g, (query) => {
  const index = mediaQueries.push(query) - 1;
  return `__ORE_EDIT_MEDIA_${index}__{`;
});
const literals = [...new Set(protectedMedia.match(literalPattern) ?? [])].sort((a, b) => a.localeCompare(b));
const defaults = literals.map((literal) => `  ${tokenFor(literal)}: ${literal};`).join('\n');
const transformed = protectedMedia.replace(literalPattern, (literal) => `var(${tokenFor(literal)})`)
  .replace(/__ORE_EDIT_MEDIA_(\d+)__\{/g, (_, index) => mediaQueries[Number(index)]);
const tokenBlock = [
  '/* Generated from ore.css by tools/generate-ore-edit.mjs. Do not edit manually. */',
  '/* The editor applies overrides only to its canvas theme root. */',
  '',
  '.ore-theme {',
  '  /* Component-level defaults preserve the stable Ore visual baseline. */',
  defaults,
  '}',
  ''
].join('\n');

const insertion = transformed.indexOf('.ore-theme {');
if (insertion < 0) {
  throw new Error('Could not find the Ore theme scope.');
}

await writeFile(editPath, `${transformed.slice(0, insertion)}${tokenBlock}${transformed.slice(insertion)}`, 'utf8');
console.log(`Generated ${editPath.pathname} with ${literals.length} editable literals.`);
