// These factors stay hidden during play and become the posthumous history.
export function recordLifeFactor(s,kind,text,change=0){
 s.lifeHistory||=[];
 const previous=s.lifeHistory.findLast(x=>x.kind===kind&&x.text===text);
 if(previous&&previous.kind===kind&&previous.text===text&&s.day-previous.to<=2){previous.to=s.day;previous.count++;previous.change+=change;}
 else s.lifeHistory.push({kind,text,from:s.day,to:s.day,count:1,change});
 if(s.lifeHistory.length>600){const permanent=new Set(['injury','violence','care']);const remove=s.lifeHistory.findIndex(x=>!permanent.has(x.kind));s.lifeHistory.splice(remove>=0?remove:0,1);}
}
export function deathHistory(s,reason){
 const relevant={hunger:['hunger','food','illness'],cold:['cold','illness','neglect'],illness:['food','cold','illness','neglect','injury'],injury:['injury','neglect','illness','food'],violence:['violence','injury','neglect'],prison:['prison','illness','food'],exhaustion:['strain','food','illness','cold','hunger','injury','neglect'],age:['age','illness','strain']}[reason]||[];
 const factors=(s.lifeHistory||[]).filter(x=>relevant.includes(x.kind)||x.kind==='care'&&reason!=='age');
 const selected=new Set();
 for(const kind of [...relevant,'care']){const latest=factors.findLast(x=>x.kind===kind);if(latest)selected.add(latest);}
 for(const item of factors.toReversed()){if(selected.size>=8)break;selected.add(item);}
 return [...selected].sort((a,b)=>a.from-b.from).slice(-8).map(x=>({...x}));
}
