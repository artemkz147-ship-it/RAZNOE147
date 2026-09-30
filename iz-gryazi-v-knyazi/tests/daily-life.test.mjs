import test from 'node:test';
import assert from 'node:assert/strict';
import {freshState} from '../src/systems/state.js';
import {GameEngine} from '../src/systems/engine.js';
import {events} from '../src/data/events.js';
import {contextEvents,eventEligible} from '../src/data/contextEvents.js';
import {moveHome,housingBill,housingDay} from '../src/systems/housing.js';
import {dismissCareer,businessDuty,salaryFor} from '../src/systems/routine.js';
import {dailyBusiness,businessForecast} from '../src/systems/economy.js';
import {businesses} from '../src/data/world.js';
import {careers} from '../src/data/lifestyle.js';
import {shell,modal} from '../src/ui/views.js';

test('rent events require a lease and permanently change the next bill',()=>{
 const s=freshState(),g=new GameEngine(s,()=>.99),rent=events.find(e=>e.id==='rent');s.day=10;
 assert.equal(eventEligible(s,rent),false);moveHome(s,'room');assert.equal(eventEligible(s,rent),true);
 s.money=10000;const before=s.money;s.pending={type:'event',id:'rent'};g.resolveChoice(0);assert.equal(s.money,before);assert.equal(housingBill(s),6384);
 s.day=39;housingDay(s,()=>.99);assert.equal(s.money,before);s.day=40;housingDay(s,()=>.99);assert.equal(s.money,3616);assert.equal(s.housing.nextDue,70);
});
test('hostel bills daily and ownership cannot be removed by a temporary lack of cash',()=>{
 const s=freshState();s.money=1000;moveHome(s,'hostel');s.day++;housingDay(s,()=>.99);assert.equal(s.money,845);
 s.ownedHomes.push('penthouse');moveHome(s,'penthouse');s.money=0;s.day+=30;housingDay(s,()=>.99);assert.equal(s.home,'penthouse');assert.ok(s.ownedHomes.includes('penthouse'));
});
test('the free host can expel an enemy and rich housing relocates the scene',()=>{
 const s=freshState(),g=new GameEngine(s,()=>.99);s.social.valera.score=-25;g.tick(24,true);assert.equal(s.home,'station');s.recentIncident=null;s.money=9000000;
 assert.equal(g.buy('home','penthouse').ok,true);assert.equal(s.district,'heights');assert.equal(s.housing.nextDue,s.day+30);
});
test('a daily employment shift consumes time and energy; payday is monthly',()=>{
 const s=freshState(),g=new GameEngine(s,()=>.99);s.money=50000;g.hire('janitor');const before=s.money;g.tick(24,true);assert.equal(s.hour,15);assert.equal(s.employment.worked,1);assert.equal(s.money,before);assert.match(s.log.map(x=>x.text).join('\n'),/Закончил в 15:00/);
 for(let i=0;i<29;i++){s.stats.health=90;s.vitals.nutrition=80;g.tick(24,true,{skipDiet:true});s.recentIncident=null;}
 assert.ok(s.ledger.some(e=>e.category==='Зарплата'&&e.amount===salaryFor(s,careers[0])));assert.equal(s.employment.nextPay,61);
});
test('firing blocks immediate rehiring and misconduct produces a permanent ban',()=>{
 const s=freshState(),g=new GameEngine(s,()=>.99);g.hire('janitor');dismissCareer(s,'Плохая работа');s.recentIncident=null;assert.equal(g.hire('janitor').ok,false);s.day=92;assert.equal(g.hire('janitor').ok,true);
 dismissCareer(s,'Кража',true);s.recentIncident=null;s.day=1000;assert.equal(g.hire('janitor').ok,false);
});
test('business forecast and actual settlement share exactly the same formula',()=>{
 const s=freshState();s.money=50000;s.businesses.stall={level:2,staff:true,condition:75,strategy:'growth',expenseFactor:1.08};const f=businessForecast(s,businesses[0],s.businesses.stall),cash=s.money;
 const net=dailyBusiness(s,false,()=>0);assert.equal(net,f.netLow);assert.equal(s.money,cash);assert.equal(s.businesses.stall.lastResult.expense,f.expense);
});
test('business insolvency depends on funds, not a random condition threshold',()=>{
 const s=freshState();s.money=50000;s.businesses.stall={level:1,staff:false,condition:20,strategy:'normal'};dailyBusiness(s,false,()=>0);assert.ok(s.businesses.stall);
 s.money=0;dailyBusiness(s,false,()=>0);assert.equal(s.businesses.stall,undefined);assert.match(s.recentIncident.text,/не хватило денег/);
});
test('staff saves owner time but increases actual expenses and opens conditional theft events',()=>{
 const s=freshState(),config=businesses[0],firm={level:1,condition:100,staff:false};s.businesses.stall=firm;assert.equal(businessDuty(config,firm).hours,8);const theft=contextEvents.find(e=>e.id==='staff-stall');assert.equal(eventEligible(s,theft),false);
 const expense=businessForecast(s,config,firm).expense;firm.staff=true;assert.equal(businessDuty(config,firm).hours,1);assert.ok(businessForecast(s,config,firm).expense>expense);assert.equal(eventEligible(s,theft),true);firm.audited=true;assert.equal(eventEligible(s,theft),false);
});
test('work events only concern the current job and are illustrated by a complete person',()=>{
 const s=freshState();s.employment={id:'janitor',lastWorkedDay:1};const eligible=contextEvents.filter(e=>eventEligible(s,e)&&e.scope==='work');assert.equal(eligible.length,3);assert.ok(eligible.every(e=>e.career==='janitor'));
 s.pending={type:'event',id:eligible[0].id};const html=modal(s);assert.match(html,/incident-illustration/);assert.doesNotMatch(html,/items\/jacket/);
});
test('initial contacts have no strangers or visible relationship score targets',()=>{
 const s=freshState(),html=shell(s,'people','home',null,'legal',null,null,null,'contacts');assert.match(html,/Марина/);assert.match(html,/Валера/);assert.match(html,/Тамара/);assert.doesNotMatch(html,/data-id="alisa"|data-id="nina"|ДОВ\.|ПОМОЩЬ ОКАЗАНА/);
 assert.doesNotMatch(shell(s,'people','home',null,'legal',null,null,null,'romance'),/class="romance-card/);
});
test('a friendly conversation is not an affair and even a suitable proposal can be refused',()=>{
 const s=freshState(),g=new GameEngine(s,()=>0);s.romance.partner='nina';s.romance.partners=['nina'];s.romance.profiles.nina={met:true,rapport:90,days:10};s.romance.profiles.marina.rapport=30;
 g.romanceAction('marina','talk');assert.equal(s.romance.conflict,null);g.resolveIncident(0);g.romanceAction('marina','commit');assert.equal(s.romance.partners.includes('marina'),false);
});
test('hunger lives in HUD, daily needs have their own tab, and nothing blocks ordinary navigation',()=>{
 const s=freshState();s.vitals.nutrition=0;assert.match(shell(s,'work','home',null),/СЫТ\./);assert.doesNotMatch(shell(s,'city','home',null),/К ЕДЕ|Город без инструкции|class="diet-panel"/);const html=shell(s,'life','home',null);assert.match(html,/Знакомый ветеринар|Персональный врач|data-id="sleep"/);
});
test('large ordinary time advances preserve every elapsed day',()=>{
 const s=freshState(),g=new GameEngine(s,()=>.99);g.hire('janitor');g.tick(72,true);assert.equal(s.day,4);
});

