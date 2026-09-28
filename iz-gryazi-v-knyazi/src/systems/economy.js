import { homes, vehicles, businesses, districts } from '../data/world.js';
import { investments } from '../data/investments.js';
import { clamp, adjust, addLog } from './state.js';

export const byId = (items,id) => items.find(item => item.id === id);
export function netWorth(state) {
  const home = state.ownedHomes.reduce((sum,id)=>sum+(byId(homes,id)?.price||0),0);
  const fleet = state.ownedVehicles.reduce((sum,id)=>sum+(byId(vehicles,id)?.price||0),0);
  const firms = Object.entries(state.businesses).reduce((sum,[id,b])=>sum+(byId(businesses,id)?.price||0)*(1+.45*(b.level-1)),0);
  return Math.round(state.money + home + fleet + firms - state.debt);
}
export function travelPrice(state,districtId) {
  const district = byId(districts,districtId);
  const vehicle = byId(vehicles,state.vehicle);
  return Math.ceil((district?.fare||0)*vehicle.fareFactor);
}
export function districtUnlocked(state,district) {
  const r = district.required;
  return (!r.story || state.story >= r.story) && (!r.respect || state.stats.respect >= r.respect);
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
export function dailyBusiness(state,offline=false,rng=Math.random) {
  let result = 0;
  const marketMultiplier = [.75,1,1.16,1.38][state.market];
  for (const [id,firm] of Object.entries(state.businesses)) {
    const config = byId(businesses,id);
    if (!config) continue;
    const relationBoost = 1 + state.stats.business*.003 + state.stats.fame*.0008 + state.stats.contacts*.001;
    const condition = Math.max(.38,firm.condition/100);
    const leadership = state.flags.includes('fairboss')?1.08:state.flags.includes('hardboss')?1.05:1;
    const strategy=businessStrategies[firm.strategy]||businessStrategies.normal;
    const gross = Math.round(config.base*firm.level*(firm.staff ? 1.32 : 1)*marketMultiplier*relationBoost*condition*leadership*strategy.income);
    const supplierDiscount = state.flags.includes('supplier')?.95:1;
    const expense = Math.round((config.upkeep*(1+.38*(firm.level-1)) + (firm.staff ? config.staff : 0))*supplierDiscount*strategy.expense);
    let net = gross-expense;
    const riskFactor = state.flags.includes('openbooks')?.7:state.flags.includes('closedbooks')?1.45:1;
    const crimeFactor = 1+state.stats.crime*.006;
    if (!offline && rng() < config.risk*.15*riskFactor*crimeFactor*strategy.risk) {
      const repair = Math.round(config.upkeep*(1+firm.level*.6));
      net -= repair; firm.condition = clamp(firm.condition-12,20,100);
      addLog(state,`${config.name}: внеплановый ремонт обошёлся в ${repair.toLocaleString('ru-RU')} ₽.`,'bad');
    } else firm.condition = clamp(firm.condition-strategy.wear,20,100);
    result += net;
  }
  if (offline) result = Math.floor(result*.62);
  return result;
}
export function dailySettlement(state,{offline=false,rng=Math.random}={}) {
  const home = byId(homes,state.home);
  const vehicle = byId(vehicles,state.vehicle);
  state.market = Math.floor(rng()*4);
  const business = dailyBusiness(state,offline,rng);
  const expenses = home.daily + vehicle.upkeep + 110 + Math.floor(state.debt*.018);
  state.money += business-expenses;
  state.debt += Math.ceil(state.debt*.012);
  state.heat = Math.max(0,(state.heat||0)-4);
  state.stats.life = clamp(Math.round(8+home.prestige*.55+vehicle.prestige*.16+state.stats.mood*.1-state.stats.stress*.06),0,100);
  adjust(state,{energy:Math.round(home.restore*.43),health:state.money<0?-4:1,mood:state.money<0?-3:state.stats.life>55?1:0,stress:state.money<0?5:state.stats.life>55?0:1});
  if (business) addLog(state,`День ${state.day}: дела принесли ${business.toLocaleString('ru-RU')} ₽; быт и обязательства забрали ${expenses.toLocaleString('ru-RU')} ₽.`,business>=expenses?'good':'bad');
  else addLog(state,`День ${state.day}: бытовые расходы ${expenses.toLocaleString('ru-RU')} ₽.`,'neutral');
  if (state.money < -12000) {
    state.debt += Math.abs(state.money)+12000; state.money = -12000;
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
    if(payout>item.amount)state.totalEarned+=payout-item.amount;
    addLog(state,`${config.name}: вернулось ${payout.toLocaleString('ru-RU')} ₽ (${payout>=item.amount?'+':''}${(payout-item.amount).toLocaleString('ru-RU')} ₽).`,payout>=item.amount?'good':'bad');
  }
  state.investments=state.investments.filter(x=>x.maturity>state.day);
}
