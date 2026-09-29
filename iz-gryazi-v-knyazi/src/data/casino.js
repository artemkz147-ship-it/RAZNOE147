export const casinoGames = [
  {id:'roulette',name:'Рулетка',chance:'Красное 18/37 · число 1/37',payout:'×2 или ×36',description:'Выбери цвет, чётность или конкретное число. Зеро забирает обычные ставки.',asset:'roulette'},
  {id:'blackjack',name:'21 · Блэкджек',chance:'Три игрока и крупье',payout:'×2 · натуральное 21 ×2,5',description:'Вадим, Лера и Артур сидят рядом с тобой. Крупье играет против каждого. Бери карту, стой или удваивай.',asset:'cards'},
  {id:'poker',name:'Покер · пять карт',chance:'Три соперника за столом',payout:'Банк до ×8',description:'Артур, Лера и Вадим разыгрывают общий банк. Обмени карты, откройся, повысь или сбрось.',asset:'cards'}
];

export const casinoOpponents={
  blackjack:[
    {id:'vadim',name:'Вадим',portrait:'valera',style:'Осторожный · стоит на 16',hold:16},
    {id:'lera',name:'Лера',portrait:'lida',style:'Рисковая · добирает до 18',hold:18},
    {id:'artur',name:'Артур',portrait:'artur',style:'Хладнокровный · стоит на 17',hold:17}
  ],
  poker:[
    {id:'artur',name:'Артур',portrait:'artur',style:'Осторожный · ценит пару',call:0.12},
    {id:'lera',name:'Лера',portrait:'lida',style:'Смелая · часто отвечает',call:0.68},
    {id:'vadim',name:'Вадим',portrait:'valera',style:'Читает стол · иногда блефует',call:0.34}
  ]
};

export function casinoLimit(state) {
  const assets=Math.max(0,state.money)+Object.values(state.businesses||{}).length*150000;
  return Math.max(3000,Math.min(100000000,Math.round(assets*.8)));
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
