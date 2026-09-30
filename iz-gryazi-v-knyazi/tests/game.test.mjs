import test from 'node:test';
import assert from 'node:assert/strict';
import { freshState } from '../src/systems/state.js';
import { GameEngine } from '../src/systems/engine.js';
import { dailySettlement,districtUnlocked,netWorth,settleMatureInvestments,travelOptions } from '../src/systems/economy.js';
import { districts } from '../src/data/world.js';
import { people } from '../src/data/people.js';
import { events } from '../src/data/events.js';
import { milestones,routesToSuccess,successRoute,successRoutes } from '../src/data/goals.js';
import { createCasinoTable,actCasinoTable,blackjackValue,pokerScore } from '../src/systems/casinoTable.js';
import { incidents } from '../src/data/incidents.js';
import { validate } from '../src/systems/save.js';
import { casinoOutcome,casinoRemaining } from '../src/data/casino.js';
import { shell,modal } from '../src/ui/views.js';
import { dialogues,nextDialogue } from '../src/data/dialogues.js';
import { MiniGame } from '../src/ui/minigames.js';
import { activePartners,socialGroup,socialValue } from '../src/systems/social.js';
import { ageOf } from '../src/systems/lifestyle.js';
import { populationDay,personAge,livingPeople,livingRomancePeople,knownDepartures } from '../src/systems/population.js';
import { ageAppearance,ageDecade,ageStyle } from '../src/systems/appearance.js';
import { heroImage,heroStage,heroAgeLayers } from '../src/assets/manifest.js';
import { artScene } from '../src/scene/artscene.js';

globalThis.localStorage={data:new Map(),setItem(k,v){this.data.set(k,v)},getItem(k){return this.data.get(k)||null},removeItem(k){this.data.delete(k)}};

test('every decade changes appearance while clothes survive birthdays',()=>{
  const s=freshState();s.upgrades.push('office-suit');
  const outfit=heroImage(s);
  for(const age of [30,40,50,60,70,80,90]){
    s.day=1+(age-30)*360;
    assert.equal(ageOf(s),age);
    assert.equal(ageDecade(age),age);
    assert.equal(heroImage(s),outfit);
    assert.equal(heroAgeLayers(s).length,age===30?0:1);
    if(age>30)assert.match(heroAgeLayers(s)[0].image,new RegExp(`hero-age-${age}`));
    if(age>30)assert.match(artScene(districts[0],s),new RegExp(`age-decade-${age}`));
  }
  assert.equal(heroStage(s),'suit');
  assert.equal(ageAppearance(49).next,50);
  assert.ok(Number(ageStyle(90).match(/--age-gray:([\d.]+)/)[1])>Number(ageStyle(40).match(/--age-gray:([\d.]+)/)[1]));
});

test('ordinary days charge no automatic food and manual meals enter the diary',()=>{
  const s=freshState(),game=new GameEngine(s,()=>.99);
  const before=s.money;
  game.tick(24,true);
  assert.equal(s.lastSettlement.food,0);
  assert.equal(s.money,before);
  assert.ok(s.vitals.nutrition<60);
  assert.equal(game.setDiet('basic').ok,true);
  assert.equal(s.money,before-180);
  assert.ok(s.ledger.some(x=>x.category==='Питание'&&x.amount===-180));
});

test('player chooses up to five years and skip records income, diet and actual stop',()=>{
  const s=freshState(),game=new GameEngine(s,()=>.99);
  game.hire('janitor');s.money=50000;
  assert.equal(game.skipTime('career','janitor',61,'basic').ok,false);
  assert.equal(game.skipTime('career','janitor',1,'expired').ok,true);
  assert.equal(s.timeSkip.diet,'expired');
  assert.equal(s.timeSkip.planned,30);
  assert.ok(s.ledger.some(x=>x.category==='Зарплата'));
  assert.match(shell(s,'story','home',null),/ЛИЧНЫЙ ДНЕВНИК/);
});

test('prison serves the full term with no rent, income or food charges',()=>{
  const s=freshState(),game=new GameEngine(s,()=>.99);s.jailDays=3;s.home='room';s.money=5000;
  const day=s.day,money=s.money;
  assert.equal(game.serveSentence().ok,true);
  assert.equal(s.day,day+3);assert.equal(s.jailDays,0);assert.equal(s.money,money);
  assert.equal(s.timeSkip.kind,'prison');
});

