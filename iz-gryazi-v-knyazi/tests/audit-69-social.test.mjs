import test from 'node:test';
import assert from 'node:assert/strict';
import { freshState } from '../src/systems/state.js';
import { GameEngine } from '../src/systems/engine.js';
import { events } from '../src/data/events.js';
import { eventEligible } from '../src/data/contextEvents.js';
import { actCasinoTable } from '../src/systems/casinoTable.js';
import { populationDay, knownDepartures, livingPeople, livingRomancePeople } from '../src/systems/population.js';
import { nextDialogue, dialogueCount } from '../src/data/dialogues.js';
Object.defineProperty(globalThis,'localStorage',{value:{setItem(){}},configurable:true});

const card=(rank,suit='♠')=>({rank,suit});
const blackjack=player=>({id:'blackjack',stake:100,wager:100,phase:'play',player,rival:[card(10,'♥'),card(8,'♦')],opponents:[],deck:[card(2,'♥'),card(9,'♣')],returned:0});

test('blackjack cannot double after the player has already taken a third card',()=>{
  const t=blackjack([card(4),card(6,'♦'),card(3,'♣')]),before=structuredClone(t);
  const result=actCasinoTable(t,'double',null,()=>.99);
  assert.match(result.error,/двух карт/);
  assert.deepEqual(t,before,'rejected doubling must not change the hand, deck or wager');
});

test('blackjack allows an initial double and records exactly one extra stake',()=>{
  const t=blackjack([card(5),card(6,'♦')]);
  const result=actCasinoTable(t,'double',null,()=>.99);
  assert.equal(result.done,true);assert.equal(result.extraStake,100);
  assert.equal(t.wager,200);assert.equal(t.player.length,3);
});

test('a deceased journalist cannot initiate a new investigation',()=>{
  const s=freshState();s.day=80;s.social.vera={met:true,score:40};
  s.population.departed.vera={day:60,age:70,cause:'Ушла из жизни'};
  assert.equal(eventEligible(s,events.find(e=>e.id==='article')),false);
});

test('a declined relationship proposal cannot be repeated immediately on the same day',()=>{
  const s=freshState(),g=new GameEngine(s,()=>0);s.romance.profiles.marina.rapport=60;
  const first=g.romanceAction('marina','commit');assert.match(first.message,/не торопить/);
  const before={mood:s.stats.mood,stress:s.stats.stress,logs:s.log.length};
  const second=g.romanceAction('marina','commit');
  assert.equal(second.ok,false);
  assert.deepEqual({mood:s.stats.mood,stress:s.stats.stress,logs:s.log.length},before);
});

test('an exhausted partner cannot start another personal conversation',()=>{
  const s=freshState(),g=new GameEngine(s,()=>.99);
  s.romance.partner='marina';s.romance.partners=['marina'];s.stats.energy=0;
  assert.equal(g.romanceAction('marina','talk').ok,false);
  assert.equal(s.recentIncident,null);
});

test('an unknown resident can die without inventing a personal shared history',()=>{
  const s=freshState();s.day=2910;s.population.nextArrivalDay=100000;
  const rolls=[.99,0];populationDay(s,()=>rolls.shift()??.99);
  assert.ok(s.population.departed.minister,'mortality still affects unknown residents');
  assert.equal(s.recentIncident,null,'a personal memorial belongs to someone the player knew');
});

test('personal memorials list only departed people the player actually met',()=>{
  const s=freshState();s.population.departed.minister={day:50,age:70,cause:'Ушёл из жизни'};
  s.population.departed.valera={day:60,age:75,cause:'Ушёл из жизни'};
  assert.deepEqual(knownDepartures(s).map(x=>x.id),['valera']);
});

test('a birthday invitation is replaced when the same resident dies that day',()=>{
  const s=freshState();s.day=5460;s.population.nextArrivalDay=100000;
  for(const p of [...livingPeople(s),...livingRomancePeople(s)])if(p.id!=='zoya')s.population.departed[p.id]={day:1,age:90};
  s.social.zoya={met:true,score:50};
  populationDay(s,()=>0);
  assert.ok(s.population.departed.zoya);
  assert.match(s.recentIncident?.title||'',/Память/);
  assert.ok(s.recentIncident.choices.every(choice=>!choice.cost),'there is no birthday gift bill for the departed');
});

test('death of the sofa host stops a running skip through the housing interruption',()=>{
  const s=freshState();s.day=12030;s.population.nextArrivalDay=100000;
  s.activeSkip={kind:'career',status:'running',goal:360,worked:1};
  populationDay(s,()=>0,true);
  assert.ok(s.population.departed.sergey);
  assert.equal(s.home,'station');
  assert.match(s.activeSkip.interruption,/Потерян ночлег/);
  assert.equal(s.recentIncident.interruptSkip,true);
});

test('the casino replaces a departed city rival while keeping three opponents',()=>{
  for(const id of ['blackjack','poker']){
    const s=freshState();s.money=100000;s.stats.respect=10;s.upgrades=['clean-shirt'];
    s.population.departed.artur={day:1,age:90,cause:'Ушёл из жизни'};
    const g=new GameEngine(s,()=>.99);
    assert.equal(g.startCasino(id,100).ok,true);
    assert.equal(s.casinoTable.opponents.length,3);
    assert.ok(s.casinoTable.opponents.every(opponent=>!s.population.departed[opponent.id]),'a departed resident cannot sit at a new casino table');
  }
});

test('conversations continue after introductions and use current housing, work, health or business',()=>{
  const cases=[
    {setup:s=>{s.home='station';},text:/вокзал/i},
    {setup:s=>{s.home='room';s.employment={id:'janitor'};},text:/дворник/i},
    {setup:s=>{s.home='room';s.businesses.stall={paused:true};},text:/остановил работу/},
    {setup:s=>{s.vitals.illness=20;},text:/самочувств|трудно сосредоточиться/},
    {setup:s=>{},text:/Серёги/},
    {setup:s=>{s.home='flat';},text:/план|на уме/}
  ];
  for(const item of cases){
    const s=freshState();s.dialogueProgress.valera=dialogueCount(s,'valera');item.setup(s);
    const scene=nextDialogue(s,'valera');
    assert.equal(scene.generic,true);assert.match(scene.prompt,item.text);
    assert.equal(scene.choices.length,2);
    for(const choice of scene.choices){
      assert.equal(choice.effect?.money,undefined);assert.equal(choice.cost,undefined);
      assert.equal(choice.effect?.energy,undefined);assert.equal(choice.unlockJob,undefined);
      assert.ok(Math.abs(choice.relation)<=3);
    }
  }
});

test('a follow-up conversation never repeats the garage referral or a money payment',()=>{
  const s=freshState();s.dialogueProgress.valera=dialogueCount(s,'valera');s.home='room';
  const g=new GameEngine(s,()=>.99),money=s.money,flags=[...s.flags];
  assert.equal(g.person('valera','talk',0).ok,true);
  assert.equal(s.money,money);assert.deepEqual(s.flags,flags);
  assert.equal(s.stats.energy,68,'the engine alone charges the five energy conversation cost');
});
