import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const bundlePath='art_b64/production_art_bundle.gz.b64';
const outDir='public/assets/art';
const packed=Buffer.from(fs.readFileSync(bundlePath,'utf8').trim(),'base64');
const json=zlib.gunzipSync(packed).toString('utf8');
const files=JSON.parse(json);
fs.mkdirSync(outDir,{recursive:true});
for(const [name,b64] of Object.entries(files)){
  fs.writeFileSync(path.join(outDir,name),Buffer.from(b64,'base64'));
}
console.log(`Materialized ${Object.keys(files).length} production art assets.`);
