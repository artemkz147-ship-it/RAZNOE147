import test from 'node:test';
import assert from 'node:assert/strict';
import { freshState } from '../src/systems/state.js';
import { GameEngine } from '../src/systems/engine.js';
import { dailySettlement,districtUnlocked,netWorth,settleMatureInvestments,travelOptions } from '../src/systems/economy.js';
import { districts } from '../src/data/world.js';
import { events } from '../src/data/events.js';
import { milestones,routesToSuccess,successRoute,successRoutes } from '../src/data/goals.js';
import { createCasinoTable,actCasinoTable,blackjackValue,pokerScore } from '../src/systems/casinoTable.js';
import { incidents } from '../src/data/incidents.js';
import { validate } from '../src/systems/save.js';
import { casinoOutcome,casinoRemaining } from '../src/data/casino.js';
import { shell,modal } from '../src/ui/views.js';
import { dialogues } from '../src/data/dialogues.js';
import { MiniGame } from '../src/ui/minigames.js';

globalThis.localStorage={data:new Map(),setItem(k,v){this.data.set(k,v)},getItem(k){return this.data.get(k)||null},removeItem(k){this.data.delete(k)}};

test('first shifts consume time and energy and pay earned money',()=>{
  const game=new GameEngine(freshState(),()=>.9);
  const result=game.completeJob('scrap',.95);
  assert.equal(result.ok,true);
  assert.ok(game.state.money>870);
  assert.equal(game.state.hour,10);
  assert.ok(game.state.stats.energy<73);
  assert.equal(game.state.jobsDone,1);
});

test('districts unlock through independent money, respect or contacts paths',()=>{
  const state=freshState();
  const market=districts.find(x=>x.id==='market');
  assert.equal(districtUnlocked(state,market),false);
  state.money=5000;assert.equal(districtUnlocked(state,market),true);
  state.money=0;state.stats.contacts=3;assert.equal(districtUnlocked(state,market),true);
  state.stats.contacts=0;state.stats.respect=8;assert.equal(districtUnlocked(state,market),true);
  state.stats.respect=0;state.visitedDistricts.push('market');assert.equal(districtUnlocked(state,market),true);
  assert.equal(state.pending,null);
});

test('old chapter prompt disappears when a save moves to free play',()=>{
  const old=freshState();old.pending={type:'story',id:3};old.district='market';old.ending=true;
  const migrated=validate(old);
  assert.equal(migrated.pending,null);
  assert.equal(migrated.ending,false);
  assert.ok(migrated.visitedDistricts.includes('market'));
});

test('business settlements include maintenance and condition',()=>{
  const state=freshState();state.money=20000;state.businesses.stall={level:2,staff:true,condition:100};
  const before=state.money;
  const result=dailySettlement(state,{rng:()=>.9});
  assert.ok(result.business>0);
  assert.equal(state.money,before+result.business-result.expenses);
  assert.equal(state.businesses.stall.condition,99);
  assert.ok(netWorth(state)>state.money);
});

test('zero health can recover through rest instead of soft locking',()=>{
  const state=freshState();state.stats.health=0;
  const game=new GameEngine(state,()=>.9);
  const result=game.activity('sleep');
  assert.equal(result.ok,true);
  assert.ok(state.stats.health>0);
});

test('import validation rejects incompatible saves and preserves nested defaults',()=>{
  assert.throws(()=>validate({version:999}),/Неверный/);
  const state=validate({...freshState(),stats:{health:55}});
  assert.equal(state.stats.health,55);
  assert.equal(state.stats.energy,73);
});

test('every random event offers a decision without upfront cash',()=>{
  for (const event of events) assert.ok(event.choices.some(choice=>!choice.cost),event.id);
});

test('mature investments pay out and leave the portfolio',()=>{
  const state=freshState();state.day=8;state.investments=[{id:'bonds',amount:15000,maturity:8}];
  settleMatureInvestments(state,()=>0);
  assert.equal(state.money,870+Math.round(15000*1.07));
  assert.equal(state.investments.length,0);
});

