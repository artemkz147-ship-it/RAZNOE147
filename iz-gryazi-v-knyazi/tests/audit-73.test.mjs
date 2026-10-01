import test from 'node:test';
import assert from 'node:assert/strict';
import {freshState,addLedger} from '../src/systems/state.js';
import {GameEngine} from '../src/systems/engine.js';
import {moveHome,housingDay} from '../src/systems/housing.js';
import {hireCareer} from '../src/systems/routine.js';
import {dailySettlement} from '../src/systems/economy.js';
import {socialDay} from '../src/systems/social.js';
import {dayPlan} from '../src/systems/planner.js';
import {validate} from '../src/systems/save.js';
globalThis.localStorage={setItem(){}};
const setup=()=>{const s=freshState();s.money=100000;return [s,new GameEngine(s,()=>.99)];};

test('a brief trip away cannot reset the friends guest-stay deadline',()=>{
 const [s]=setup();s.day=44;moveHome(s,'station');moveHome(s,'sofa');s.day=46;housingDay(s,()=>.99);assert.equal(s.home,'station');
});
test('a genuinely long absence allows a fresh stay with the friend',()=>{
 const [s]=setup();s.day=10;moveHome(s,'station');s.day=100;moveHome(s,'sofa');housingDay(s,()=>.99);assert.equal(s.home,'sofa');assert.equal(s.sofaSince,100);
});
test('a late walk does not overlay a new meeting on a pending daily event',()=>{
 const [s,g]=setup();s.money=50000;s.day=3;s.hour=23;s.social.sergey.score=0;s.district='market';s.visitedDistricts.push('market');g.rng=()=>0;g.activity('walk');assert.equal(s.day,4);assert.ok(s.pending);assert.equal(s.recentIncident,null);
});
test('full time-skip receipts survive journal truncation and reload',()=>{
 const [s,g]=setup();hireCareer(s,'janitor');g.startTimeSkip('career','janitor',1,'basic');
 for(let i=0;i<10001;i++){s.money++;addLedger(s,'Зарплата',1,'Начисление');}
 const loaded=validate(JSON.parse(JSON.stringify(s))),resumed=new GameEngine(loaded,()=>.99);resumed.finishTimeSkip('');assert.equal(loaded.timeSkip.earned,10001);assert.equal(loaded.timeSkip.net,10001);
});
test('converting an overdraft into debt is not earned cash during a skip',()=>{
 const [s,g]=setup();hireCareer(s,'janitor');g.startTimeSkip('career','janitor',1,'basic');s.money=-14000;dailySettlement(s,{rng:()=>.99});g.finishTimeSkip('');assert.equal(s.timeSkip.earned,0);
});
for(const choice of [0,1])test('an enemy response cannot refund negative cash, choice '+choice,()=>{
 const [s,g]=setup();s.day=3;s.money=-1000;s.social.azamat={met:true,score:-30};s.citizens.azamat.retaliation=1;socialDay(s,()=>0);assert.match(s.recentIncident.title,/Ответный ход/);g.resolveIncident(choice);assert.equal(s.money,-1000);
});
test('a shift forecast accounts for illness that actually prevents working',()=>{
 const [s,g]=setup();hireCareer(s,'janitor');s.vitals.illness=4;assert.equal(dayPlan(s).working,false);g.activity('sleep');assert.equal(s.employment.worked,0);
});
test('old saves preserve a long absence from the friends sofa',()=>{
 const [s]=setup();s.day=10;moveHome(s,'station');delete s.sofaAbsentSince;s.day=100;
 const restored=validate(JSON.parse(JSON.stringify(s)));new GameEngine(restored,()=>.99);moveHome(restored,'sofa');housingDay(restored,()=>.99);assert.equal(restored.home,'sofa');assert.equal(restored.sofaSince,100);
});
test('old journals initialise receipts excluding transfers into debt',()=>{
 const [s]=setup();addLedger(s,'Зарплата',9000,'Оплата');addLedger(s,'Перенос в долг',2000,'Минус');delete s.ledgerReceipts;
 const loaded=validate(JSON.parse(JSON.stringify(s)));assert.equal(loaded.ledgerReceipts,9000);new GameEngine(loaded,()=>.99);hireCareer(loaded,'janitor');const g=new GameEngine(loaded,()=>.99);g.startTimeSkip('career','janitor',1,'basic');loaded.money+=300;addLedger(loaded,'Социальные связи',300,'Помощь друга');g.finishTimeSkip('');assert.equal(loaded.timeSkip.earned,300);
});
test('legacy active skips without the new receipt snapshot still report available receipts',()=>{
 const [s,g]=setup();hireCareer(s,'janitor');g.startTimeSkip('career','janitor',1,'basic');delete s.activeSkip.startReceipts;s.money+=250;addLedger(s,'Зарплата',250,'Оплата');g.finishTimeSkip('');assert.equal(s.timeSkip.earned,250);
});
test('invalid receipt counters cannot enter an imported save',()=>{
 const [s]=setup();s.ledgerReceipts=-20;assert.throws(()=>validate(s));s.ledgerReceipts=0;hireCareer(s,'janitor');new GameEngine(s,()=>.99).startTimeSkip('career','janitor',1,'basic');s.activeSkip.startReceipts=-5;assert.throws(()=>validate(s));
});
