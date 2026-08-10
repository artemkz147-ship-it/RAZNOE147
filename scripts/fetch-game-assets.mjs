import { mkdir, writeFile } from 'node:fs/promises';

const root='https://raw.githubusercontent.com/KenneyNL/Starter-Kit-3D-Platformer/main/models';
const files=['character.glb','cloud.glb','flag.glb'];

await mkdir('public/assets/kenney',{recursive:true});
for(const name of files){
  const response=await fetch(`${root}/${name}`);
  if(!response.ok) throw new Error(`Asset download failed: ${name} (${response.status})`);
  await writeFile(`public/assets/kenney/${name}`,Buffer.from(await response.arrayBuffer()));
  console.log(`Downloaded ${name}`);
}