test('crime can end in arrest, fine, injury and a served sentence',()=>{
  const state=freshState();const game=new GameEngine(state,()=>0);
  const outcome=game.completeCrime('parcel',.4);
  assert.equal(outcome.ok,true);
  assert.equal(state.arrestCount,1);
  assert.equal(state.jailDays,2);
  assert.ok(state.money<870);
  assert.ok(state.stats.health<82);
  assert.equal(game.completeJob('scrap',1).ok,false);
  game.serveSentence();game.serveSentence();
  assert.equal(state.jailDays,0);
});

test('successful crime earns more than nearby legal work without accumulating wanted level',()=>{
  const state=freshState();const game=new GameEngine(state,()=>.99);
  game.completeCrime('parcel',.9);
  assert.ok(state.money>2000);
  assert.equal(state.heat,0);
  assert.equal(state.arrestCount,0);
});

test('walking is free and wears shoes; paid bus and fare dodging have consistent costs',()=>{
  const walkState=freshState();walkState.story=2;walkState.stats.respect=8;
  const options=travelOptions(walkState,'market');
  assert.equal(options.find(x=>x.id==='walk').cost,0);
  assert.equal(options.find(x=>x.id==='fare-dodge').fine,options.find(x=>x.id==='bus').cost*10);
  const walk=new GameEngine(walkState,()=>.99);const cash=walkState.money;
  assert.equal(walk.travel('market','walk').ok,true);
  assert.equal(walkState.money,cash);assert.ok(walkState.conditions.shoes<100);
  const busState=freshState();busState.story=2;busState.stats.respect=8;
  const bus=new GameEngine(busState,()=>.99);assert.equal(bus.travel('market','bus').ok,true);
  assert.equal(busState.money,870-options.find(x=>x.id==='bus').cost);
  const evader=freshState();evader.story=2;evader.stats.respect=8;evader.money=0;
  assert.equal(new GameEngine(evader,()=>0).travel('market','fare-dodge').ok,true);
  assert.equal(evader.jailDays,1);
});

test('contextual incidents appear after a relevant action and can be resolved',()=>{
  assert.ok(incidents.length>=40);
  assert.ok(incidents.every(x=>x.choices.length>=2&&x.choices.some(choice=>!choice.cost)));
  const state=freshState();state.story=2;state.stats.respect=8;
  const game=new GameEngine(state,()=>0);
  game.travel('market','walk');
  assert.ok(state.recentIncident);
  assert.equal(game.activity('rest').ok,false);
  assert.equal(game.resolveIncident(0).ok,true);
  assert.equal(state.recentIncident,null);
});

test('damaged shoes slow walking and suits prevent asking for handouts',()=>{
  const state=freshState();state.story=2;state.stats.respect=8;
  const normal=travelOptions(state,'market').find(x=>x.id==='walk');
  state.conditions.shoes=10;
  const damaged=travelOptions(state,'market').find(x=>x.id==='walk');
  assert.ok(damaged.hours>normal.hours&&damaged.energy>normal.energy&&damaged.risk>normal.risk);
  state.upgrades.push('office-suit');
  assert.match(new GameEngine(state,()=>.9).activity('beg').message,/костюме/);
});

test('free sofa has no housing charge and formal work checks appearance',()=>{
  const s=freshState();const before=s.money;
  const settlement=dailySettlement(s,{rng:()=>.99});
  assert.equal(settlement.expenses,110);
  assert.equal(s.money,before-110);
  s.district='center';const game=new GameEngine(s,()=>.99);
  assert.match(game.jobReady('barista').message,/рубашка/);
  s.upgrades.push('clean-shirt');assert.equal(game.jobReady('barista').ok,true);
  s.conditions.hangover=1;assert.match(game.jobReady('barista').message,/перегар/);
});

test('new mini games resolve touch choices without word memory',()=>{
  for(const gameType of ['sort','cipher','stealth']){
    let outcome=null;const game=new MiniGame({game:gameType,name:'Проверка',district:'yard'},score=>outcome=score);
    game.onRender=()=>{};
    assert.ok(game.render().includes('data-mini'));
    for(let i=0;i<3&&game.active;i++){
      const choice=gameType==='sort'?game.sortRound().correct:gameType==='cipher'?game.cipherRound().correct:1;
      game.input(gameType,String(choice));
    }
    assert.ok(Number.isFinite(outcome),gameType);
    assert.ok(outcome>0,gameType);
  }
});

