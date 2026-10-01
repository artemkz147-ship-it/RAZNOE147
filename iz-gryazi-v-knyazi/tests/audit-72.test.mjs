import test from 'node:test';
import assert from 'node:assert/strict';
import {freshState} from '../src/systems/state.js';
import {GameEngine} from '../src/systems/engine.js';
import {activityEvents} from '../src/data/activityEvents.js';
import {applyOccurrence} from '../src/systems/eventEffects.js';
import {hireCareer} from '../src/systems/routine.js';
import {dailySettlement} from '../src/systems/economy.js';
import {moveHome,housingDay} from '../src/systems/housing.js';
import {eventEligible,contextEvents} from '../src/data/contextEvents.js';
import {validate} from '../src/systems/save.js';
import {budgetPlan} from '../src/systems/planner.js';
import {homes} from '../src/data/world.js';
import {dailyVitals} from '../src/systems/lifestyle.js';
globalThis.localStorage={setItem(){}};
const setup=()=>{const s=freshState();s.money=100000;return [s,new GameEngine(s,()=>.99)];};
test('sleep does not remove the hangover before the next scheduled shift',()=>{
 const [s,g]=setup();hireCareer(s,'janitor');g.activity('drink');g.activity('sleep');assert.equal(s.employment.worked,0);assert.equal(s.employment.accrued,0);assert.equal(s.conditions.hangover,0);
});
test('cash shortage reimbursement cannot create money when the worker has less than the shortage',()=>{
 const [s,g]=setup();s.money=50;const e=activityEvents.find(x=>x.id==='activity-shop-shortage');const before={money:50,health:s.stats.health,energy:s.stats.energy};applyOccurrence(s,e);s.pending={type:'event',id:e.id,occurred:true,before};g.resolveChoice(0);assert.equal(s.money,50);assert.equal(s.outcome.changes.money,0);
});
test('one injuring decision is recorded once in the lifetime history',()=>{
 const [s,g]=setup();s.pending={type:'event',id:'activity-clerk-link',occurred:true};
 const e=activityEvents.find(x=>x.id==='activity-janitor-bin');s.pending.id=e.id;g.rng=()=>0;g.resolveChoice(1);
 assert.equal(s.lifeHistory.filter(x=>x.kind==='injury').reduce((n,x)=>n+x.count,0),1);
});
test('invalid incidental choice leaves the pending decision and money intact',()=>{
 const [s,g]=setup();s.recentIncident={title:'Ссора',choices:[{text:'Ответить',effect:{money:10},reply:'Ответил'}]};const before=s.money;assert.equal(g.resolveIncident(4).ok,false);assert.ok(s.recentIncident);assert.equal(s.money,before);
});
test('an incident after sleep cannot stack on a newly queued event',()=>{
 const [s,g]=setup();s.pending={type:'event',id:'credit',occurred:true};g.rng=()=>0;assert.equal(g.maybeIncident('daily',1),null);assert.equal(s.recentIncident,null);
});
test('switching between owned homes preserves payment dates',()=>{
 const [s]=setup();s.ownedHomes.push('loft','duplex');moveHome(s,'loft');assert.equal(s.housing.nextDue,31);s.day=20;moveHome(s,'duplex');s.day=30;moveHome(s,'loft');assert.equal(s.housing.nextDue,31);
});
test('unused owned home still incurs its scheduled maintenance',()=>{
 const [s]=setup();s.ownedHomes.push('loft');moveHome(s,'loft');s.day=10;moveHome(s,'station');s.day=31;const before=s.money;housingDay(s,()=>.99);assert.equal(before-s.money,66000);assert.equal(s.home,'station');
});
test('owned home billing dates survive saving and loading',()=>{
 const [s]=setup();s.ownedHomes.push('loft');moveHome(s,'loft');s.day=20;moveHome(s,'station');const loaded=validate(JSON.parse(JSON.stringify(s)));loaded.day=30;moveHome(loaded,'loft');assert.equal(loaded.housing.nextDue,31);housingDay(loaded,()=>.99);assert.equal(loaded.money,100000);
});
test('budget includes maintenance on unused property and its next due date',()=>{
 const [s]=setup();s.ownedHomes.push('loft');moveHome(s,'loft');s.day=10;moveHome(s,'station');const b=budgetPlan(s);assert.equal(b.nextDue,31);assert.equal(b.rent,66000);assert.equal(b.recurring,2200);
});
test('jail freezes active and unused property bills once each',()=>{
 const [s]=setup();s.ownedHomes.push('loft','duplex');moveHome(s,'loft');for(let i=0;i<3;i++){s.day++;dailySettlement(s,{prison:true,rng:()=>.99});}assert.equal(s.housing.nextDue,34);assert.equal(s.propertyAccounts.duplex.nextDue,34);assert.equal(s.money,100000);
});
test('occupied owned home is billed once, not both as a home and a property',()=>{
 const [s]=setup();s.ownedHomes.push('loft');moveHome(s,'loft');s.day=31;assert.equal(housingDay(s,()=>.99),66000);assert.equal(s.money,34000);assert.equal(s.housing.nextDue,61);
});
test('after eviction recovery uses the actual shelter rather than the lost home',()=>{
 const [s]=setup();moveHome(s,'flat');s.money=0;s.day=31;s.stats.energy=0;s.diet='basic';dailySettlement(s,{skipDiet:true,rng:()=>.99});assert.equal(s.home,'station');assert.equal(s.stats.energy,Math.round(homes.find(h=>h.id==='station').restore*.43));
});
test('Sergey cannot host again after leaving the population',()=>{
 const [s,g]=setup();moveHome(s,'station');s.population.departed.sergey={day:1,age:80,cause:'Ушёл из жизни'};assert.equal(g.equip('home','sofa').ok,false);assert.equal(s.home,'station');
});
test('staff shortage cannot arise in a business that is stopped',()=>{
 const [s]=setup();s.businesses.laundry={level:1,condition:100,staff:true,paused:true};assert.equal(eventEligible(s,contextEvents.find(x=>x.id==='staff-laundry')),false);
});
test('a fine without any injury cannot become a trauma in the death history',()=>{
 const [s,g]=setup();let calls=0;g.rng=()=>[0,.99,0][calls++]??.99;
 g.completeCrime('parcel',1);assert.equal(s.lastHarm,null);
});
test('robbers can take only available cash, not create an overdraft',()=>{
 const [s,g]=setup();s.money=20;const e=activityEvents.find(x=>x.id==='activity-crime-crew'),before={money:s.money,health:s.stats.health,energy:s.stats.energy};applyOccurrence(s,e);s.pending={type:'event',id:e.id,occurred:true,before};g.rng=()=>0;g.resolveChoice(1);assert.equal(s.money,0);assert.equal(s.outcome.changes.money,-20);
});
test('the last prison day cannot offer a home or private clinic as a workplace fever choice',()=>{
 const [s]=setup();s.stats.health=50;s.vitals.immunity=0;s.diet='expired';dailyVitals(s,()=>0,{fed:true,prison:true});assert.equal(s.recentIncident,null);
});
