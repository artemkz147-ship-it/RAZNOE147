/* Permanent mutations and inventory. Offline, versioned, bounded saves. */
window.Progression=(()=>{
 const KEY='deadlight-profile-v2',perks=['vitality','energy','claws','feeding'],items=['life','energy','claw','armour'];
 const integer=(v,min,max,fallback=0)=>Number.isFinite(v)?Math.max(min,Math.min(max,Math.floor(v))):fallback;
 function fresh(){return {level:1,xp:0,points:0,upgrades:{vitality:0,energy:0,claws:0,feeding:0},inventory:{life:2,energy:2,claw:0,armour:0},equipment:{claw:false,armour:false}}}
 function need(p){return 100+(p.level-1)*40}
 function load(){let raw;try{raw=JSON.parse(localStorage.getItem(KEY))}catch{}if(!raw||typeof raw!=='object')return fresh();let p=fresh();p.level=integer(raw.level,1,50,1);p.xp=integer(raw.xp,0,need(p)-1);p.points=integer(raw.points,0,196);for(let k of perks)p.upgrades[k]=integer(raw.upgrades?.[k],0,10);for(let k of items)p.inventory[k]=integer(raw.inventory?.[k],0,999);for(let k of ['claw','armour'])p.equipment[k]=raw.equipment?.[k]===true&&p.inventory[k]>0;return p}
 function save(p){try{localStorage.setItem(KEY,JSON.stringify(p));return true}catch{return false}}
 function stats(p){return {hp:100+p.upgrades.vitality*20,energy:100+p.upgrades.energy*15,damage:12+p.upgrades.claws*3+(p.equipment.claw?5:0),armour:p.equipment.armour?0.2:0,heal:35+p.upgrades.feeding*5,feedEnergy:45+p.upgrades.feeding*4}}
 function award(p,xp){p.xp+=integer(xp,0,1000);let levels=0;while(p.level<50&&p.xp>=need(p)){p.xp-=need(p);p.level++;p.points++;levels++}if(p.level===50)p.xp=Math.min(p.xp,need(p)-1);save(p);return levels}
 function upgrade(p,k){if(!perks.includes(k)||p.points<1||p.upgrades[k]>=10)return false;p.points--;p.upgrades[k]++;save(p);return true}
 function add(p,k,count=1){if(!items.includes(k))return false;p.inventory[k]=Math.min(999,p.inventory[k]+integer(count,0,999));save(p);return true}
 function use(p,a,k){if(!['life','energy'].includes(k)||p.inventory[k]<1)return false;let s=stats(p),field=k==='life'?'hp':'energy',max=s[field];if(a[field]>=max)return false;a[field]=Math.min(max,a[field]+(k==='life'?50:60));p.inventory[k]--;save(p);return true}
 function equip(p,k){if(!['claw','armour'].includes(k)||p.inventory[k]<1)return false;p.equipment[k]=!p.equipment[k];save(p);return true}
 return {fresh,load,save,need,stats,award,upgrade,add,use,equip,perks,items};
})();