test('bicycle chain incident is never in the bus or car pool',()=>{
  const chain=incidents.find(x=>x.id==='chain');
  assert.equal(chain.context,'bike');
  assert.ok(!incidents.filter(x=>x.context==='bus'||x.context==='ride').includes(chain));
  assert.ok(incidents.some(x=>x.context==='taxi'));
  assert.ok(incidents.some(x=>x.context==='moped'));
});

test('paid housing eviction happens on its payment date, never on an arbitrary negative day',()=>{
 const s=freshState(),game=new GameEngine(s,()=>.99);s.home='room';s.ownedHomes.push('room');s.housing={id:'room',since:1,nextDue:31,multiplier:1};s.money=100;
 game.tick(24,true);assert.equal(s.home,'room');s.day=30;game.tick(24,true);assert.equal(s.home,'station');assert.ok(!s.ownedHomes.includes('room'));
});

test('career skip counts parallel business income and hunger is visible from other tabs',()=>{
  const s=freshState(),game=new GameEngine(s,()=>.99);s.money=50000;game.hire('janitor');s.businesses.stall={level:1,staff:false,condition:100,strategy:'normal'};
  assert.equal(game.workCareer('janitor',1,'basic').ok,true);
  assert.ok(s.timeSkip.earned>10000);
  s.vitals.nutrition=30;
  assert.match(shell(s,'work','home',null),/СЫТ\./);
});

test('age, diet and month long career use real game days and living costs',()=>{
  const s=freshState(),game=new GameEngine(s,()=>.99);
  assert.equal(ageOf(s),30);
  assert.equal(game.setDiet('expired').ok,true);
  assert.equal(dailySettlement(s,{rng:()=>.99}).expenses,0);
  game.setDiet('basic');
  const before=s.money;
  s.money=20000;game.hire('janitor');
  const result=game.workCareer('janitor',1);
  assert.equal(result.ok,true);
  assert.equal(s.day,31);
  assert.equal(s.careerMonths,1);
  assert.ok(s.money>before);
  assert.match(s.lastWorkResult.detail,/Получено/);
  s.day=361;assert.equal(ageOf(s),31);
});

test('casino entrance requires status and clean clothing',()=>{
  const s=freshState(),game=new GameEngine(s,()=>.9);
  assert.equal(game.startCasino('roulette',100).ok,false);
  s.stats.respect=8;s.upgrades.push('clean-shirt');
  assert.equal(game.startCasino('roulette',100).ok,true);
});

test('month skip advances an existing relationship',()=>{
  const s=freshState();s.romance.partner='nina';s.romance.partners=['nina'];s.romance.profiles.nina={rapport:80,lastDay:1,days:0,met:true};
  const game=new GameEngine(s,()=>.99);s.money=30000;game.hire('janitor');
  assert.equal(game.workCareer('janitor',1).ok,true);
  assert.ok(s.romance.profiles.nina.days>0);
  assert.ok(s.day<=31);
});

test('everyone ages with game time and new generations enter the city',()=>{
  const s=freshState(),valera=people.find(p=>p.id==='valera');
  assert.equal(personAge(s,valera),48);
  s.day=181;populationDay(s,()=>.99);
  assert.equal(s.population.residents.length,1);
  assert.equal(s.population.residents[0].name,'Данил Орлов');
  assert.ok(livingPeople(s).some(p=>p.id==='resident-1'));
  s.day=361;populationDay(s,()=>.99);
  assert.equal(personAge(s,valera),49);
  assert.equal(personAge(s,s.population.residents[0]),25);
  assert.ok(livingRomancePeople(s).some(p=>p.id==='resident-2'));
  assert.match(nextDialogue(s,'resident-1').prompt,/Данил/);
  const migrated=validate({...freshState(),day:800});
  assert.equal(migrated.population.nextArrivalDay,181);
  populationDay(migrated,()=>.99);
  assert.equal(migrated.population.residents.length,4);
});

test('aging can remove old contacts and changes the free sofa',()=>{
  const s=freshState();s.day=19470;s.population.nextArrivalDay=25000;
  populationDay(s,()=>0);
  assert.ok(!livingPeople(s).some(p=>p.id==='sergey'));
  assert.equal(s.home,'station');
  assert.equal(knownDepartures(s).some(x=>x.person.id==='sergey'),true);
  assert.match(shell(s,'people','home',null,'legal',null,null,null,'contacts'),/ПАМЯТЬ ГОРОДА/);
});

