import test from 'node:test';
import { readFileSync } from 'node:fs';
import { citizenAtlases } from '../src/assets/citizens.js';
import assert from 'node:assert/strict';
import { freshState } from '../src/systems/state.js';
import { GameEngine } from '../src/systems/engine.js';
import { people } from '../src/data/people.js';
import { romancePeople } from '../src/data/romance.js';
import { personAge } from '../src/systems/population.js';
import { citizenLife,citizenStatus,citizensDay } from '../src/systems/citizens.js';
import { citizenFrames } from '../src/ui/portraits.js';
import { citizenModal,modal } from '../src/ui/views.js';
import { socialDay } from '../src/systems/social.js';
import { validate } from '../src/systems/save.js';
globalThis.localStorage={setItem(){}};
test('every resident has a shipped raster atlas with matching portrait proportions',()=>{
  for(const p of [...people,...romancePeople]){
    const atlas=citizenAtlases[p.id],png=readFileSync(new URL(atlas.image));
    assert.equal(png.toString('hex',0,8),'89504e470d0a1a0a');
    const w=png.readUInt32BE(16),h=png.readUInt32BE(20);
    assert.ok(Math.abs(atlas.cellHeight-(h/atlas.rows)/(w/atlas.columns))<.001,p.id);
    for(const outfit of ['worn','casual','business','luxury'])for(const age of [30,40,50,60,70,80,90]){
      const s=freshState();s.citizens[p.id]={...citizenLife(s,p),outfit};
      const f=citizenFrames(s,p,age);assert.ok(f.column>=0&&f.next<atlas.columns);assert.ok(f.row>=0&&f.row<4);
    }
  }
});
const person=id=>[...people,...romancePeople].find(p=>p.id===id);
function setup(id,score=50,rng=()=>.99){const s=freshState();s.money=1000000;s.social[id]={met:true,score};const game=new GameEngine(s,rng);return {s,game,life:citizenLife(s,person(id))};}

test('residents start at their individual ages and portrait selection follows their own birthday',()=>{
  const {s}=setup('valera');
  assert.equal(personAge(s,person('valera')),48);
  assert.equal(personAge(s,person('tamara')),64);
  assert.equal(personAge(s,person('alisa')),20);
  assert.equal(personAge(s,person('viktoria')),25);
  assert.equal(citizenFrames(s,person('valera')).column,1);
  assert.equal(citizenFrames(s,person('alisa')).column,0);
  s.day=361;assert.equal(personAge(s,person('valera')),49);assert.equal(personAge(s,person('alisa')),21);
});
test('gifted clothing is charged once and survives every decade in a saved life',()=>{
  const {s,game,life}=setup('alisa');s.romance.partners=['alisa'];
  assert.equal(game.citizenAction('alisa','gift-business').ok,true);
  assert.equal(s.money,982000);assert.equal(life.outfit,'business');
  assert.ok(s.ledger.some(x=>x.category==='Социальные связи'&&x.amount===-18000));
  assert.equal(game.citizenAction('alisa','gift-business').ok,false);
  for(const age of [20,30,40,50,60,70,80,90])assert.equal(citizenFrames(s,person('alisa'),age).row,2);
  const loaded=validate(JSON.parse(JSON.stringify(s)));new GameEngine(loaded);
  assert.equal(citizenLife(loaded,person('alisa')).outfit,'business');
  assert.match(citizenModal(loaded,'alisa','clothes'),/НАДЕТО/);
});
test('financial support repays actual friend debt and has a meaningful cooldown',()=>{
  const {s,game,life}=setup('valera');const before=s.money;
  assert.equal(game.citizenAction('valera','support').ok,true);
  assert.equal(life.debt,0);assert.equal(s.money,before-7000);
  const cash=s.money;assert.equal(game.citizenAction('valera','support').ok,false);assert.equal(s.money,cash);
});
test('work recommendations and business funding affect the next monthly settlement',()=>{
  const {s,game,life}=setup('nina',60,()=>0);s.stats.contacts=25;
  assert.equal(game.citizenAction('nina','introduce').ok,true);assert.ok(life.salary>44000);
  assert.equal(game.citizenAction('nina','fund').ok,true);assert.ok(life.enterprise);
  s.day=30;citizensDay(s,()=>.9);assert.ok(life.lastMonthIncome>0);assert.ok(life.lastMonthExpenses>0);
});
test('enemy business can fail after pressure, while failure still costs the player',()=>{
  const {s,game,life}=setup('azamat',-50,()=>0);s.stats.business=40;
  life.cash=0;life.assets=10000;life.debt=100000;
  assert.equal(game.citizenAction('azamat','competition').ok,true);
  assert.equal(life.enterprise,null);assert.equal(citizenStatus(life),'Разорение');assert.ok(life.retaliation);
  const failed=setup('artur',-50,()=>.99);failed.s.stats.business=40;const before=failed.s.money;
  assert.equal(failed.game.citizenAction('artur','competition').ok,true);assert.ok(failed.s.money<before);assert.ok(failed.life.enterprise);
  const noBusiness=setup('valera',-50,()=>0);noBusiness.s.stats.business=40;
  assert.equal(noBusiness.game.citizenAction('valera','competition').ok,false);
});
test('a harmed enemy can respond through an illustrated social event',()=>{
  const {s,game,life}=setup('azamat',-50,()=>0);life.retaliation=1;s.day=3;
  socialDay(s,()=>0);assert.equal(s.recentIncident.socialId,'azamat');assert.match(modal(s,null),/azamat-life\.png/);
  assert.equal(game.resolveIncident(0).ok,true);assert.equal(life.retaliation,0);
});
test('strangers and enemies cannot be given control of friendly support actions',()=>{
  const {s,game}=setup('valera',-50);assert.equal(game.citizenAction('valera','support').ok,false);
  delete s.social.valera;assert.equal(game.citizenAction('valera','gift-business').ok,false);
});
