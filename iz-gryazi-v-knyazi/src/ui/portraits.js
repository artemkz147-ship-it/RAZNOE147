import { citizenAtlases } from '../assets/citizens.js';
import { citizenLife,citizenClothes } from '../systems/citizens.js';
import { personAge } from '../systems/population.js';

export function citizenFrames(state,person,age=personAge(state,person)){
  const atlas=citizenAtlases[person.portraitId||person.id]||citizenAtlases.valera;
  const life=citizenLife(state,person),row=citizenClothes.find(x=>x.id===life.outfit)?.row||0;
  const position=Math.max(0,Math.min(atlas.columns-1,(age-atlas.minAge)/10)),column=Math.floor(position),blend=position-column;
  return {atlas,row,column,next:Math.min(atlas.columns-1,column+1),blend,outfit:life.outfit,age};
}
export function citizenPortrait(state,person,cls='',labels='',age){
  const f=citizenFrames(state,person,age),[xs,ys]=f.atlas.bounds;
  const x=xs[f.column]+5,y=ys[f.row]+2,width=xs[f.column+1]-x-5;
  const h=(ys[f.row+1]-y-2)/width;
  // Different ages are separately drawn faces: blending them creates a second
  // face. Crop one decade with explicit coordinates and keep its proportions.
  const clip=`portrait-${++portraitSequence}`;
  const crop=cls.includes('person-portrait-raster')?Math.min(h,1.5):h;
  const layer=`<svg class="citizen-skin" viewBox="0 0 1 ${crop}" preserveAspectRatio="xMidYMin meet" aria-hidden="true"><defs><clipPath id="${clip}" clipPathUnits="userSpaceOnUse"><rect width="1" height="${crop}"/></clipPath></defs><g clip-path="url(#${clip})"><image href="${f.atlas.image}" x="${-x/width}" y="${-y/width}" width="${xs.at(-1)/width}" height="${ys.at(-1)/width}" preserveAspectRatio="none"/></g></svg>`;
  const name=String(person.name).replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;');
  return `<div class="citizen-portrait ${cls}" style="--citizen-aspect:1 / ${h}" data-person="${person.id}" data-age="${f.age}" data-outfit="${f.outfit}" role="img" aria-label="${name}, ${f.age} лет">${layer}${labels}</div>`;
}
let portraitSequence=0;
