// Gross payouts include the original stake. Every game has a visible house edge.
export const casinoGames = [
  {id:'roulette',name:'Рулетка: красное',chance:'18 из 37 · 48,6%',payout:'×1,9',description:'Шарик может упасть на зеро. Тогда ставка уходит дому.',asset:'roulette'},
  {id:'slots',name:'Автоматы',chance:'Пара 27% · джекпот 4,5%',payout:'×2 или ×8',description:'Три барабана. Красивый звук не означает прибыль.',asset:'slots'},
  {id:'cards',name:'Старшая карта',chance:'78 из 169 · 46,2%',payout:'×2',description:'Твоя карта должна быть старше карты крупье. Ничья за домом.',asset:'cards'}
];

export function casinoLimit(state) {
  const assets=Math.max(0,state.money)+Object.values(state.businesses||{}).length*150000;
  return Math.max(3000,Math.min(1000000,Math.round(assets*.15)));
}

export function casinoRemaining(state) {
  const today=state.casinoDaily?.day===state.day?state.casinoDaily:null;
  return Math.max(0,(today?.limit||casinoLimit(state))-(today?.wagered||0));
}

export function casinoOutcome(id,rng) {
  if(id==='roulette') {
    const pocket=Math.floor(rng()*37);
    const red=[1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36].includes(pocket);
    return {gross:red?1.9:0,result:pocket===0?'Зеро':`${pocket} · ${red?'красное':'чёрное'}`};
  }
  if(id==='slots') {
    const roll=rng();
    return roll<.045?{gross:8,result:'Три короны · джекпот'}:roll<.315?{gross:2,result:'Два символа · приз'}:{gross:0,result:'Барабаны не совпали'};
  }
  if(id==='cards') {
    const yours=1+Math.floor(rng()*13),dealer=1+Math.floor(rng()*13);
    const label=n=>({1:'Т',11:'В',12:'Д',13:'К'})[n]||String(n);
    return {gross:yours>dealer?2:0,result:`Твоя ${label(yours)} · крупье ${label(dealer)}`};
  }
  return null;
}
