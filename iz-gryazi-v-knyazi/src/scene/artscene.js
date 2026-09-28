import { backgrounds,characters,heroStage,transport,props } from '../assets/manifest.js';

export function artScene(district,state) {
  const stage=heroStage(state);
  const vehicle=transport[state.vehicle];
  return `<div class="world-art" data-district="${district.id}">
    <img class="world-bg" src="${backgrounds[district.id]}" alt="" draggable="false">
    <div class="world-atmosphere"></div>
    ${district.id==='yard'||district.id==='market'?`<img class="world-prop world-kiosk" src="${props.kiosk}" alt="" draggable="false">`:''}
    ${district.id==='yard'||district.id==='industrial'||district.id==='center'?`<img class="world-prop world-streetlamp" src="${props.streetlamp}" alt="" draggable="false">`:''}
    ${vehicle?`<img class="world-vehicle" src="${vehicle}" alt="${state.vehicle}" draggable="false">`:''}
    <img class="world-hero" src="${characters[stage]}" alt="Герой" draggable="false">
    <div class="world-ground"></div>
  </div>`;
}
