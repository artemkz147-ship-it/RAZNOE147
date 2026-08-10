import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const decodeGzipBase64=(file)=>zlib.gunzipSync(Buffer.from(fs.readFileSync(file,'utf8').trim(),'base64'));

const packed=decodeGzipBase64('art_b64/production_art_bundle.gz.b64');
const files=JSON.parse(packed.toString('utf8'));
const outDir='public/assets/art';
fs.mkdirSync(outDir,{recursive:true});
for(const [name,b64] of Object.entries(files)){
  fs.writeFileSync(path.join(outDir,name),Buffer.from(b64,'base64'));
}

const patcher=decodeGzipBase64('art_b64/apply-art.mjs.gz.b64');
fs.writeFileSync('scripts/apply-art.mjs',patcher);
console.log(`Materialized ${Object.keys(files).length} production art assets and art patcher.`);
