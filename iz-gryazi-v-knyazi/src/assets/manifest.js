const file=path=>new URL(path,import.meta.url).href;
export const backgrounds={yard:file('./backgrounds/yard.webp'),market:file('./backgrounds/market.webp'),industrial:file('./backgrounds/industrial.webp'),center:file('./backgrounds/center.webp'),glass:file('./backgrounds/glass.webp'),heights:file('./backgrounds/heights.webp')};
export const characters={poor:file('./characters/hero-poor.webp'),worker:file('./characters/hero-worker.webp'),jacket:file('./characters/hero-jacket.webp'),middle:file('./characters/hero-middle.webp'),suit:file('./characters/hero-suit.webp'),magnate:file('./characters/hero-magnate.webp'),rich:file('./characters/hero-rich.webp')};
export const heroStages=['poor','worker','jacket','middle','suit','magnate','rich'];
export const heroStage=state=>{
  const story=typeof state==='number'?state:state.story||0;
  const wardrobe=['clean-shirt','work-jacket','leather-jacket','office-suit','tailored-suit','cashmere-coat'];
  const owned=typeof state==='number'?[]:state.upgrades||[];
  const clothing=wardrobe.reduce((stage,id,i)=>owned.includes(id)?Math.max(stage,i+1):stage,0);
  return heroStages[Math.min(heroStages.length-1,Math.max(Math.floor(Math.max(0,story)/2),clothing))];
};
export const transport={bike:file('./vehicles/bike.webp'),moped:file('./vehicles/moped.webp'),lada:file('./vehicles/lada.webp'),sedan:file('./vehicles/sedan.webp'),suv:file('./vehicles/suv.webp'),limousine:file('./vehicles/luxury.webp')};
export const props={kiosk:file('./props/kiosk.webp'),streetlamp:file('./props/streetlamp.webp')};
export const portraits={valera:file('./people/valera.webp'),tamara:file('./people/tamara.webp'),azamat:file('./people/azamat.webp'),rosa:file('./people/rosa.webp'),lida:file('./people/lida.webp'),pasha:file('./people/pasha.webp'),vera:file('./people/vera.webp'),artur:file('./people/artur.webp'),minister:file('./people/minister.webp'),zoya:file('./people/minister.webp')};
export const items={boots:file('./items/boots.webp'),phone:file('./items/phone.webp'),jacket:file('./items/jacket.webp'),course:file('./items/course.webp'),trainer:file('./items/trainer.webp'),assistant:file('./items/assistant.webp')};
export const casinoArt={floor:file('./casino/casino-floor.webp'),roulette:file('./casino/roulette.webp'),slots:file('./casino/slot-machine.webp'),cards:file('./casino/cards.webp')};
