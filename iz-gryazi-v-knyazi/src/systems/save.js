import { freshState, VERSION } from './state.js';
import {districts,homes,vehicles,businesses,upgrades} from '../data/world.js';
import {diets,careers} from '../data/lifestyle.js';
import {people} from '../data/people.js';
import {romancePeople} from '../data/romance.js';
import {investments} from '../data/investments.js';
import {casinoGames} from '../data/casino.js';

const invalid=()=>{throw new Error('Неверный формат данных сохранения');};
const record=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const finite=x=>typeof x==='number'&&Number.isFinite(x);
const day=x=>Number.isSafeInteger(x)&&x>=1;
const hour=x=>Number.isInteger(x)&&x>=0&&x<24;
const ids=list=>new Set(list.map(x=>x.id));
const sets={district:ids(districts),home:ids(homes),vehicle:ids(vehicles),business:ids(businesses),upgrade:ids(upgrades),diet:ids(diets),career:ids(careers),investment:ids(investments),casino:ids(casinoGames)};
const clothes=new Set(['worn','casual','business','luxury']);
const card=x=>record(x)&&Number.isInteger(x.rank)&&x.rank>=2&&x.rank<=14&&['♠','♥','♦','♣'].includes(x.suit);
function numericFields(value,allowed){
  if(!record(value))invalid();
  for(const [key,n] of Object.entries(value))if(!Object.hasOwn(allowed,key)||!finite(n))invalid();
}
function arrayField(value,check=()=>true){if(!Array.isArray(value)||!value.every(check))invalid();}
function optionalObject(input,key){if(input[key]!==undefined&&!record(input[key]))invalid();}
function optionalId(value,allowed){if(value!==undefined&&!allowed.has(value))invalid();}
function optionalNumbers(value,keys){for(const key of keys)if(value[key]!==undefined&&!finite(value[key]))invalid();}
function validateSchema(input,fresh){
  if(!record(input)||input.version!==VERSION||!finite(input.money)||!record(input.stats)||!record(input.skills)||!Array.isArray(input.log))invalid();
  if(input.day!==undefined&&!day(input.day)||input.hour!==undefined&&!hour(input.hour))invalid();
  optionalId(input.district,sets.district);optionalId(input.home,sets.home);optionalId(input.vehicle,sets.vehicle);
  optionalId(input.diet,sets.diet);optionalId(input.skipDiet,sets.diet);
  if(input.vehicleFaults!==undefined){if(!record(input.vehicleFaults))invalid();for(const [id,fault] of Object.entries(input.vehicleFaults))if(!sets.vehicle.has(id)||id==='feet'||typeof fault!=='string')invalid();}
  for(const key of ['stats','skills','xp','vitals','conditions'])if(input[key]!==undefined)numericFields(input[key],fresh[key]);
  optionalNumbers(input,['debt','heat','jailDays','lastRestDay','lastMealDay','crimesDone','arrestCount','careerMonths','jobsDone','totalEarned','market','ledgerSeq','lastSaved','story']);
  for(const key of ['ownedHomes','ownedVehicles','upgrades','visitedDistricts'])if(input[key]!==undefined){const allowed=sets[{ownedHomes:'home',ownedVehicles:'vehicle',upgrades:'upgrade',visitedDistricts:'district'}[key]];arrayField(input[key],id=>allowed.has(id));}
  for(const key of ['flags','eventHistory','incidentHistory','achievedRoutes'])if(input[key]!==undefined)arrayField(input[key],id=>typeof id==='string');
  arrayField(input.log,item=>record(item)&&day(item.day)&&hour(item.hour)&&typeof item.text==='string');
  if(input.ledger!==undefined)arrayField(input.ledger,item=>record(item)&&day(item.day)&&hour(item.hour)&&finite(item.amount)&&typeof item.text==='string'&&typeof item.category==='string'&&(item.seq===undefined||Number.isSafeInteger(item.seq)&&item.seq>=0));
  if(input.businesses!==undefined){
    if(!record(input.businesses))invalid();
    for(const [id,firm] of Object.entries(input.businesses)){if(!sets.business.has(id)||!record(firm))invalid();optionalNumbers(firm,['level','condition','expenseFactor','boostUntil','capacity']);if(firm.level!==undefined&&(!Number.isInteger(firm.level)||firm.level<1||firm.level>5))invalid();if(firm.staff!==undefined&&typeof firm.staff!=='boolean')invalid();}
  }
  if(input.housing!==undefined&&input.housing!==null){if(!record(input.housing))invalid();optionalId(input.housing.id,sets.home);for(const key of ['since','nextDue'])if(input.housing[key]!==undefined&&!day(input.housing[key]))invalid();if(input.housing.multiplier!==undefined&&(!finite(input.housing.multiplier)||input.housing.multiplier<=0))invalid();}
  if(input.employment!==undefined&&input.employment!==null){const e=input.employment;if(!record(e)||!sets.career.has(e.id))invalid();optionalNumbers(e,['since','nextPay','worked','accrued','lastShift','reviewDay','leaveUntil','medicalUntil','lastLeave','lastWorkedDay','absences','promotions','performance']);for(const key of ['since','nextPay','reviewDay'])if(e[key]!==undefined&&!day(e[key]))invalid();}
  if(input.investments!==undefined)arrayField(input.investments,item=>record(item)&&sets.investment.has(item.id)&&finite(item.amount)&&item.amount>=0&&day(item.maturity));
  if(input.criminalCases!==undefined)arrayField(input.criminalCases,item=>record(item)&&typeof item.name==='string'&&day(item.day)&&day(item.due)&&day(item.expires)&&finite(item.fine)&&finite(item.jail)&&finite(item.risk));
  if(input.population!==undefined){
    const p=input.population;if(!record(p))invalid();if(p.nextArrivalDay!==undefined&&!day(p.nextArrivalDay))invalid();
    if(p.residents!==undefined)arrayField(p.residents,item=>record(item)&&/^resident-[1-9]\d*$/.test(item.id)&&typeof item.name==='string'&&typeof item.role==='string'&&sets.district.has(item.district)&&day(item.arrivedDay)&&finite(item.ageAtArrival)&&Array.isArray(item.lines)&&item.lines.every(line=>typeof line==='string'));
    if(p.departed!==undefined){if(!record(p.departed))invalid();for(const departed of Object.values(p.departed))if(!record(departed)||!day(departed.day)||!finite(departed.age))invalid();}
  }
  const citizens=new Set([...people,...romancePeople,...(input.population?.residents||[])].map(p=>p.id));
  const romantic=new Set([...romancePeople,...(input.population?.residents||[]).filter(p=>p.romance)].map(p=>p.id));
  for(const key of ['social','relations','dialogueProgress','dialogueLast','citizens']){
    optionalObject(input,key);for(const [id,value] of Object.entries(input[key]||{})){if(!citizens.has(id))invalid();if(['relations','dialogueProgress'].includes(key)&&!finite(value))invalid();if(key==='dialogueLast'&&typeof value!=='string')invalid();if(key==='social'&&(!record(value)||value.score!==undefined&&!finite(value.score)))invalid();if(key==='citizens'){if(!record(value))invalid();optionalNumbers(value,['cash','debt','assets','salary','skill','reputation','pressure','lastActionDay','lastMonth','lastMonthIncome','lastMonthExpenses','outfitUntil','retaliation']);optionalId(value.outfit,clothes);if(value.wardrobe!==undefined)arrayField(value.wardrobe,id=>clothes.has(id));}}
  }
  optionalObject(input,'careerBans');for(const [id,ban] of Object.entries(input.careerBans||{}))if(!sets.career.has(id)||!record(ban)||ban.until!==null&&ban.until!==undefined&&!day(ban.until))invalid();
  if(input.romance!==undefined){
    const r=input.romance;if(!record(r))invalid();if(r.partner!==undefined&&r.partner!==null&&!romantic.has(r.partner))invalid();if(r.partners!==undefined)arrayField(r.partners,id=>romantic.has(id));
    if(r.profiles!==undefined){if(!record(r.profiles))invalid();for(const [id,profile] of Object.entries(r.profiles)){if(!romantic.has(id)||!record(profile))invalid();optionalNumbers(profile,['rapport','meetings','lastDay','days','spent','conversations','appearance','quarrels','lastDateDay','lastGiftDay']);}}
    if(r.history!==undefined)arrayField(r.history);if(r.conflict!==undefined&&r.conflict!==null&&(!record(r.conflict)||!romantic.has(r.conflict.partnerId)))invalid();
  }
  for(const key of ['casino','casinoDaily']){optionalObject(input,key);if(input[key])optionalNumbers(input[key],key==='casino'?['rounds','wins','wagered','returned']:['day','wagered','limit']);}
  if(input.casino?.history!==undefined)arrayField(input.casino.history);
  if(input.casinoTable!==undefined&&input.casinoTable!==null){
    const t=input.casinoTable;if(!record(t)||!sets.casino.has(t.id)||!['play','done'].includes(t.phase))invalid();optionalNumbers(t,['stake','wager','returned','pot']);
    if(['blackjack','poker'].includes(t.id)&&Array.isArray(t.opponents)){
      arrayField(t.deck,card);arrayField(t.player,card);if(t.player.length<(t.id==='poker'?5:2))invalid();
      arrayField(t.opponents,o=>record(o)&&typeof o.id==='string'&&typeof o.name==='string'&&Array.isArray(o.hand)&&o.hand.every(card)&&o.hand.length>=(t.id==='poker'?5:2));if(t.opponents.length!==3)invalid();
      if(t.id==='blackjack')arrayField(t.rival,card);else arrayField(t.selected,n=>Number.isInteger(n)&&n>=0&&n<5);
    }
  }
  if(input.activeSkip!==undefined&&input.activeSkip!==null){const p=input.activeSkip;if(!record(p)||!['career','business'].includes(p.kind))invalid();if(p.kind==='career'&&!sets.career.has(p.id))invalid();optionalId(p.diet,sets.diet);optionalNumbers(p,['from','planned','worked','startMoney','startSeq']);}
  if(input.death!==undefined&&input.death!==null&&(!record(input.death)||!day(input.death.day)||!finite(input.death.age)||typeof input.death.cause!=='string'))invalid();
  for(const key of ['pending','recentIncident'])if(input[key]!==undefined&&input[key]!==null&&!record(input[key]))invalid();
}

