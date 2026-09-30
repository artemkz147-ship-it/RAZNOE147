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
  const f=citizenFrames(state,person,age),h=f.atlas.cellHeight||1;
  // SVG selects exactly one atlas cell and keeps the face's proportions in
  // both narrow people cards and wide event illustrations.
  const layer=(column,opacity=1)=>`<svg class="citizen-skin" viewBox="0 0 1 ${h}" preserveAspectRatio="xMidYMin meet" style="opacity:${opacity}" aria-hidden="true"><svg width="1" height="${h}" viewBox="${column} ${f.row*h} 1 ${h}" overflow="hidden"><image href="${f.atlas.image}" width="${f.atlas.columns}" height="${f.atlas.rows*h}" preserveAspectRatio="none"/></svg></svg>`;
  const name=String(person.name).replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;');
  return `<div class="citizen-portrait ${cls}" style="--citizen-aspect:1 / ${h}" data-person="${person.id}" data-age="${f.age}" data-outfit="${f.outfit}" role="img" aria-label="${name}, ${f.age} лет">${layer(f.column)}${f.blend?layer(f.next,Number(f.blend.toFixed(3))):''}${labels}</div>`;
}
