import { backgrounds,heroImage,heroAgeLayers,transport,props } from '../assets/manifest.js';
import { ageOf } from '../systems/lifestyle.js';
import { ageDecade } from '../systems/appearance.js';

export function artScene(district,state) {
  const vehicle=transport[state.vehicle];
  return `<div class="world-art" data-district="${district.id}">
    <img class="world-bg" src="${backgrounds[district.id]}" alt="" draggable="false">
    <div class="world-atmosphere"></div>
    ${district.id==='yard'||district.id==='market'?`<img class="world-prop world-kiosk" src="${props.kiosk}" alt="" draggable="false">`:''}
    ${district.id==='yard'||district.id==='industrial'||district.id==='center'?`<img class="world-prop world-streetlamp" src="${props.streetlamp}" alt="" draggable="false">`:''}
    ${district.id==='market'?`<img class="world-prop world-bus-stop" src="${props.busStop}" alt="" draggable="false">`:''}
    ${vehicle?`<img class="world-vehicle" src="${vehicle}" alt="${state.vehicle}" draggable="false">`:''}
    <img class="world-hero" src="${heroImage(state)}" alt="Герой, ${ageOf(state)} лет" draggable="false">
    ${heroAgeLayers(state).map(layer=>`<img class="world-hero world-hero-age age-decade-${ageDecade(ageOf(state))}" style="--age-opacity:${layer.opacity.toFixed(3)}" src="${layer.image}" alt="" aria-hidden="true" draggable="false">`).join('')}
    <div class="world-ground"></div>
  </div>`;
}
