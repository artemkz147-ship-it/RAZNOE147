import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {freshState} from '../src/systems/state.js';
import {validate} from '../src/systems/save.js';
import {GameEngine} from '../src/systems/engine.js';
import {shell,modal} from '../src/ui/views.js';
import {heroStage,backgrounds,characters,heroAgePortraits,transport,props,portraits,items,casinoArt,shelterArt} from '../src/assets/manifest.js';
import {citizenAtlases} from '../src/assets/citizens.js';
import {citizenFrames} from '../src/ui/portraits.js';
import {people} from '../src/data/people.js';
import {romancePeople} from '../src/data/romance.js';
import {districts} from '../src/data/world.js';
import {createCasinoTable} from '../src/systems/casinoTable.js';
import {populationDay} from '../src/systems/population.js';

globalThis.localStorage={setItem(){}};
const clone=s=>JSON.parse(JSON.stringify(s));

test('a free sofa does not dress a new broke hero as a wealthy owner',()=>{
  const s=freshState();assert.equal(heroStage(s),'poor');
  s.home='room';s.ownedHomes.push('room');assert.equal(heroStage(s),'poor');
  s.upgrades.push('clean-shirt');assert.equal(heroStage(s),'worker');
});

test('import clears obsolete pending events just like a normal reload',()=>{
  const saved=validate({...freshState(),pending:{type:'event',id:'removed-legacy-event'}});
  const g=new GameEngine(freshState(),()=>.99);g.replaceState(saved);
  assert.equal(g.state.pending,null);assert.equal(g.activity('rest').ok,true);
});

test('import clears obsolete incidental events just like a normal reload',()=>{
  const saved=validate({...freshState(),recentIncident:{id:'removed-legacy-incident'}});
  const g=new GameEngine(freshState(),()=>.99);g.replaceState(saved);
  assert.equal(g.state.recentIncident,null);assert.equal(g.activity('rest').ok,true);
});

test('import of old owned housing immediately moves the hero to its district',()=>{
  const old=freshState();delete old.housing;old.home='penthouse';old.ownedHomes.push('penthouse');
  const g=new GameEngine(freshState(),()=>.99);g.replaceState(validate(clone(old)));
  assert.equal(g.state.district,'heights');assert.equal(g.state.housing.id,'penthouse');
  assert.equal(g.state.homeRelocationPending,undefined);
});

test('a met wandering contact can be talked to wherever she appears',()=>{
  for(const district of districts){
    const s=freshState();s.district=district.id;s.romance.profiles.irina={met:true,rapport:10,meetings:1,lastDay:0,days:0,spent:0};s.social.irina={met:true,score:10};
    const html=shell(s,'people','home',null,'legal',null,null,null,'contacts');
    assert.match(html,/data-romance-action="talk" data-id="irina"\s*>ПОГОВОРИТЬ/,district.id);
  }
});

for(const [name,patch] of [
  ['invalid day',{day:0}],['missing day',{day:null}],['unknown district',{district:'missing-district'}],
  ['nonnumeric health',{stats:{...freshState().stats,health:'broken'}}],['invalid inventory',{ownedHomes:{}}]
])test('import rejects '+name+' before replacing a working game',()=>assert.throws(()=>validate({...freshState(),...patch}),/сохран|формат|данн|невер/i));

test('valid old saves, current casino tables and new residents still survive validation',()=>{
  const old=freshState();for(const key of ['housing','vitals','population','social','citizens','skipDiet','conditions'])delete old[key];
  const migrated=validate(clone(old));assert.equal(migrated.vitals.nutrition,60);assert.equal(migrated.social.sergey.score,35);
  for(const id of ['roulette','blackjack','poker']){const s=freshState();s.casinoTable=createCasinoTable(id,100,()=>.5);assert.equal(validate(clone(s)).casinoTable.id,id);}
  const populated=freshState();populated.day=721;populationDay(populated,()=>.99);assert.equal(validate(clone(populated)).population.residents.length,4);
});

test('incomplete new casino tables are rejected instead of crashing the resume screen',()=>{
  const s=freshState();s.casinoTable={id:'poker',phase:'play',wager:100,opponents:[]};
  assert.throws(()=>validate(s),/сохран|формат|данн|невер/i);
});

test('non-numeric vital and skill values cannot poison future daily calculations',()=>{
  for(const [key,field] of [['skills','grit'],['vitals','nutrition'],['conditions','shoes']]){
    const s=freshState();s[key][field]=null;assert.throws(()=>validate(s),/сохран|формат|данн|невер/i);
  }
});

test('all registered raster references exist and atlas crops stay within their drawn sheet',()=>{
  const assets=[backgrounds,characters,heroAgePortraits,transport,props,portraits,items,casinoArt,shelterArt,...Object.values(citizenAtlases).map(a=>({image:a.image}))];
  for(const group of assets)for(const url of Object.values(group))assert.ok(fs.existsSync(new URL(url)),url);
  for(const [id,atlas] of Object.entries(citizenAtlases)){
    const png=fs.readFileSync(new URL(atlas.image));
    assert.deepEqual([png.readUInt32BE(16),png.readUInt32BE(20)],atlas.bounds.map(axis=>axis.at(-1)),id+' atlas must match physical PNG dimensions');
  }
  const s=freshState();for(const person of [...people,...romancePeople])for(const age of [20,30,40,50,60,70,80,90,100])for(const outfit of ['worn','casual','business','luxury']){
    s.citizens[person.id]={outfit};const f=citizenFrames(s,person,age),[xs,ys]=f.atlas.bounds;
    assert.ok(f.column>=0&&f.column<f.atlas.columns);assert.ok(f.row>=0&&f.row<f.atlas.rows);
    assert.equal(xs.length,f.atlas.columns+1);assert.equal(ys.length,f.atlas.rows+1);
    assert.ok(xs[f.column+1]-xs[f.column]>10);assert.ok(ys[f.row+1]-ys[f.row]>4);
  }
});

test('every top-level view renders across homeless, rich, old, sick and partnered states',()=>{
  const states=[freshState()];
  const old=freshState();old.day=18001;old.home='station';old.ownedHomes.push('station');old.stats.energy=0;old.vitals.nutrition=0;old.vitals.illness=5;states.push(old);
  const rich=freshState();rich.money=200000000;rich.home='estate';rich.ownedHomes.push('estate');rich.vehicle='limousine';rich.ownedVehicles.push('limousine');rich.upgrades.push('cashmere-coat');rich.romance.partner='marina';rich.romance.partners=['marina'];states.push(rich);
  for(const s of states)for(const tab of ['city','work','assets','business','casino','people','life','story']){
    let html;assert.doesNotThrow(()=>{html=shell(s,tab,'home',null)});
    assert.doesNotMatch(html,/src="(?:undefined|null)"|(?:NaN|Infinity)/,tab+' day '+s.day);
  }
});

test('cause-specific death illustration differs for starvation, illness and affluent old age',()=>{
  const scene=(cause,wealth)=>{const s=freshState();s.money=wealth;s.death={cause,day:20000,age:85};return modal(s,null).match(/<div class="death-art"[^>]*>/)?.[0];};
  const scenes=new Set([scene('Истощение от голода',0),scene('Осложнения болезни',10000),scene('Подорванное здоровье',100000000)]);
  assert.equal(scenes.size,3,'Death currently uses one identical illustration for every cause and social status');
});
