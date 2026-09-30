import test from 'node:test';
import assert from 'node:assert/strict';
import {freshState} from '../src/systems/state.js';
import {GameEngine} from '../src/systems/engine.js';
import {incidents} from '../src/data/incidents.js';
import {validate} from '../src/systems/save.js';
import {citizenPortrait} from '../src/ui/portraits.js';
import {people} from '../src/data/people.js';
import {citizenAtlases} from '../src/assets/citizens.js';
import {moveHome} from '../src/systems/housing.js';
import {shell,citizenModal} from '../src/ui/views.js';
globalThis.localStorage={setItem(){}};
test('sleep always starts the next day at morning, even when begun early',()=>{
 for(const hour of [3,7,14,23]){const s=freshState();s.hour=hour;const g=new GameEngine(s,()=>.99);const result=g.activity('sleep');assert.equal(result.ok,true);assert.equal(s.day,2);assert.equal(s.hour,7);}
});
test('sleep immediately runs the employed shift and reports the lost energy',()=>{
 const s=freshState(),g=new GameEngine(s,()=>.99);g.hire('janitor');const result=g.activity('sleep');assert.equal(s.day,2);assert.equal(s.hour,15);assert.equal(s.employment.worked,1);assert.equal(s.lastRoutine.energy,25);assert.match(result.message,/Дворник.*15:00.*25/);assert.ok(s.stats.energy<80);
});
test('wet phone needs an owned phone and invalid saved incidents are removed',()=>{
 const s=freshState(),item=incidents.find(x=>x.id==='wet-phone');assert.equal(item.test?.(s),false);s.recentIncident={id:item.id};new GameEngine(s);assert.equal(s.recentIncident,null);s.upgrades.push('phone');assert.equal(item.test(s),true);
});
test('interrupted time skips and save reload preserve the original monthly pay date and accrued wages',()=>{
 const s=freshState();s.money=100000;s.stats.health=95;s.stats.mood=80;s.stats.appeal=80;const g=new GameEngine(s,()=>.99);g.hire('janitor');g.tick(24,true,{skipDiet:true});const credit=s.employment.accrued;s.recentIncident={title:'Пауза',text:'Нужно решение'};g.resolveIncident(0);const restored=validate(JSON.parse(JSON.stringify(s))),next=new GameEngine(restored,()=>.99);assert.equal(restored.employment.nextPay,31);assert.equal(restored.employment.accrued,credit);next.runSkip({kind:'career',id:'janitor',name:'Дворник',months:1,dietId:'basic'});assert.equal(restored.employment.nextPay,61);const pay=restored.ledger.filter(x=>x.category==='Зарплата');assert.equal(pay.length,1);assert.equal(pay[0].day,31);
});
test('all acquaintances share person cards without publicly editable clothes',()=>{
 const s=freshState(),html=shell(s,'people','home',null);assert.equal((html.match(/class="person-card /g)||[]).length,3);assert.doesNotMatch(html,/class="romance-card|ЖИЗНЬ И ГАРДЕРОБ|ПОДАРКИ И ПОСТУПКИ/);assert.doesNotMatch(citizenModal(s,'valera'),/data-citizen-pane="clothes"/);const g=new GameEngine(s);s.social.valera.score=60;assert.equal(g.citizenAction('valera','gift-business').ok,false);
});
test('portraits render a single clipped atlas cell without ghosted ages',()=>{
 for(const p of people){const html=citizenPortrait(freshState(),p,'','',64);assert.equal((html.match(/<image /g)||[]).length,1);assert.match(html,/clipPath/);}
});
test('every illustrated atlas has monotonic measured boundaries for all ages and outfits',()=>{
 for(const atlas of Object.values(citizenAtlases)){const [x,y]=atlas.bounds;assert.equal(x.length,atlas.columns+1);assert.equal(y.length,atlas.rows+1);for(const values of [x,y])for(let i=1;i<values.length;i++)assert.ok(values[i]>values[i-1]);}
});
test('an actual interrupted skip resumes accrual and pays every 30 days from hiring',()=>{
 const s=freshState();s.day=10;s.money=500000;s.stats.health=95;s.stats.mood=80;s.stats.appeal=80;const g=new GameEngine(s,()=>.99);g.hire('janitor');const tick=g.tick.bind(g);g.tick=(...args)=>{tick(...args);if(s.day===17)s.recentIncident={title:'Звонок',text:'Нужно ответить',choices:[{text:'Ответить',reply:'Разговор завершён'}]};};
 moveHome(s,'room');g.workCareer('janitor',1,'basic');assert.equal(s.timeSkip.worked,7);assert.equal(s.employment.since,10);assert.equal(s.employment.nextPay,40);assert.ok(s.employment.accrued>0);assert.match(s.timeSkip.stop,/решение/);g.resolveIncident(0);g.tick=tick;g.workCareer('janitor',1,'basic');assert.equal(s.ledger.filter(e=>e.category==='Зарплата')[0].day,40);assert.equal(s.employment.nextPay,70);assert.match(s.lastWorkResult.detail,/Начислено к выплате.*день 70/);
});
test('sleep on a scheduled day off leaves the morning free',()=>{
 const s=freshState(),g=new GameEngine(s,()=>.99);g.hire('janitor');s.day=6;s.hour=20;const result=g.activity('sleep');assert.equal(s.day,7);assert.equal(s.hour,7);assert.equal(s.lastRoutine.energy,0);assert.match(result.message,/без рабочей смены/);
});
