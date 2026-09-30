import test from 'node:test';
import assert from 'node:assert/strict';
import {freshState} from '../src/systems/state.js';
import {moveHome,housingBill} from '../src/systems/housing.js';
import {budgetPlan} from '../src/systems/planner.js';
import {goalsSnapshot,milestones,goalReached} from '../src/data/goals.js';
import {netWorth} from '../src/systems/economy.js';
import {GameEngine} from '../src/systems/engine.js';
import {dailyVitals} from '../src/systems/lifestyle.js';
globalThis.localStorage={setItem(){}};

test('daily recurring budget includes the actual permanently raised monthly rent',()=>{
  const s=freshState();
  moveHome(s,'room');
  s.housing.multiplier=1.12;
  assert.equal(housingBill(s),6384);
  assert.equal(budgetPlan(s).recurring,Math.round(6384/30));
});

test('a free street shelter never grants the penthouse ownership milestone',()=>{
  const s=freshState();
  s.ownedHomes.push('station');
  moveHome(s,'station');
  assert.equal(goalsSnapshot(s).homeRank,0);
  assert.equal(goalReached(s,milestones.find(m=>m.id==='penthouse')),false);
});

test('prepaid rent is not counted as an owned real-estate capital asset',()=>{
  const s=freshState();
  s.money=0;
  s.ownedHomes.push('flat');
  moveHome(s,'flat');
  assert.equal(netWorth(s),0);
});

test('eviction interrupts time skipping even if a promotion already occupied the incident slot',()=>{
  const s=freshState();
  s.money=10000;
  Object.assign(s.stats,{health:100,mood:100,appeal:100,respect:100});
  const g=new GameEngine(s,()=>.99);
  g.hire('janitor');
  moveHome(s,'room');
  s.day=30;s.money=1000;s.employment.accrued=10000;s.housing.multiplier=3;
  g.startTimeSkip('career','janitor',1,'basic');
  g.rng=()=>.02;
  g.advanceTimeSkip();
  assert.equal(s.home,'station');
  assert.equal(s.activeSkip,null);
  assert.match(s.timeSkip.stop,/ночлег|жиль|высел/i);
});

test('the physician chosen in a fever event grants the same medically excused recovery as the clinic',()=>{
  const s=freshState();s.money=10000;s.stats.health=40;s.vitals.illness=4;s.vitals.immunity=0;s.conditions.back=3;
  const g=new GameEngine(s,()=>.99);
  g.hire('janitor');
  dailyVitals(s,()=>0,{fed:true});
  assert.equal(s.recentIncident.title,'Ночная температура');
  const clinicState=structuredClone(s);
  clinicState.recentIncident=null;
  const clinicGame=new GameEngine(clinicState,()=>.99);
  assert.equal(clinicGame.activity('clinic').ok,true);
  g.resolveIncident(1);
  assert.equal(s.vitals.illness,0);
  assert.equal(s.conditions.back,0);
  assert.equal(s.employment.medicalUntil,3);
  for(const field of ['illness','immunity'])assert.equal(s.vitals[field],clinicState.vitals[field]);
  assert.equal(s.conditions.back,clinicState.conditions.back);
  assert.equal(s.employment.medicalUntil,clinicState.employment.medicalUntil);
  assert.equal(s.money,clinicState.money);
  g.activity('sleep');
  assert.equal(s.employment.worked,0);
  assert.equal(s.employment.absences||0,0);
});
