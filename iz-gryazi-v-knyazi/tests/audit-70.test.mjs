import test from 'node:test';
import assert from 'node:assert/strict';
import {freshState} from '../src/systems/state.js';
import {GameEngine} from '../src/systems/engine.js';
import {netWorth,dailySettlement,settleMatureInvestments} from '../src/systems/economy.js';
import {endingFor,endLife} from '../src/systems/endings.js';
import {validate} from '../src/systems/save.js';
import {createCasinoTable} from '../src/systems/casinoTable.js';
import {crimes} from '../src/data/crime.js';
import {shell} from '../src/ui/views.js';
import {events} from '../src/data/events.js';
import {contextEvents} from '../src/data/contextEvents.js';
import {incidents} from '../src/data/incidents.js';
import {moveHome} from '../src/systems/housing.js';
globalThis.localStorage={setItem(){}};

test('refinancing only transfers the excess overdraft into debt',()=>{
 const s=freshState();s.money=-13000;
 dailySettlement(s,{rng:()=>.99});
 assert.equal(s.money,-12000);assert.equal(s.debt,1000);
 assert.equal(netWorth(s),-13000);
});
test('capital includes the principal of investments still awaiting maturity',()=>{
 const s=freshState();s.money=100000;const g=new GameEngine(s,()=>.99);
 assert.equal(g.invest('bonds',15000).ok,true);
 assert.equal(netWorth(s),100000);assert.equal(endingFor(s,'age').wealth,100000);
});
test('investment cashflows reconcile without an unexplained principal return',()=>{
 const s=freshState();s.money=100000;const g=new GameEngine(s,()=>.99);
 g.invest('bonds',15000);s.day=8;settleMatureInvestments(s,()=>0);g.emit({ok:true});
 assert.equal(s.money,101050);
 assert.deepEqual(s.ledger.filter(x=>x.category==='Вложения').map(x=>x.amount),[16050,-15000]);
 assert.equal(s.ledger.filter(x=>x.category==='Прочее').length,0);
});
test('a crime cannot start after a death with health still above zero',()=>{
 const s=freshState(),crime=crimes[0];s.district=crime.district;endLife(s,'age');
 const g=new GameEngine(s,()=>.99),before=JSON.stringify(s);
 assert.equal(g.crimeReady(crime.id).ok,false);assert.equal(g.completeCrime(crime.id,1).ok,false);
 assert.equal(s.money,JSON.parse(before).money);assert.equal(s.day,1);assert.equal(s.crimesDone,0);
});
test('an active casino round prevents starting a criminal minigame',()=>{
 const s=freshState(),crime=crimes[0];s.district=crime.district;s.casinoTable=createCasinoTable('roulette',100,()=>.5);
 const g=new GameEngine(s,()=>.99);assert.equal(g.crimeReady(crime.id).ok,false);
});
for(const [name,alter] of [
 ['incomplete employment',s=>s.employment={id:'janitor'}],
 ['incomplete business',s=>s.businesses={stall:{staff:false}}],
 ['incomplete skip',s=>s.activeSkip={kind:'career',id:'janitor'}],
 ['missing roulette stake',s=>{s.casinoTable=createCasinoTable('roulette',100,()=>.5);delete s.casinoTable.stake;}],
 ['out of range market',s=>s.market=4],
 ['negative incarceration',s=>s.jailDays=-5]
]){
 test('import rejects '+name+' before corrupting the running simulation',()=>{
  const s=freshState();alter(s);assert.throws(()=>validate(s),/формат/);
 });
}
test('social encounters still happen during automatic working days',()=>{
 const s=freshState();s.day=2;s.hour=0;s.money=100000;s.home='loft';s.ownedHomes.push('loft');
 const g=new GameEngine(s,()=>.1);g.tick(24,true,{skipDiet:true});
 assert.equal(s.recentIncident?.socialId,'sergey');
});
test('a prison bed does not keep exposing the prisoner to a former street shelter',()=>{
 const s=freshState();s.home='station';s.vitals.exposure=50;
 dailySettlement(s,{rng:()=>.99,prison:true});
 assert.equal(s.vitals.exposure,47);
});
test('undamaged boots cannot be billed for a nonexistent repair',()=>{
 const s=freshState();const g=new GameEngine(s,()=>.99);const before=s.money;
 assert.equal(g.activity('yardrepair').ok,false);assert.equal(s.money,before);assert.equal(s.hour,7);
});
test('nearby familiar faces include known women and exclude unintroduced people',()=>{
 const s=freshState();const html=shell(s,'city','home',null);
 assert.match(html,/Знакомых лиц<\/span><strong>4<\/strong>/);
 s.social.valera.met=false;s.social.marina.met=false;s.romance.profiles.marina.met=false;
 assert.match(shell(s,'city','home',null),/Знакомых лиц<\/span><strong>2<\/strong>/);
});
test('a multi-day advance keeps every log timestamp valid for exporting and loading',()=>{
 const s=freshState();s.money=100000;const g=new GameEngine(s,()=>.99);g.hire('janitor');
 g.tick(72,true,{skipDiet:true});
 assert.equal(s.day,4);assert.ok(s.log.every(x=>x.hour>=0&&x.hour<24));
 assert.doesNotThrow(()=>validate(JSON.parse(JSON.stringify(s))));
});
test('death partway through a long advance retains a valid clock and actual elapsed days',()=>{
 const s=freshState();s.stats.health=2;s.vitals.nutrition=0;s.vitals.unfedDays=6;s.hour=7;
 const g=new GameEngine(s,()=>.99);g.tick(72,true);
 assert.equal(s.death.day,2);assert.ok(s.hour>=0&&s.hour<24);
 assert.doesNotThrow(()=>validate(JSON.parse(JSON.stringify(s))));
});
test('daily work is logged at its actual completion hour instead of midnight',()=>{
 const s=freshState();const g=new GameEngine(s,()=>.99);g.hire('janitor');g.activity('sleep');
 assert.equal(s.log.find(x=>x.text.startsWith('Проснулся, отработал')).hour,15);
});
for(const seed of [17,83,411,1091]){
 test('180 mixed working days remain finite and reloadable with random seed '+seed,()=>{
  let n=seed;const rng=()=>{n=(Math.imul(n,1664525)+1013904223)>>>0;return n/4294967296;};
  let s=freshState();s.money=2000000;moveHome(s,'loft');s.ownedHomes.push('loft');s.district='yard';
  s.upgrades.push('clean-shirt','phone');s.businesses.stall={level:1,condition:100,staff:true,strategy:'safe'};
  const g=new GameEngine(s,rng);assert.equal(g.hire('janitor').ok,true);
  for(let i=0;i<180&&!s.death;i++){
   s.hour=0;g.tick(24,true,{skipDiet:true});
   for(let j=0;j<10&&(s.pending||s.recentIncident||s.romance.conflict);j++){
    const choices=s.pending?[...events,...contextEvents].find(e=>e.id===s.pending.id)?.choices:s.recentIncident?.id?incidents.find(e=>e.id===s.recentIncident.id)?.choices:s.recentIncident?.choices;
    if(s.romance.conflict){g.resolveRomanceConflict(0);continue;}
    const index=Math.max(0,choices?.findIndex(c=>!c.cost||c.cost<=s.money)??0);
    if(s.pending)g.resolveChoice(index);else g.resolveIncident(index);
   }
   assert.ok(Number.isFinite(s.money)&&Number.isFinite(netWorth(s)));
   assert.ok(Object.values(s.stats).every(Number.isFinite));
   if(s.employment)assert.equal((s.employment.nextPay-s.employment.since)%30,0);
   const before={day:s.day,money:s.money,pay:s.employment?.nextPay};
   const restored=validate(JSON.parse(JSON.stringify(s)));g.replaceState(restored);s=g.state;
   assert.deepEqual({day:s.day,money:s.money,pay:s.employment?.nextPay},before);
  }
  assert.equal(s.day,181);assert.equal(s.death,null);
 });
}
