import { readFile } from 'node:fs/promises';

const orePath = new URL('../src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore.css', import.meta.url);
const editPath = new URL('../src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/ore-edit.css', import.meta.url);
const editableTokenPattern = /--ore-[\w-]+\s*:/g;
const literalPattern = /#[0-9a-fA-F]{3,8}|rgba?\([^)]*\)|(?<![\w.-])-?\d+(?:\.\d+)?px\b/g;

const [oreCss, editCss] = await Promise.all([readFile(orePath, 'utf8'), readFile(editPath, 'utf8')]);
const oreTokens = new Set(oreCss.match(editableTokenPattern) ?? []);
const editTokens = new Set(editCss.match(editableTokenPattern) ?? []);
const missing = [...oreTokens].filter((token) => !editTokens.has(token));
const literals = new Set(oreCss.match(literalPattern) ?? []);
const mediaQueries = [];
const withoutMedia = editCss.replace(/@media\s*\([^)]*\)\s*\{/g, (query) => {
  const index = mediaQueries.push(query) - 1;
  return `__ORE_EDIT_MEDIA_${index}__{`;
});
const withoutDefaults = withoutMedia.replace(/\.ore-theme\s*\{[^}]*\}/s, '');
const unresolved = withoutDefaults.match(literalPattern) ?? [];

if (missing.length) {
  throw new Error(`ore-edit.css is missing editable Ore tokens: ${missing.join(', ')}`);
}
if (unresolved.length) {
  throw new Error(`ore-edit.css still contains non-tokenized literals: ${[...new Set(unresolved)].join(', ')}`);
}

console.log(`ore-edit tokens: ${editTokens.size}`);
console.log(`ore.css literal audit entries: ${literals.size}`);
console.log('All non-media literal values are tokenized in ore-edit.css.');
