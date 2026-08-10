const KEY='shadowRoninSaveV1';
const blank=()=>({
  version:1, coins:0, unlocked:1, levels:{}, scrolls:{},
  upgrades:{vitality:0,blade:0,shadow:0,focus:0},
  settings:{sfx:true,music:true,quality:'balanced',sensitivity:1},
  stats:{deaths:0,kills:0,playSeconds:0,bosses:0},
  introSeen:false
});
export function loadSave(){
  try{
    const raw=localStorage.getItem(KEY);
    if(!raw)return blank();
    const parsed=JSON.parse(raw), b=blank();
    return {...b,...parsed,upgrades:{...b.upgrades,...parsed.upgrades},settings:{...b.settings,...parsed.settings},stats:{...b.stats,...parsed.stats},levels:{...b.levels,...parsed.levels},scrolls:{...b.scrolls,...parsed.scrolls}};
  }catch{return blank();}
}
export function persist(save){localStorage.setItem(KEY,JSON.stringify(save));}
export function resetSave(){const s=blank();persist(s);return s;}
