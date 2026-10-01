import test from 'node:test';
import assert from 'node:assert/strict';
import {freshState} from '../src/systems/state.js';
import {GameEngine} from '../src/systems/engine.js';
import {activityEvents} from '../src/data/activityEvents.js';
import {eventEligible} from '../src/data/contextEvents.js';
import {encounterAllowed,recordEncounter} from '../src/systems/encounters.js';
import {moveHome,housingDay} from '../src/systems/housing.js';
import {hireCareer} from '../src/systems/routine.js';
import {endLife,recordHarm} from '../src/systems/endings.js';
import {dailyVitals} from '../src/systems/lifestyle.js';
import {shell,modal} from '../src/ui/views.js';
import {validate} from '../src/systems/save.js';
import {romanceDay} from '../src/systems/romance.js';
import {renderSkipTimer} from '../src/ui/timeSkip.js';
import {recordLifeFactor,deathHistory} from '../src/systems/lifeHistory.js';
globalThis.localStorage={setItem(){}};
const setup=()=>{const s=freshState();s.money=100000;s.stats.health=90;s.stats.energy=90;return [s,new GameEngine(s,()=>.99)];};
test('30 new events are gated and half already have consequences on arrival',()=>{
 assert.equal(activityEvents.length,30);assert.equal(activityEvents.filter(e=>e.occurrence).length,15);
 assert.equal(new Set(activityEvents.map(e=>e.id)).size,30);
 for(const e of activityEvents){assert.equal(typeof e.test,'function');assert.ok(e.scene);assert.equal(e.choices.length,2);}
 const s=freshState();s.day=20;assert.ok(activityEvents.filter(e=>eventEligible(s,e)).every(e=>e.id==='activity-friend-meal'));
});
test('random events have a persisted two per calendar month skip budget',()=>{
 const s=freshState();s.activeSkip={};s.day=2;assert.ok(encounterAllowed(s,'a'));recordEncounter(s,'a');
 s.day=3;assert.equal(encounterAllowed(s,'b'),false);s.day=9;assert.ok(encounterAllowed(s,'b'));recordEncounter(s,'b');
 s.day=25;assert.equal(encounterAllowed(s,'c'),false);s.day=31;assert.ok(encounterAllowed(s,'c'));assert.equal(encounterAllowed(s,'a'),false);
});
test('blogger requires a real operating business',()=>{const s=freshState();s.day=20;assert.equal(eventEligible(s,{id:'blogger',minDay:9}),false);s.businesses.laundry={paused:true};assert.equal(eventEligible(s,{id:'blogger',minDay:9}),false);s.businesses.laundry.paused=false;assert.equal(eventEligible(s,{id:'blogger',minDay:9}),true);});
test('found phone bad outcome dismisses actual janitor and creates a result card',()=>{
 const [s,g]=setup();hireCareer(s,'janitor');s.employment.lastWorkedDay=s.day;g.rng=()=>0;
 s.pending={type:'event',id:'activity-janitor-phone',occurred:true};assert.equal(g.resolveChoice(1).ok,true);
 assert.equal(s.employment,null);assert.equal(s.careerBans.janitor.until,null);assert.equal(s.outcome.scene,'complaint');assert.match(s.outcome.text,/расторгли/);
 assert.equal(g.activity('rest').ok,false);assert.equal(g.acknowledgeOutcome().ok,true);assert.equal(s.outcome,null);
});
test('ordinary event pauses its skip through the result then resumes original pay schedule',()=>{
 const [s,g]=setup();hireCareer(s,'janitor');g.startTimeSkip('career','janitor',1,'basic');const nextPay=s.employment.nextPay;
 s.pending={type:'event',id:'activity-janitor-phone',occurred:true};g.resolveChoice(0);assert.ok(s.outcome);
 g.advanceTimeSkip();assert.equal(s.activeSkip.worked,0);assert.equal(s.activeSkip.status,'waiting');
 const result=g.acknowledgeOutcome();assert.equal(result.timeSkipFrame,undefined);
 g.advanceTimeSkip();assert.equal(s.activeSkip.worked,1);assert.equal(s.employment.nextPay,nextPay);
});
test('result card hides the skip clock until acknowledged',()=>{
 const queries={h2:{},p:{},progress:{}};const root={hidden:false,classList:{toggle(){}},style:{setProperty(){}},querySelector:k=>queries[k]};
 globalThis.document={body:{classList:{toggle(){}}},getElementById:()=>root};
 const [s]=setup();s.activeSkip={name:'Дворник',planned:30,worked:1};s.outcome={title:'Результат'};
 renderSkipTimer(s);assert.equal(root.hidden,true);s.outcome=null;renderSkipTimer(s);assert.equal(root.hidden,false);delete globalThis.document;
});
test('Irina assault is rolled once per day, not again after a failed first roll',()=>{
 const [s]=setup();s.day=10;s.romance.partners=['irina'];s.romance.profiles.irina={met:true,days:6,rapport:90,lastDay:10,spent:0};
 let calls=0;romanceDay(s,()=>{calls++;return calls===1?.2:.99;});assert.equal(calls,3);assert.ok(s.romance.partners.includes('irina'));
});
test('manual spoiled meals are also retained for the death explanation',()=>{
 const [s,g]=setup();g.setDiet('expired');assert.ok(s.lifeHistory.some(x=>x.kind==='food'));endLife(s,'illness');assert.ok(s.death.history.some(x=>x.kind==='food'));
});
test('save validation rejects a nonexistent business anchor',()=>{
 const [s,g]=setup();s.businesses.laundry={level:1,condition:100,staff:true};g.startTimeSkip('business','laundry',1,'basic');
 assert.equal(validate(JSON.parse(JSON.stringify(s))).activeSkip.id,'laundry');s.activeSkip.id='nonexistent';assert.throws(()=>validate(s));
});
test('years of repeated daily conditions do not erase an old injury from death history',()=>{
 const [s]=setup();recordHarm(s,'injury','Падение со стремянки');
 for(let i=0;i<2000;i++){s.day++;recordLifeFactor(s,'food','Питался просрочкой',-1);recordLifeFactor(s,'cold','Ночевал на вокзале');recordLifeFactor(s,'illness','Болел без полного восстановления');}
 assert.equal(s.lifeHistory.length,4);assert.equal(s.lifeHistory.find(x=>x.kind==='food').count,2000);
 assert.ok(deathHistory(s,'illness').some(x=>/стремянки/.test(x.text)));
});
test('risky choice can also succeed and does not guarantee dismissal',()=>{
 const [s,g]=setup();hireCareer(s,'janitor');s.employment.lastWorkedDay=s.day;
 s.pending={type:'event',id:'activity-janitor-phone',occurred:true};g.resolveChoice(1);assert.ok(s.employment);assert.ok(s.outcome);assert.equal(s.careerBans.janitor,undefined);
});
test('booking hostel prepays entire period, extension adds days without double charge',()=>{
 const [s,g]=setup();const before=s.money;assert.equal(g.bookHostel(30).ok,true);assert.equal(s.home,'hostel');
 const bill=s.housing.prepaidCost;assert.equal(before-s.money,bill);assert.equal(s.housing.nextDue,31);
 s.day=30;assert.equal(housingDay(s,()=>.99),0);g.bookHostel(7);assert.equal(s.housing.nextDue,38);
 const paid=s.money;s.day=37;assert.equal(housingDay(s,()=>.99),0);assert.equal(s.money,paid);s.day=38;assert.ok(housingDay(s,()=>.99)>0);
});
test('selected business is the skip anchor and its shutdown stops that skip',()=>{
 const [s,g]=setup();s.businesses.laundry={level:1,condition:100,staff:true};s.businesses.pickup={level:1,condition:100,staff:true};
 assert.equal(g.startTimeSkip('business','laundry',1,'basic').ok,true);assert.match(s.activeSkip.name,/Прачечная/);
 s.businesses.laundry.paused=true;g.advanceTimeSkip();assert.equal(s.activeSkip,null);assert.match(s.timeSkip.stop,/остановлен/);
});
test('warmth scale is shared by acquaintances and partners; used talk vanishes',()=>{
 const [s,g]=setup();const html=shell(s,'people','home',null,'legal',null,null,null,'contacts');assert.match(html,/ТЕПЛОТА/);assert.match(html,/role="meter"/);
 g.person('valera','talk',0);const after=shell(s,'people','home',null,'legal',null,null,null,'contacts');assert.equal(/data-action="talk" data-id="valera"/.test(after),false);
});
test('death explanation preserves old harm and actual nutrition history',()=>{
 const [s]=setup();recordHarm(s,'injury','Падение со стремянки');s.day=30;s.diet='expired';dailyVitals(s,()=>.99,{fed:true});s.day=40;s.vitals.illness=2;endLife(s,'illness');
 assert.ok(s.death.history.some(x=>/стремянки/.test(x.text)));assert.ok(s.death.history.some(x=>/просроч/.test(x.text)));
 const html=modal(s);assert.match(html,/УЗНАТЬ ПРИЧИНУ/);assert.match(html,/Падение со стремянки/);
 const restored=validate(JSON.parse(JSON.stringify(s)));assert.deepEqual(restored.death.history,s.death.history);
});
