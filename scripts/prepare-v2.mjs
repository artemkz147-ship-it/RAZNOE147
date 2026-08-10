import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const parts = Array.from({ length: 6 }, (_, i) => `src/v2/main.part${String(i).padStart(2, '0')}.txt`);
const source = parts.map(p => readFileSync(p, 'utf8')).join('');
const out = 'src/main-v2.generated.ts';
const baseOut = 'src/base-v2.generated.ts';
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, source);

let base = source.replace('class NeonApexGame {', 'export class NeonApexGame {');
const bootAt = base.lastIndexOf('\nconst game = new NeonApexGame();');
if (bootAt < 0) throw new Error('NeonApexGame autostart block was not found');
base = base.slice(0, bootAt) + '\n';
writeFileSync(baseOut, base);
console.log(`assembled ${out} and ${baseOut} from ${parts.length} parts`);
