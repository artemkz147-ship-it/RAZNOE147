import {homes,vehicles,businesses} from '../data/world.js';
import {addLog} from './state.js';

// The ending describes the actual cause and snapshots the life when it ends.
const stories={
 hunger:{cause:'Истощение от голода',art:'street',titles:['Последние силы','Без следующего завтрака','Капитал не заменил пищу'],texts:['Долгое отсутствие еды привело к истощению. Организм больше не смог поддерживать жизнь.','Запасы сил закончились после нескольких дней без еды. Планы на будущее остались в дневнике.','Накопленное состояние не защитило от истощения. Последние дни без еды оказались роковыми.']},
 cold:{cause:'Переохлаждение',art:'street',titles:['Последний холод','Тепла не хватило','Цена холода'],texts:['Воздействие холода подорвало здоровье. Организм не выдержал переохлаждения.','Переохлаждение оказалось смертельным. Прежние удачи уже не могли изменить этот исход.','Накопленное состояние не защитило от переохлаждения. История жизни закончилась из-за холода.']},
 illness:{cause:'Осложнения болезни',art:'hospital',titles:['Болезнь оказалась сильнее','Оборванные планы','Перед болезнью все равны'],texts:['Болезнь привела к смертельным осложнениям. Организм не смог с ними справиться.','Осложнения болезни оборвали жизнь. Всё, что удалось построить, осталось в её истории.','Даже большое состояние не гарантирует здоровья. Осложнения болезни оказались смертельными.']},
 injury:{cause:'Последствия тяжёлой травмы',art:'hospital',titles:['Последняя травма','Оборванная жизнь','Цена одного риска'],texts:['Травма оказалась смертельной. Организм не смог восстановиться после повреждений.','Последствия тяжёлой травмы оборвали жизнь. Новых записей в дневнике уже не будет.','Большое состояние не защитило от последствий травмы. Накопленный капитал остался итогом прожитой жизни.']},
 violence:{cause:'Смертельные травмы после нападения',art:'alley',titles:['Нападение оборвало жизнь','Встреча без продолжения','Последнее нападение'],texts:['Полученные при нападении травмы оказались смертельными. Эта встреча завершила жизнь.','Нападение закончилось смертельными травмами. Причина этой встречи осталась в событиях дневника.','Накопленное состояние не защитило от нападения. Полученные травмы оказались смертельными.']},
 prison:{cause:'Здоровье не выдержало заключения',art:'prison',titles:['До освобождения не дожил','Последний срок','За закрытой дверью'],texts:['Здоровье ухудшилось в заключении. Жизнь закончилась до освобождения.','Организм не выдержал заключения. Начать следующую страницу жизни после срока уже не удалось.','Большое состояние осталось за стенами. Здоровье не выдержало заключения.']},
 exhaustion:{cause:'Организм не выдержал нагрузки',art:'hospital',titles:['Последние силы','Планы остались на потом','Состояние пережило хозяина'],texts:['Здоровье постепенно ухудшалось, и запас сил закончился. Организм больше не выдержал нагрузки.','Накопленная нагрузка и ухудшение здоровья оборвали жизнь. Оставшиеся планы сохранились только в дневнике.','Большое состояние не заменило здоровье. Организм исчерпал запас сил и не выдержал нагрузки.']},
 age:{cause:'Естественное угасание в старости',art:'home',titles:['Долгая дорога закончилась','Последняя страница дневника','Тихое прощание'],texts:['За плечами осталась долгая жизнь со своими удачами и потерями. В глубокой старости она завершилась естественным угасанием.','Старость завершила долгую жизнь. Её удачи, потери и решения остались в дневнике.','Долгая жизнь и накопленное состояние стали итогом этой истории. Глубокая старость завершила её естественным угасанием.']}
};
export const endingCount=Object.keys(stories).length*3;
function worth(s){return Math.round(s.money-s.debt+(s.investments||[]).reduce((n,item)=>n+item.amount,0)+s.ownedHomes.filter(id=>!['sofa','station','heating-main','hostel','room','flat'].includes(id)).reduce((n,id)=>n+(homes.find(h=>h.id===id)?.price||0),0)+s.ownedVehicles.reduce((n,id)=>n+(vehicles.find(v=>v.id===id)?.price||0),0)+Object.entries(s.businesses).reduce((n,[id,b])=>n+(businesses.find(x=>x.id===id)?.price||0)*(1+.45*(b.level-1)),0));}
export function recordHarm(s,reason,detail){s.lastHarm={reason,detail,day:s.day};}
export function fatalReason(s){
 if(s.vitals.unfedDays>=7&&s.vitals.nutrition===0)return 'hunger';
 if(s.lastHarm&&s.day-s.lastHarm.day<=3)return s.lastHarm.reason;
 if(s.jailDays>0||s.inPrison)return 'prison';
 if(['station','heating-main'].includes(s.home)&&s.vitals.exposure>45&&!s.vitals.illness)return 'cold';
 if(s.vitals.illness)return 'illness';
 if(s.day>=19801&&s.stats.health>=40)return 'age';
 return 'exhaustion';
}
export function endingFor(s,reason=fatalReason(s),snapshot=null){
 const data=stories[reason]||stories.exhaustion,wealth=snapshot?.wealth??worth(s);
 const home=snapshot?.home||s.home,street=['station','heating-main'].includes(home);
 const status=wealth>=10000000?2:wealth>=100000||['loft','duplex','penthouse','estate'].includes(home)?1:0;
 const art=['age','hunger'].includes(reason)?street?'street':['penthouse','estate'].includes(home)?'luxury':'home':data.art;
 return {endingId:`${reason}-${status}`,reason,cause:data.cause,title:data.titles[status],text:data.texts[status],art,wealth,status,detail:s.lastHarm?.reason===reason&&s.day-s.lastHarm.day<=3?s.lastHarm.detail:''};
}
export function endLife(s,reason=fatalReason(s)){
 if(s.death)return s.death;
 s.death={...endingFor(s,reason),day:s.day,age:30+Math.floor((s.day-1)/360),home:s.home};
 if(s.activeSkip){const p=s.activeSkip;s.timeSkip={...p,to:s.day,worked:s.day-p.from,earned:(s.ledger||[]).filter(x=>x.seq>p.startSeq&&x.amount>0).reduce((n,x)=>n+x.amount,0),net:s.money-p.startMoney,stop:'Жизнь закончилась.'};}
 s.activeSkip=null;s.pending=null;s.recentIncident=null;s.casinoTable=null;if(s.romance)s.romance.conflict=null;
 addLog(s,`${s.death.cause}. ${s.death.title}. Жизнь закончилась в ${s.death.age} лет.`,'bad');return s.death;
}
export function deathEnding(s){
 if(s.death?.endingId)return s.death;
 const cause=s.death?.cause||'';
 const genericCause=!cause||cause==='Подорванное здоровье';
 const reason=/голод|Истощение/.test(cause)?'hunger':/болезн/.test(cause)?'illness':/старост/.test(cause)||genericCause&&s.death?.age>=85?'age':fatalReason(s);
 return {...s.death,...endingFor(s,reason,s.death),cause:genericCause?stories[reason].cause:cause};
}
