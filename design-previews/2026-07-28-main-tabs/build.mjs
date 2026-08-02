// 把 frag-a/b/c/d.html 内联进 index.html(替换 <!--FRAG_X--> 占位)
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
let html = fs.readFileSync(path.join(dir, 'index.html'), 'utf8');
for (const L of ['A', 'B', 'C', 'D']) {
  const frag = fs.readFileSync(path.join(dir, `frag-${L.toLowerCase()}.html`), 'utf8');
  const marker = `<!--FRAG_${L}-->`;
  if (!html.includes(marker)) throw new Error(`marker missing: ${marker}`);
  html = html.replace(marker, () => frag);
}
fs.writeFileSync(path.join(dir, 'index.html'), html);
console.log('inlined:', ['A','B','C','D'].map(L => `frag-${L.toLowerCase()}.html`).join(', '));
