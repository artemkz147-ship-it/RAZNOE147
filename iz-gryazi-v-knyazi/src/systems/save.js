import { freshState, VERSION } from './state.js';

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
  if (!input || input.version !== VERSION || typeof input.money !== 'number' || !input.stats || !input.skills || !Array.isArray(input.log)) throw new Error('Неверный формат сохранения');
  const fresh = freshState();
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