test('a newly arrived partner can join relationships and ages with the hero',()=>{
  const s=freshState();s.day=361;populationDay(s,()=>.99);s.district='market';s.visitedDistricts.push('market');
  const newcomer=livingRomancePeople(s).find(p=>p.id==='resident-2');
  assert.equal(newcomer.name,'Оксана Орлова');
  s.romance.profiles[newcomer.id]={rapport:55,meetings:3,lastDay:0,days:0,spent:0,appearance:0,last:'Вы знакомы.',met:true};
  const game=new GameEngine(s,()=>.99);
  assert.equal(game.romanceAction(newcomer.id,'commit').ok,true);
  assert.ok(activePartners(s).includes(newcomer.id));
  assert.match(shell(s,'people','home',null,'legal',null,null,null,'romance'),/Оксана/);
  s.day=721;assert.equal(personAge(s,newcomer),38);
});

test('a known person has a birthday event that changes the bond',()=>{
  const s=freshState(),valera=people.find(p=>p.id==='valera');
  const birthday=Array.from({length:360},(_,i)=>i+2).find(day=>personAge({...s,day},valera)>personAge({...s,day:day-1},valera));
  s.day=birthday;s.population.nextArrivalDay=1000;s.social.valera={score:35,met:true,lastDay:0};
  populationDay(s,()=>.99);
  assert.match(s.recentIncident.title,/День рождения/);
  const game=new GameEngine(s,()=>.99);
  assert.equal(game.resolveIncident(0).ok,true);
  assert.equal(s.social.valera.score,40);
  const working=freshState();working.day=birthday;working.social.valera={score:35,met:true,lastDay:0};working.population.nextArrivalDay=1000;
  populationDay(working,()=>.99,true);
  assert.equal(working.recentIncident,null);
});

