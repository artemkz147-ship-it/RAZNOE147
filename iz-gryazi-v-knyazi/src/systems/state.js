export const VERSION = 1;

export function freshState() {
  return {
    version: VERSION, day: 1, hour: 7, district: 'yard', visitedDistricts:['yard'], money: 870, debt: 0,
    stats: { health: 82, energy: 73, mood: 48, respect: 0, fame: 0, stress: 19, appeal: 5, contacts: 0, crime: 0, business: 0, life: 8 },
    skills: { grit: 1, charm: 1, focus: 1 }, xp: { grit: 0, charm: 0, focus: 0 },
    employment:null,careerBans:{},propertyAccounts:{},housing:{id:'sofa',since:1,nextDue:2,multiplier:1},home: 'sofa', vehicle: 'feet', ownedHomes: ['sofa'], ownedVehicles: ['feet'], businesses: {}, upgrades: [], lastRestDay: 0,
    conditions: {shoes:100,back:0,hangover:0,loaderShifts:0,walkTrips:0}, incidentHistory: [], recentIncident: null,
    diet:'basic',skipDiet:'basic',activeSkip:null,criminalCases:[], lastMealDay:0, vitals:{nutrition:60,unfedDays:0,immunity:70,fitness:35,exposure:0,strain:0,illness:0}, death:null, careerMonths:0, lastSettlement:null, timeSkip:null,
    vehicleFaults:{},lastHarm:null,lifeHistory:[],outcome:null,eventDays:{},skipEncounters:null,heat: 0, jailDays: 0, crimesDone: 0, arrestCount: 0,
    casino: {rounds:0,wins:0,wagered:0,returned:0,history:[]}, casinoDaily: {day:1,wagered:0}, casinoTable:null,
    romance:{partner:null,partners:[],profiles:{marina:{met:true,rapport:0,meetings:0,lastDay:0,days:0,spent:0}},betrayedNina:false,history:[],conflict:null,affairs:0},
    population:{residents:[],departed:{},nextArrivalDay:181},
    citizens:{},social:{sergey:{met:true,score:35},valera:{met:true,score:8},tamara:{met:true,score:0},marina:{met:true,score:0}},relations: {}, dialogueProgress: {}, dialogueLast: {}, lastWorkResult:null, story: 0, flags: [], log: [{ day:1, hour:7, text:'Проснулся на чужом диване. Город пока не в курсе, что ты собираешься его купить.', type:'story' }],
    market: 1, pending: null, eventHistory: [], lastSaved: Date.now(), totalEarned: 0, jobsDone: 0, ending: false, achievedRoutes: [], ledger:[]
  };
}

export const limits = { health:[0,100], energy:[0,100], mood:[0,100], respect:[0,250], fame:[0,200], stress:[0,100], appeal:[0,100], contacts:[0,100], crime:[0,100], business:[0,200], life:[0,100] };
export function clamp(n,min,max) { return Math.max(min,Math.min(max,n)); }
export function adjust(state,changes) {
  for (const [key,delta] of Object.entries(changes || {})) {
    if (key === 'money') state.money += delta;
    else if (key === 'debt') state.debt = Math.max(0,state.debt + delta);
    else if (key in state.stats) state.stats[key] = clamp(state.stats[key] + delta,...limits[key]);
  }
}

export function addLog(state,text,type='neutral') {
  state.log.unshift({day:state.day,hour:state.hour,text,type});
  state.log = state.log.slice(0,10000);
}
export function addLedger(state,category,amount,text){
  state.ledger ||= [];
  state.ledgerSeq=(state.ledgerSeq||0)+1;
  state.ledger.unshift({seq:state.ledgerSeq,day:state.day,hour:state.hour,category,amount,text});
  state.ledger=state.ledger.slice(0,10000);
}
