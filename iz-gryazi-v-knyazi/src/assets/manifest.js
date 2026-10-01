import { ageAppearance } from '../systems/appearance.js';
import { netWorth } from '../systems/economy.js';
const file=path=>new URL(path,import.meta.url).href;
export const backgrounds={yard:file('./backgrounds/yard.webp'),market:file('./backgrounds/market.webp'),industrial:file('./backgrounds/industrial.webp'),center:file('./backgrounds/center.webp'),glass:file('./backgrounds/glass.webp'),heights:file('./backgrounds/heights.webp')};
export const shelterArt={station:file('./backgrounds/station-shelter.png'),heating:file('./backgrounds/heating-shelter.png')};
export const endingArt={street:file('./endings/street.png'),hospital:file('./endings/hospital.png'),alley:file('./endings/alley.png'),home:file('./endings/home.png'),prison:file('./endings/prison.png'),luxury:file('./endings/luxury.png')};
export const eventArt={...Object.fromEntries(['found-phone','workshop','office-review','food-counter','hostel-desk','complaint','laundry','parcel-counter','friend-meal','checkout'].map(key=>[key,file('./events/'+key+'.jpg')])),cold:endingArt.street,hospital:endingArt.hospital,eviction:file('./events/eviction.png'),back:file('./events/back.png'),bike:file('./events/bike.png'),mugging:file('./events/mugging.png')};
export const characters={poor:file('./characters/hero-poor.webp'),worker:file('./characters/hero-worker.webp'),jacket:file('./characters/hero-jacket.webp'),middle:file('./characters/hero-middle.webp'),suit:file('./characters/hero-suit.webp'),magnate:file('./characters/hero-magnate.webp'),rich:file('./characters/hero-rich.webp')};
export const olderCharacters={worker:file('./characters/hero-worker-aged.png'),suit:file('./characters/hero-suit-aged.png')};
export const heroStages=['poor','worker','jacket','middle','suit','magnate','rich'];
export const heroStage=state=>{
  const wealth=typeof state==='number'?state:netWorth(state);
  const wardrobe=['clean-shirt','work-jacket','leather-jacket','office-suit','tailored-suit','cashmere-coat'];
  const owned=typeof state==='number'?[]:state.upgrades||[];
  const clothing=wardrobe.reduce((stage,id,i)=>owned.includes(id)?Math.max(stage,i+1):stage,0);
  const wealthStage=wealth>=20000000?6:wealth>=4000000?5:wealth>=600000?4:wealth>=90000?3:wealth>=15000?2:wealth>=3500?1:0;
  return heroStages[Math.min(heroStages.length-1,clothing||wealthStage)];
};
export const heroImage=state=>{
  return characters[heroStage(state)];
};
export const heroAgePortraits={40:file('./characters/hero-age-40.png'),50:file('./characters/hero-age-50.png'),60:file('./characters/hero-age-60.png'),70:file('./characters/hero-age-70.png'),80:file('./characters/hero-age-80.png'),90:file('./characters/hero-age-90.png')};
export const heroAgeLayers=state=>{
  const age=30+Math.floor(((state.day||1)-1)/360);
  const {decade,next,blend}=ageAppearance(age);
  return [{image:heroAgePortraits[decade],opacity:1},{image:heroAgePortraits[next],opacity:blend}].filter(x=>x.image&&x.opacity>0);
};
export const transport={bike:file('./vehicles/bike.webp'),moped:file('./vehicles/moped.webp'),lada:file('./vehicles/lada.webp'),sedan:file('./vehicles/sedan.webp'),suv:file('./vehicles/suv.webp'),limousine:file('./vehicles/luxury.webp')};
export const props={kiosk:file('./props/kiosk.webp'),streetlamp:file('./props/streetlamp.webp'),busStop:file('./props/bus-stop.png')};
export const portraits={valera:file('./people/valera.webp'),tamara:file('./people/tamara.webp'),azamat:file('./people/azamat.webp'),rosa:file('./people/rosa.webp'),lida:file('./people/lida.webp'),pasha:file('./people/pasha.webp'),vera:file('./people/vera.webp'),artur:file('./people/artur.webp'),minister:file('./people/minister.webp'),zoya:file('./people/minister.webp')};
export const items={boots:file('./items/boots.webp'),phone:file('./items/phone.webp'),jacket:file('./items/jacket.webp'),course:file('./items/course.webp'),trainer:file('./items/trainer.webp'),assistant:file('./items/assistant.webp')};
export const casinoArt={floor:file('./casino/casino-floor.webp'),roulette:file('./casino/roulette.webp'),slots:file('./casino/slot-machine.webp'),cards:file('./casino/cards.webp')};
