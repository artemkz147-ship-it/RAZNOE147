import { homes } from '../data/world.js';
import { addLog,addLedger } from './state.js';
export const homeTerms=id=>{
  const h=homes.find(x=>x.id===id)||homes[0];
  const kind=['station','heating-main'].includes(id)?'street':id==='sofa'?'friend':id==='hostel'?'daily':['room','flat'].includes(id)?'rent':'owned';
  return {kind,district:{sofa:'yard',station:'market','heating-main':'yard',hostel:'market',room:'yard',flat:'center',loft:'center',duplex:'glass',penthouse:'heights',estate:'heights'}[id]||'yard',amount:kind==='rent'||kind==='owned'?h.daily*30:h.daily,period:kind==='rent'||kind==='owned'?30:1};
};
export function ensureHousing(s){
  s.propertyAccounts||={};
  if(!s.housing||s.housing.id!==s.home)s.housing={id:s.home,since:s.day,nextDue:s.day+homeTerms(s.home).period,multiplier:1};
  if(s.home!=='sofa')s.sofaAbsentSince??=s.housing.since;
  if(homeTerms(s.home).kind==='owned'){
    s.propertyAccounts[s.home]||=s.housing;
    s.housing=s.propertyAccounts[s.home];
  }
  if(s.home==='sofa'){
    s.sofaSince??=s.housing.since;
    s.housing.since=Math.min(s.housing.since,s.sofaSince);
    s.housing.grace=Math.min(14,Math.max(0,s.housing.grace||0));
  }
  return s.housing;
}
export function grantSofaGrace(s,days=7){
  if(s.home!=='sofa')return false;
  const account=ensureHousing(s),remaining=14-(account.grace||0);
  if(remaining<=0)return false;
  account.grace=(account.grace||0)+Math.min(remaining,Math.max(0,days));return true;
}
export const housingBill=s=>Math.round(homeTerms(s.home).amount*(ensureHousing(s).multiplier||1));
export function housingAccounts(s){
  const current=ensureHousing(s),accounts=homeTerms(s.home).kind==='owned'?[]:[current];
  for(const id of s.ownedHomes.filter(id=>homeTerms(id).kind==='owned')){
    s.propertyAccounts[id]||={id,since:s.day,nextDue:s.day+30,multiplier:1};
    accounts.push(s.propertyAccounts[id]);
  }
  return accounts;
}
export function pauseHousingBills(s){for(const account of housingAccounts(s))account.nextDue++;}
export function housingBudget(s){
  const accounts=housingAccounts(s).filter(a=>homeTerms(a.id).amount),nextDue=accounts.length?Math.min(...accounts.map(a=>a.nextDue)):null;
  const bill=a=>Math.round(homeTerms(a.id).amount*(a.multiplier||1));
  return {nextDue,rent:accounts.filter(a=>a.nextDue===nextDue).reduce((n,a)=>n+bill(a),0),daily:accounts.reduce((n,a)=>n+bill(a)/homeTerms(a.id).period,0)};
}
export function moveHome(s,id){
  if(s.home===id){ensureHousing(s);return;}
  housingAccounts(s);
  if(s.home==='sofa')s.sofaAbsentSince=s.day;
  if(id==='sofa'&&s.day>=(s.sofaBlockedUntil||0)&&s.day-(s.sofaAbsentSince??s.day)>=30)s.sofaSince=s.day;
  if(s.home!==id&&['hostel','room','flat'].includes(s.home))s.ownedHomes=s.ownedHomes.filter(h=>h!==s.home);
  s.home=id;s.housing={id,since:id==='sofa'?(s.sofaSince||s.day):s.day,nextDue:s.day+homeTerms(id).period,multiplier:1};
  ensureHousing(s);
  s.district=homeTerms(id).district;s.visitedDistricts||=['yard'];if(!s.visitedDistricts.includes(s.district))s.visitedDistricts.push(s.district);
}
export function loseHousing(s,reason){
  if(s.activeSkip)s.activeSkip.interruption='Потерян ночлег: '+reason;
  if(s.home==='sofa'){s.sofaBlockedUntil=s.day+30;s.sofaSince=s.day;}
  const wasSofa=s.home==='sofa',former=homes.find(x=>x.id===s.home)?.name||s.home;
  s.ownedHomes=s.ownedHomes.filter(x=>!['hostel','room','flat'].includes(x));
  if(!s.ownedHomes.includes('station'))s.ownedHomes.push('station');
  moveHome(s,'station');
  const text=`${former}: ${reason} Пришлось уйти на вокзал.`;
  addLog(s,text,'bad');if(!s.recentIncident)s.recentIncident={title:'Нужно искать ночлег',text,art:3,scene:wasSofa?'eviction':undefined,interruptSkip:true};
}
function currentHousingDay(s,rng){
  const account=ensureHousing(s),terms=homeTerms(s.home);
  if(terms.kind==='friend'){
    const score=s.social?.sergey?.score||0,days=s.day-account.since,limit=45+(account.grace||0);
    if(s.population?.departed?.sergey||score<=-20||days>=limit){
      loseHousing(s,score<=-20?'Серёга больше не хочет тебя принимать.':'Серёга попросил освободить диван: бесплатная помощь не была навсегда.');
    }else if(days>=25&&days%10===0&&(account.grace||0)<14&&!s.recentIncident)s.recentIncident={title:'Разговор о диване',text:'Серёга устал делить комнату. Нужно обсудить, сколько ты ещё останешься.',image:'person:sergey',socialId:'sergey',choices:[{text:'Помочь с бытом и договориться ещё на неделю',effect:{energy:-12},socialDelta:4,housingGrace:7,reply:'Ты помог с домашними делами. Серёга согласился дать ещё неделю, но попросил искать своё место.'},{text:'Сказать, что тебе все должны',socialDelta:-25,evict:true,reply:'Серёга предложил искать другой ночлег.'}]};
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
export function housingDay(s,rng){
  const accounts=housingAccounts(s);
  let total=homeTerms(s.home).kind==='owned'?0:currentHousingDay(s,rng);
  for(const account of accounts.filter(a=>homeTerms(a.id).kind==='owned')){
    while(s.day>=account.nextDue){
      const bill=Math.round(homeTerms(account.id).amount*(account.multiplier||1));
      s.money-=bill;total+=bill;account.nextDue+=30;
      const name=homes.find(h=>h.id===account.id).name;
      addLedger(s,'Содержание дома',-bill,name);
      addLog(s,`${name}: содержание ${bill} ₽. Следующий платёж — день ${account.nextDue}.`,'neutral');
    }
  }
  return total;
}