test('district activities are gated and spend their visible cost',()=>{
  const state=freshState();
  const game=new GameEngine(state,()=>.9);
  assert.equal(game.activity('centerdate').ok,false);
  const before=state.money;
  assert.equal(game.activity('yardtea').ok,true);
  assert.equal(state.money,before-180);
  assert.ok(state.stats.contacts>0);
});

test('business strategy changes projected daily result',()=>{
  const state=freshState();
  state.money=100000;
  state.businesses.stall={level:1,staff:false,condition:100,strategy:'safe'};
  const safe=dailySettlement(state,{rng:()=>.99}).business;
  state.businesses.stall.strategy='growth';
  const growth=dailySettlement(state,{rng:()=>.99}).business;
  assert.ok(growth>safe);
});

test('expanded content keeps multiple progression layers available',async()=>{
  const world=await import('../src/data/world.js');
  const activities=await import('../src/data/activities.js');
  assert.ok(world.jobs.length>=25);
  assert.ok(world.businesses.length>=12);
  assert.ok(activities.activities.length>=15);
  assert.ok(milestones.length>=10);
  assert.equal(routesToSuccess.length,3);
});

test('success can be reached by different routes without ending play',()=>{
  const business=freshState();business.money=30000000;business.stats.respect=100;
  for(const id of ['stall','garage','shop','cafe'])business.businesses[id]={level:1,condition:100};
  assert.equal(successRoute(business)?.id,'builder');
  const civic=freshState();civic.money=20000000;civic.stats.respect=150;civic.stats.contacts=35;
  assert.equal(successRoute(civic)?.id,'civic');
  const shadow=freshState();shadow.money=50000000;shadow.stats.crime=45;
  for(const id of ['stall','garage'])shadow.businesses[id]={level:1,condition:100};
  assert.equal(successRoute(shadow)?.id,'shadow');
  const game=new GameEngine(civic,()=>.99);assert.equal(game.activity('rest').ok,true);assert.equal(civic.ending,true);
  assert.equal(game.activity('sleep').ok,true);
  civic.stats.crime=45;civic.businesses.stall={level:1,condition:100};civic.businesses.garage={level:1,condition:100};civic.money=50000000;
  game.activity('rest');
  assert.deepEqual(successRoutes(civic).map(x=>x.id),['civic','shadow']);
  assert.deepEqual(civic.achievedRoutes,['civic','shadow']);
});

test('casino displays deterministic payout and tracks a daily budget',()=>{
  const state=freshState();state.money=100000;
  const game=new GameEngine(state,()=>.04); // roulette pocket 1: red
  const result=game.playCasino('roulette',1000);
  assert.equal(result.ok,true);
  assert.equal(state.money,100900);
  assert.equal(state.casino.wins,1);
  assert.equal(state.casino.history[0].net,900);
  assert.equal(state.casinoDaily.limit,80000);
  assert.equal(casinoRemaining(state),79000);
  assert.equal(game.playCasino('roulette',79001).ok,false);
  assert.equal(state.casino.rounds,1);
});

test('casino losses and invalid stakes cannot create money',()=>{
  const state=freshState();const game=new GameEngine(state,()=>0); // roulette zero
  assert.equal(game.playCasino('roulette',0).ok,false);
  assert.equal(game.playCasino('roulette','oops').ok,false);
  assert.equal(game.playCasino('roulette',100).ok,true);
  assert.equal(state.money,770);
  assert.equal(state.casino.returned,0);
  assert.equal(state.casino.history[0].result,'Зеро');
  assert.equal(casinoOutcome('cards',()=>0).gross,0);
});

test('older saves acquire casino state without losing progress',()=>{
  const old=freshState();old.day=9;old.money=7777;delete old.casino;delete old.casinoDaily;
  const restored=validate(old);
  assert.equal(restored.money,7777);
  assert.equal(restored.casino.rounds,0);
  assert.equal(restored.casinoDaily.wagered,0);
});

