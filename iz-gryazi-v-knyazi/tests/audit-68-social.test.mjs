import test from 'node:test';
import assert from 'node:assert/strict';
import { freshState } from '../src/systems/state.js';
import { socialDay, socialGroup, activePartners } from '../src/systems/social.js';
import { romanceDay } from '../src/systems/romance.js';
import { actCasinoTable, pokerScore } from '../src/systems/casinoTable.js';
import { GameEngine } from '../src/systems/engine.js';
import { contextEvents,eventEligible } from '../src/data/contextEvents.js';
Object.defineProperty(globalThis,'localStorage',{value:{setItem(){}},configurable:true});

const card = (rank, suit='♠') => ({rank,suit});
const blackjack = (player,rival) => ({id:'blackjack',stake:100,wager:100,phase:'play',player,rival,opponents:[],deck:[],returned:0});

test('a positive acquaintance cannot spread a rumor about a nonexistent quarrel',()=>{
  const s=freshState();s.day=3;s.social={valera:{met:true,score:25}};
  assert.equal(socialGroup(s,'valera'),'contacts');
  socialDay(s,()=>0);
  assert.doesNotMatch(s.recentIncident?.text||'',/После вашей ссоры/);
});

test('natural blackjack beats a dealer 21 made from three cards',()=>{
  const t=blackjack([card(14),card(13,'♥')],[card(10,'♥'),card(6,'♦'),card(5,'♣')]);
  assert.equal(actCasinoTable(t,'stand',null,()=>.99).done,true);
  assert.equal(t.returned,250);
});

test('dealer natural blackjack beats a player 21 made from three cards',()=>{
  const t=blackjack([card(10),card(6,'♥'),card(5,'♦')],[card(14,'♥'),card(13,'♣')]);
  assert.equal(actCasinoTable(t,'stand',null,()=>.99).done,true);
  assert.equal(t.returned,0);
});

test('a poker rival keeps a completed straight flush during the draw',()=>{
  const winning=[9,10,11,12,13].map(n=>card(n));
  const opponent={id:'rival',name:'Соперник',hand:structuredClone(winning)};
  const t={id:'poker',stake:100,wager:100,phase:'play',player:[card(2,'♦'),card(2,'♥'),card(7,'♣'),card(8,'♥'),card(14,'♣')],opponents:[opponent],deck:[card(3,'♥'),card(4,'♣'),card(5,'♦')],selected:[],drawn:false};
  assert.equal(pokerScore(opponent.hand)[0],8);
  actCasinoTable(t,'draw',null,()=>0);
  assert.deepEqual(opponent.hand,winning);
});

test('an automatic breakup clears a conflict with the departed partner',()=>{
  const s=freshState();s.romance.partners=['viktoria'];s.romance.partner='viktoria';
  s.romance.profiles.viktoria={met:true,rapport:80,meetings:5,lastDay:1,days:10,spent:0,married:true};
  s.social.viktoria={met:true,score:80};s.day=5;
  romanceDay(s,()=>0);
  assert.deepEqual(activePartners(s),[]);
  assert.match(s.recentIncident.text,/Доходы упали/);
  assert.equal(s.romance.conflict,null);
  assert.equal(s.romance.profiles.viktoria.married,false);
});

test('a breakfast received from a food coupon ends fasting and restores nutrition',()=>{
  const s=freshState();s.day=10;s.vitals.nutrition=0;s.vitals.unfedDays=2;s.lastMealDay=8;s.recentIncident={id:'coupon'};
  const game=new GameEngine(s,()=>.99);
  assert.equal(game.resolveIncident(0).ok,true);
  assert.ok(s.vitals.nutrition>0);
  assert.equal(s.vitals.unfedDays,0);
  assert.equal(s.lastMealDay,s.day);
});

test('a complaint about arriving on shift is ineligible on an approved day off',()=>{
  const s=freshState();s.day=10;s.home='station';
  s.employment={id:'clerk',since:1,lastWorkedDay:9,leaveUntil:10};
  const event=contextEvents.find(e=>e.id==='work-street-warning');
  assert.equal(eventEligible(s,event),false);
});

test('loading an older save clears a conflict and marriage flag for an already departed partner',()=>{const s=freshState();s.romance.conflict={partnerId:'viktoria',kind:'quarrel'};s.romance.profiles.viktoria={met:true,rapport:30,married:true};new GameEngine(s,()=>.99);assert.equal(s.romance.conflict,null);assert.equal(s.romance.profiles.viktoria.married,false);});