test('first shifts consume time and energy and pay earned money',()=>{
  const game=new GameEngine(freshState(),()=>.9);
  const result=game.completeJob('scrap',.95);
  assert.equal(result.ok,true);
  assert.ok(game.state.money>870);
  assert.equal(game.state.hour,7);
  assert.equal(game.state.day,2);
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
  assert.equal(settlement.expenses,0);
  assert.equal(s.money,before);
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
  assert.equal(civic.ending,true);
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
  const state=freshState();state.money=10000;state.stats.respect=8;state.upgrades.push('clean-shirt');
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

test('authored conversations unlock a relationship without visible numeric targets',()=>{
 const s=freshState(),game=new GameEngine(s,()=>.99);for(let i=0;i<3;i++){s.day++;s.stats.energy=80;assert.equal(game.romanceAction('marina','talk').ok,true);assert.ok(s.recentIncident.choices.length===2);assert.equal(game.resolveIncident(0).ok,true);}
 assert.equal(game.romanceAction('marina','commit').ok,true);assert.equal(s.romance.partner,'marina');
});

test('relationship tab shows current couples only and moves exes into social groups',()=>{
  const s=freshState(),game=new GameEngine(s,()=>.99);
  const relationshipView=()=>shell(s,'people','home',null,'legal',null,null,null,'romance');
  const contactView=()=>shell(s,'people','home',null,'legal',null,null,null,'contacts');
  assert.doesNotMatch(relationshipView(),/class="romance-card/);
  assert.match(relationshipView(),/Пока нет отношений/);
  assert.match(contactView(),/data-romance-action="talk" data-id="marina"/);
  assert.doesNotMatch(contactView(),/data-id="irina"/);
  assert.equal(game.romanceAction('marina','meet').ok,true);
  assert.doesNotMatch(relationshipView(),/class="romance-card/);
  assert.match(contactView(),/data-romance-action="talk" data-id="marina"/);
  s.romance.profiles.marina.rapport=30;
  assert.equal(game.romanceAction('marina','commit').ok,true);
  assert.match(relationshipView(),/class="romance-card/);
  assert.doesNotMatch(contactView(),/data-romance-action="talk" data-id="marina"/);
  assert.equal(game.romanceAction('marina','separate').ok,true);
  assert.doesNotMatch(relationshipView(),/class="romance-card/);
  assert.match(contactView(),/data-romance-action="talk" data-id="marina"/);
});

test('several romances can coexist and separating one preserves the other',()=>{
  const s=freshState(),game=new GameEngine(s,()=>.99);
  s.romance.profiles.marina={met:true,rapport:50,meetings:3,lastDay:0,days:0};
  s.romance.profiles.alisa={met:true,rapport:50,meetings:3,lastDay:0,days:0};s.social.marina.score=50;
  assert.equal(game.romanceAction('marina','commit').ok,true);
  assert.equal(game.romanceAction('alisa','commit').ok,true);
  assert.deepEqual(activePartners(s),['marina','alisa']);
  assert.equal((shell(s,'people','home',null,'legal',null,null,null,'romance').match(/class="romance-card/g)||[]).length,2);
  assert.equal(game.romanceAction('marina','separate').ok,true);
  assert.deepEqual(activePartners(s),['alisa']);
  assert.equal(socialGroup(s,'marina'),'contacts');
});

test('contacts can become friends, enemies and acquaintances again',()=>{
  const s=freshState(),game=new GameEngine(s,()=>0);
  assert.equal(game.person('valera','talk',0).ok,true);
  s.social.valera.score=25;s.day+=14;s.stats.energy=80;assert.equal(game.socialAction('valera','help').ok,true);
  assert.equal(socialGroup(s,'valera'),'friends');
  assert.match(shell(s,'people','home',null,'legal',null,null,null,'friends'),/Валера «Ключ»/i);
  for(let i=0;i<4;i++){s.day++;s.stats.energy=80;assert.equal(game.socialAction('valera','boundary').ok,true);}
  assert.equal(socialGroup(s,'valera'),'enemies');
  assert.match(shell(s,'people','home',null,'legal',null,null,null,'enemies'),/Валера «Ключ»/i);
  s.day++;s.stats.energy=80;assert.equal(game.socialAction('valera','reconcile').ok,true);
  assert.equal(socialGroup(s,'valera'),'contacts');
});

test('parcel decisions have one valid answer and memory shows four coloured results',()=>{
  const sort=new MiniGame({game:'sort',name:'Склад',district:'yard'},()=>{}),round=sort.sortRound();
  assert.equal(round.cards.length,4);
  assert.deepEqual(round.cards.map((x,i)=>x.destination===round.destination&&x.weight<=round.limit&&x.seal?i:null).filter(x=>x!==null),[round.correct]);
  const memory=new MiniGame({game:'memory',name:'Заказ',district:'yard'},()=>{});memory.onRender=()=>{};memory.revealed=true;
  for(let i=0;i<3;i++)memory.input('memory',String(i===1?(memory.sequence[i]+1)%6:memory.sequence[i]));
  const html=memory.render();
  assert.match(html,/class="memory-checks"/);
  assert.equal((html.match(/class="correct"/g)||[]).length,2);
  assert.equal((html.match(/class="wrong"/g)||[]).length,1);
  memory.destroy();
});

test('more mini-game mistakes increase criminal consequences and work shows its payout',()=>{
  const poor=new GameEngine(freshState(),()=>.17),good=new GameEngine(freshState(),()=>.17);
  assert.equal(poor.completeCrime('parcel',0).ok,true);
  assert.equal(good.completeCrime('parcel',1).ok,true);
  assert.equal(poor.state.lastWorkResult.earned,0);
  assert.ok(good.state.lastWorkResult.earned>0);
  assert.match(shell(good.state,'work','home',null,'crime'),/ИТОГ ПОСЛЕДНЕГО ДЕЛА/);
  assert.match(shell(good.state,'work','home',null,'crime'),/Получено/);
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
  assert.equal((html.match(/class="asset-raster"/g)||[]).length,10);
  assert.doesNotMatch(html,/src="undefined"/);
});

test('exhausted player can recover immediately and sleep reaches a useful energy level',()=>{
  const state=freshState();state.stats.energy=0;
  const game=new GameEngine(state,()=>.99);
  assert.match(shell(state,'life','home',null),/ЭНЕР\./);
  assert.match(shell(state,'life','home',null),/data-id="rest"/);
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
  assert.match(state.log[0].text,/Гаражная смена/);
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
