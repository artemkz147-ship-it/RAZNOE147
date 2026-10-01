import {housingDay,pauseHousingBills,homeTerms} from './housing.js';
import { homes, vehicles, businesses, districts } from '../data/world.js';
import { investments } from '../data/investments.js';
import { clamp, adjust, addLog, addLedger } from './state.js';
import { diets } from '../data/lifestyle.js';
import { dailyVitals } from './lifestyle.js';

export const byId = (items,id) => items.find(item => item.id === id);
export function netWorth(state) {
  const home = state.ownedHomes.reduce((sum,id)=>sum+(homeTerms(id).kind==='owned'?(byId(homes,id)?.price||0):0),0);
  const fleet = state.ownedVehicles.reduce((sum,id)=>sum+(byId(vehicles,id)?.price||0),0);
  const firms = Object.entries(state.businesses).reduce((sum,[id,b])=>sum+(byId(businesses,id)?.price||0)*(1+.45*(b.level-1)),0);
  const invested=(state.investments||[]).reduce((sum,item)=>sum+item.amount,0);
  return Math.round(state.money + home + fleet + firms + invested - state.debt);
}
export function travelPrice(state,districtId) {
  return travelOptions(state,districtId).find(option=>option.id==='bus')?.cost||0;
}
export function travelOptions(state,districtId) {
  const from=byId(districts,state.district),to=byId(districts,districtId);
  if(!from||!to||from.id===to.id)return [];
  const distance=Math.abs(to.tier-from.tier);
  const fare=35+distance*30;
  const walkRisk=Math.min(.3,.025+distance*.018+(100-(state.conditions?.shoes??100))*.0014);
  const badShoes=(state.conditions?.shoes??100)<20;
  const options=[
    {id:'walk',name:'Пешком',cost:0,hours:distance*2+1+(badShoes?1:0),energy:8+distance*6+(badShoes?4:0),risk:walkRisk,detail:badShoes?'Бесплатно, но рваная обувь замедляет путь и повышает риск.':'Бесплатно, долго. Обувь изнашивается.'},
    {id:'bus',name:'Автобус · билет',cost:fare,hours:Math.max(1,distance),energy:3,risk:.06,detail:'Без штрафа. Возможны обычные дорожные задержки.'},
    {id:'fare-dodge',name:'Автобус · зайцем',cost:0,hours:Math.max(1,distance),energy:3,risk:Math.min(.42,.14+distance*.035),fine:fare*10,detail:'Если поймают — штраф ×10; нет денег — арест.'},
    {id:'taxi',name:'Такси',cost:fare*4,hours:1,energy:1,risk:.025,detail:'Дорого, зато до двери. Тариф известен заранее.'}
  ];
  if(state.vehicle!=='feet'&&!state.vehicleFaults?.[state.vehicle]) {
    const vehicle=byId(vehicles,state.vehicle);
    options.push({id:'own',name:vehicle.name,cost:Math.max(10,Math.ceil(fare*vehicle.fareFactor)),hours:Math.max(1,Math.ceil(distance*vehicle.timeFactor)),energy:Math.max(2,Math.ceil(4*vehicle.timeFactor)),risk:Math.min(.16,.018+distance*.008),detail:'Топливо и износ за поездку.'});
  }
  return options;
}
export function districtUnlocked(state,district) {
  const r = district.required;
  if(state.visitedDistricts?.includes(district.id))return true;
  if(!r.respect&&!r.wealth&&!r.contacts)return true;
  return (r.respect&&state.stats.respect>=r.respect)||(r.wealth&&netWorth(state)>=r.wealth)||(r.contacts&&state.stats.contacts>=r.contacts);
}
export function skillLevel(state,skill) { return state.skills[skill] || 1; }
export function gainSkill(state,skill,amount=1) {
  state.xp[skill] += amount;
  const needed = state.skills[skill]*5;
  if (state.xp[skill] >= needed && state.skills[skill] < 12) {
    state.xp[skill] -= needed;
    state.skills[skill]++;
    addLog(state,`Навык «${{grit:'Хватка',charm:'Обаяние',focus:'Смекалка'}[skill]}» вырос до ${state.skills[skill]}.`,'good');
    return true;
  }
  return false;
}
export const businessStrategies={
  safe:{label:'Бережно',income:.85,expense:1,risk:.55,wear:0},
  normal:{label:'Обычно',income:1,expense:1,risk:1,wear:1},
  growth:{label:'На износ',income:1.28,expense:1.1,risk:1.9,wear:3}
};
export function businessForecast(state,config,firm={level:1,condition:100,staff:false}){
 const strategy=businessStrategies[firm.strategy]||businessStrategies.normal;
 const boost=1+state.stats.business*.003+state.stats.fame*.0008+state.stats.contacts*.001;
 const leadership=state.flags.includes('fairboss')?1.08:state.flags.includes('hardboss')?1.05:1;
 const base=config.base*firm.level*(firm.staff?1.32:1)*[.75,1,1.16,1.38][state.market]*boost*Math.max(.38,firm.condition/100)*leadership*strategy.income*(firm.capacity??1)*(firm.orderUntil>=state.day?1.2:1);
 const expense=Math.round((config.upkeep*(1+.38*(firm.level-1))+(firm.staff?config.staff:0))*(state.flags.includes('supplier')?.95:1)*strategy.expense*(firm.expenseFactor||1));
 const low=firm.paused?0:Math.round(base*.55),high=firm.paused?0:Math.round(base*1.45);
 return {low,high,expense,netLow:low-expense,netHigh:high-expense};
}
export function dailyBusiness(state,offline=false,rng=Math.random){
 let result=0;
 for(const [id,firm] of Object.entries(state.businesses)){
  const config=byId(businesses,id);if(!config)continue;
  const f=businessForecast(state,config,firm),gross=Math.round(f.low+rng()*(f.high-f.low)),net=gross-f.expense;
  if(state.money+result+gross<f.expense){delete state.businesses[id];addLedger(state,'Ликвидация бизнеса',0,config.name+': не хватает средств на обязательства');if(!state.recentIncident)state.recentIncident={title:'Дело закрыто',text:config.name+': не хватило денег на оплату обязательств. Выручка возвращена покупателям.',art:11};addLog(state,config.name+': закрытие из-за неплатёжеспособности.','bad');continue;}
  result+=net;firm.condition=clamp(firm.condition-(businessStrategies[firm.strategy]||businessStrategies.normal).wear,20,100);
  firm.lastResult={day:state.day,gross,expense:f.expense,net};addLedger(state,'Бизнес: '+config.name,net,'Выручка '+gross+' ₽, расходы '+f.expense+' ₽');
 }
 return result;
}
export function dailySettlement(state,{offline=false,rng=Math.random,skipDiet=false,prison=false}={}) {
  let home = byId(homes,state.home);
  const vehicle = byId(vehicles,state.vehicle);
  if(prison){
    pauseHousingBills(state);if(state.employment){state.employment.nextPay++;state.employment.reviewDay++;}
    const savedDiet=state.diet;state.diet='expired';dailyVitals(state,rng,{fed:true,prison:true});state.diet=savedDiet;
    state.lastSettlement={day:state.day,food:0,rent:0,transport:0,business:0,expenses:0,prison:true};
    return {business:0,expenses:0};
  }
  state.market = Math.floor(rng()*4);
  const business = dailyBusiness(state,offline,rng);
  const food=skipDiet?(byId(diets,state.diet)||diets[1]).daily:0;
  state.money+=business;
  const rent=housingDay(state,rng);
  home=byId(homes,state.home);
  const otherExpenses = vehicle.upkeep + food + Math.floor(state.debt*.018);
  const expenses=rent+otherExpenses;
  state.money -= otherExpenses;
  if(food)addLedger(state,'Питание',-food,(byId(diets,state.diet)||diets[1]).name);

  if(vehicle.upkeep)addLedger(state,'Транспорт',-vehicle.upkeep,vehicle.name);
  if(state.debt)addLedger(state,'Долг',-Math.floor(state.debt*.018),'Проценты');
  state.debt += Math.ceil(state.debt*.012);
  state.heat = Math.max(0,(state.heat||0)-4);
  if(state.conditions) {state.conditions.back=Math.max(0,state.conditions.back-1);state.conditions.hangover=Math.max(0,state.conditions.hangover-1);}
  state.stats.life = clamp(Math.round(8+home.prestige*.55+vehicle.prestige*.16+state.stats.mood*.1-state.stats.stress*.06),0,100);
  adjust(state,{energy:Math.round(home.restore*.43),health:state.money<0?-4:1,mood:state.money<0?-3:state.stats.life>55?1:0,stress:state.money<0?5:state.stats.life>55?0:1});
  state.lastSettlement={day:state.day,food,rent,transport:vehicle.upkeep,business,expenses};
  dailyVitals(state,rng,{fed:skipDiet||state.lastMealDay===state.day-1});
  if(state.death)return {business,expenses};
  if (business) addLog(state,`День ${state.day}: дела принесли ${business.toLocaleString('ru-RU')} ₽; быт и обязательства забрали ${expenses.toLocaleString('ru-RU')} ₽.`,business>=expenses?'good':'bad');
  else if(expenses)addLog(state,`День ${state.day}: питание ${food} ₽, жильё ${rent} ₽, транспорт ${vehicle.upkeep} ₽${state.debt?', долг и проценты учтены':''}. Всего ${expenses.toLocaleString('ru-RU')} ₽.`,'neutral');
  if (state.money < -12000) {
    const transferred=-12000-state.money;
    state.debt += transferred; state.money = -12000;
    addLedger(state,'Перенос в долг',transferred,'Часть минуса переведена в отдельный долг');
    addLog(state,'Банк молча превратил минус на счёте в долг. Уведомление было удивительно вежливым.','bad');
  }
  return {business,expenses};
}
export function settleMatureInvestments(state,rng=Math.random) {
  state.investments ||= [];
  for (const item of state.investments.filter(x=>x.maturity<=state.day)) {
    const config=byId(investments,item.id);
    if (!config) continue;
    const payout=Math.round(item.amount*(config.low+rng()*(config.high-config.low)));
    state.money+=payout;
    addLedger(state,'Вложения',payout,config.name+': возврат капитала и результат');
    if(payout>item.amount)state.totalEarned+=payout-item.amount;
    addLog(state,`${config.name}: вернулось ${payout.toLocaleString('ru-RU')} ₽ (${payout>=item.amount?'+':''}${(payout-item.amount).toLocaleString('ru-RU')} ₽).`,payout>=item.amount?'good':'bad');
  }
  state.investments=state.investments.filter(x=>x.maturity>state.day);
}
