import { homes } from '../data/world.js';
import { addLog,addLedger } from './state.js';
export const homeTerms=id=>{
  const h=homes.find(x=>x.id===id)||homes[0];
  const kind=['station','heating-main'].includes(id)?'street':id==='sofa'?'friend':id==='hostel'?'daily':['room','flat'].includes(id)?'rent':'owned';
  return {kind,district:{sofa:'yard',station:'market','heating-main':'yard',hostel:'market',room:'yard',flat:'center',loft:'center',duplex:'glass',penthouse:'heights',estate:'heights'}[id]||'yard',amount:kind==='rent'||kind==='owned'?h.daily*30:h.daily,period:kind==='rent'||kind==='owned'?30:1};
};
export function ensureHousing(s){
  if(!s.housing||s.housing.id!==s.home)s.housing={id:s.home,since:s.day,nextDue:s.day+homeTerms(s.home).period,multiplier:1};
  if(s.home==='sofa')s.sofaSince??=s.housing.since;
  return s.housing;
}
export const housingBill=s=>Math.round(homeTerms(s.home).amount*(ensureHousing(s).multiplier||1));
export function moveHome(s,id){
  if(s.home!==id&&['hostel','room','flat'].includes(s.home))s.ownedHomes=s.ownedHomes.filter(h=>h!==s.home);
  s.home=id;s.housing={id,since:id==='sofa'?(s.sofaSince||s.day):s.day,nextDue:s.day+homeTerms(id).period,multiplier:1};
  s.district=homeTerms(id).district;s.visitedDistricts||=['yard'];if(!s.visitedDistricts.includes(s.district))s.visitedDistricts.push(s.district);
}
export function loseHousing(s,reason){
  if(s.home==='sofa'){s.sofaBlockedUntil=s.day+30;s.sofaSince=s.day;}
  const former=homes.find(x=>x.id===s.home)?.name||s.home;
  s.ownedHomes=s.ownedHomes.filter(x=>!['hostel','room','flat'].includes(x));
  if(!s.ownedHomes.includes('station'))s.ownedHomes.push('station');
  moveHome(s,'station');
  const text=`${former}: ${reason} Пришлось уйти на вокзал.`;
  addLog(s,text,'bad');if(!s.recentIncident)s.recentIncident={title:'Нужно искать ночлег',text,art:3};
}
export function housingDay(s,rng){
  const account=ensureHousing(s),terms=homeTerms(s.home);
  if(terms.kind==='friend'){
    const score=s.social?.valera?.score||0,days=s.day-account.since;
    if(s.population?.departed?.valera||score<=-20||days>45&&rng()<Math.min(.5,(days-45)*.008)){
      loseHousing(s,score<=-20?'Валера больше не хочет тебя принимать.':'Валера попросил освободить диван: бесплатная помощь не была навсегда.');
    }else if(days>=25&&days%10===0&&!s.recentIncident)s.recentIncident={title:'Разговор о диване',text:'Валера устал делить комнату. Нужно обсудить, сколько ты ещё останешься.',image:'person:valera',socialId:'valera',choices:[{text:'Помочь с бытом и договориться ещё на неделю',effect:{energy:-12},socialDelta:4,housingGrace:7,reply:'Ты убрал комнату и помог в гараже. Валера согласился подождать.'},{text:'Сказать, что тебе все должны',socialDelta:-25,evict:true,reply:'Валера предложил искать другой ночлег.'}]};
    return 0;
  }
  if(!terms.amount||s.day<account.nextDue)return 0;
  const bill=housingBill(s);
  if(s.money<bill&&terms.kind!=='owned'){loseHousing(s,'На очередной платёж не хватило денег.');return 0;}
  s.money-=bill;account.nextDue+=terms.period;
  addLedger(s,terms.kind==='owned'?'Содержание дома':'Жильё',-bill,homes.find(x=>x.id===s.home).name);
  addLog(s,`Оплачено жильё: ${bill} ₽. Следующий платёж — день ${account.nextDue}.`,'neutral');
  return bill;
}