const KEY = 'iz-gryazi-v-knyazi-v1';
export function loadGame() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return {state:freshState(),offlineDays:0};
    const state = validate(JSON.parse(raw));
    state.lastSaved = Date.now();
    return {state,offlineDays:0};
  } catch { return {state:freshState(),offlineDays:0}; }
}
export function validate(input) {
  const fresh = freshState();
  validateSchema(input,fresh);
  const oldTable=['blackjack','poker'].includes(input.casinoTable?.id)&&!Array.isArray(input.casinoTable.opponents);
  const refund=oldTable?Math.max(0,Math.round(Number(input.casinoTable.wager)||0)):0;
  const log=oldTable?[{day:input.day,hour:input.hour,text:`Правила казино обновились. Незавершённая ставка ${refund} ₽ возвращена.`,type:'neutral'},...input.log]:input.log;
  return {...fresh,...input,homeRelocationPending:!input.housing&&input.home!=='sofa',money:input.money+refund,log,ledger:Array.isArray(input.ledger)?input.ledger:[],casinoTable:oldTable?null:input.casinoTable||null,pending:input.pending?.type==='story'?null:input.pending,visitedDistricts:[...new Set(['yard',...(input.visitedDistricts||[]),input.district])],ending:false,stats:{...fresh.stats,...input.stats},skills:{...fresh.skills,...input.skills},xp:{...fresh.xp,...input.xp},vitals:{...fresh.vitals,...input.vitals},population:{...fresh.population,...input.population,residents:Array.isArray(input.population?.residents)?input.population.residents:[],departed:{...fresh.population.departed,...input.population?.departed}},social:{...fresh.social,...input.social},relations:{...fresh.relations,...input.relations},dialogueProgress:{...fresh.dialogueProgress,...input.dialogueProgress},dialogueLast:{...fresh.dialogueLast,...input.dialogueLast},businesses:{...fresh.businesses,...input.businesses},casino:{...fresh.casino,...input.casino},casinoDaily:{...fresh.casinoDaily,...input.casinoDaily},romance:{...fresh.romance,...input.romance,partners:[...new Set([...(input.romance?.partners||[]),input.romance?.partner].filter(Boolean))],profiles:{...fresh.romance.profiles,...input.romance?.profiles}},conditions:{...fresh.conditions,...input.conditions},incidentHistory:Array.isArray(input.incidentHistory)?input.incidentHistory:[]};
}
export function saveGame(state) {
  state.lastSaved = Date.now();
  try { localStorage.setItem(KEY,JSON.stringify(state)); return true; } catch { return false; }
}
export function exportGame(state) {
  return new Blob([JSON.stringify({...state,lastSaved:Date.now()},null,2)],{type:'application/json'});
}
export async function importGame(file) { return validate(JSON.parse(await file.text())); }
export function clearGame() { localStorage.removeItem(KEY); }