test('three casino tables preserve a real stake and use player decisions',()=>{
  const state=freshState();state.money=10000;
  const game=new GameEngine(state,()=>0);
  assert.equal(game.startCasino('roulette',100).ok,true);
  assert.equal(state.money,9900);
  assert.equal(game.casinoAct('select','n0').ok,true);
  assert.equal(game.casinoAct('spin').ok,true);
  assert.equal(state.casinoTable.pocket,0);
  assert.equal(state.money,13500);
  game.closeCasino();
  assert.equal(game.startCasino('blackjack',100).ok,true);
  assert.equal(game.casinoAct('stand').ok,true);
  assert.equal(state.casinoTable.phase,'done');
  game.closeCasino();
  assert.equal(game.startCasino('poker',100).ok,true);
  assert.equal(game.casinoAct('toggle',0).ok,true);
  assert.equal(game.casinoAct('draw').ok,true);
  assert.equal(game.casinoAct('showdown').ok,true);
  assert.equal(state.casinoTable.phase,'done');
  assert.equal(state.casino.rounds,3);
  assert.ok(state.casino.wagered>=300);
  assert.ok(state.casino.returned>=3600);
  assert.equal(blackjackValue([{rank:14},{rank:14},{rank:9}]),21);
  assert.equal(pokerScore([{rank:2,suit:'♠'},{rank:3,suit:'♠'},{rank:4,suit:'♠'},{rank:5,suit:'♠'},{rank:14,suit:'♠'}])[0],8);
});

test('romance trust unlocks commitment and daily consequences',()=>{
  const s=freshState(),game=new GameEngine(s,()=>0);
  assert.equal(game.romanceAction('marina','meet').ok,true);
  assert.equal(game.romanceAction('marina','date').ok,false);
  for(let i=0;i<2;i++){s.day++;s.stats.energy=80;assert.equal(game.romanceAction('marina','meet').ok,true);}
  assert.ok(s.romance.profiles.marina.rapport>=18);
  assert.equal(game.romanceAction('marina','commit').ok,true);
  const before=s.money;s.hour=23;game.activity('rest');
  assert.equal(s.romance.partner,'marina');
  assert.ok(s.money<before);
  assert.match(shell(s,'people','home',null,'legal',null,null,null,'romance'),/romance-card/);
});

test('card tables contain three distinct rivals and settle the shared pot',()=>{
  const blackjack=createCasinoTable('blackjack',200,()=>.43);
  assert.equal(blackjack.opponents.length,3);
  assert.equal(new Set(blackjack.opponents.map(x=>x.id)).size,3);
  assert.equal(actCasinoTable(blackjack,'stand',null,()=>.43).done,true);
  assert.ok(blackjack.opponents.every(x=>x.status!=='Ждёт хода'));
  const poker=createCasinoTable('poker',200,()=>.31);
  assert.equal(poker.opponents.length,3);
  assert.equal(poker.pot,800);
  assert.equal(actCasinoTable(poker,'draw',null,()=>.31).error,undefined);
  assert.equal(actCasinoTable(poker,'raise',null,()=>.31).done,true);
  assert.ok(poker.pot>=1000);
  assert.ok(poker.returned>=0&&poker.returned<=poker.pot);
  assert.match(modal({...freshState(),casinoTable:poker},null),/casino-seat/g);
});

