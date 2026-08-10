import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const parts = Array.from({ length: 6 }, (_, i) => `src/v2/main.part${String(i).padStart(2, '0')}.txt`);
const out = 'src/main-v2.generated.ts';
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, parts.map(p => readFileSync(p, 'utf8')).join(''));
console.log(`assembled ${out} from ${parts.length} parts`);