test('gift tastes, arguments and infidelity produce different outcomes',()=>{
  const s=freshState();s.money=10000;s.jobsDone=8;s.home='room';s.district='industrial';s.visitedDistricts.push('industrial');
  const game=new GameEngine(s,()=>.99);
  assert.equal(game.romanceAction('nina','meet').ok,true);
  const before=s.romance.profiles.nina.rapport;s.day++;
  assert.equal(game.romanceAction('nina','gift-luxury').ok,true);
  assert.ok(s.romance.profiles.nina.rapport<before);
  s.day++;
  assert.equal(game.romanceAction('nina','gift-useful').ok,true);
  assert.ok(s.romance.profiles.nina.rapport>before);
  s.romance.profiles.nina.rapport=90;assert.equal(game.romanceAction('nina','commit').ok,true);
  s.romance.conflict={kind:'quarrel',partnerId:'nina',title:'Разговор',text:'Ссора'};
  assert.match(modal(s,null),/data-romance-choice="0"/);
  assert.equal(game.resolveRomanceConflict(0).ok,true);
  assert.equal(s.romance.partner,'nina');
  s.romance.conflict={kind:'affair',partnerId:'nina',targetId:'alisa',title:'Измена',text:'Встреча раскрыта'};
  assert.equal(game.resolveRomanceConflict(0).ok,true);
  assert.equal(s.romance.partner,null);
  assert.equal(s.romance.betrayedNina,true);
});

test('old unfinished card rounds refund their stake during migration',()=>{
  const old=freshState();old.money=600;old.casinoTable={id:'poker',wager:200,stake:200,phase:'play'};
  const loaded=validate(old);
  assert.equal(loaded.money,800);
  assert.equal(loaded.casinoTable,null);
  assert.match(loaded.log[0].text,/возвращена/);
});

test('all home cards have an actual raster image reference',()=>{
  const html=shell(freshState(),'assets','home',null);
  assert.equal((html.match(/class="asset-raster"/g)||[]).length,8);
  assert.doesNotMatch(html,/src="undefined"/);
});

test('exhausted player can recover immediately and sleep reaches a useful energy level',()=>{
  const state=freshState();state.stats.energy=0;
  const game=new GameEngine(state,()=>.99);
  assert.match(shell(state,'work','home',null),/СИЛЫ НА ИСХОДЕ/);
  assert.match(shell(state,'work','home',null),/data-id="rest"/);
  assert.equal(game.activity('rest').ok,true);
  assert.equal(state.stats.energy,22);
  assert.equal(state.hour,8);
  assert.equal(game.activity('rest').ok,false);
  assert.equal(state.stats.energy,22);
  assert.equal(game.activity('sleep').ok,true);
  assert.ok(state.stats.energy>=80);
  assert.ok(state.stats.energy<=100);
  assert.ok(game.jobReady('scrap').ok);
});

test('conversations have authored choices, persistent effects and no repeated topic farming',()=>{
  const state=freshState();const game=new GameEngine(state,()=>.99);
  const first=game.person('valera','talk',0);
  assert.equal(first.ok,true);
  assert.match(first.dialogueReply,/инструменты/);
  assert.equal(state.dialogueProgress.valera,1);
  assert.equal(state.relations.valera,3);
  assert.equal(state.stats.respect,2);
  assert.equal(state.stats.energy,66);
  assert.match(state.log[0].text,/Чужой диван/);
  assert.match(shell(state,'people','home',null),/Работа без вывески/);
  for(let i=1;i<dialogues.valera.length;i++)assert.equal(game.person('valera','talk',0).ok,true);
  const energy=state.stats.energy,relationship=state.relations.valera;
  assert.equal(game.person('valera','talk',0).ok,false);
  assert.equal(state.stats.energy,energy);
  assert.equal(state.relations.valera,relationship);
  const loaded=validate(JSON.parse(JSON.stringify(state)));
  assert.equal(loaded.dialogueProgress.valera,dialogues.valera.length);
  assert.equal(loaded.dialogueLast.valera,state.dialogueLast.valera);
});

test('dialogue panel shows the speaker, alternatives and the chosen outcome',()=>{
  const state=freshState();
  assert.match(modal(state,null,'tamara'),/СВЕТ В ПОДЪЕЗДЕ/);
  assert.equal((modal(state,null,'tamara').match(/data-dialogue-choice=/g)||[]).length,2);
  const game=new GameEngine(state,()=>.99);
  const result=game.person('tamara','talk',1);
  assert.match(modal(state,null,'tamara',result),/ОТНОШЕНИЯ \+3/);
  assert.match(modal(state,null,'tamara',result),/попадает в дневник|ЗАКОНЧИТЬ РАЗГОВОР/i);
});
