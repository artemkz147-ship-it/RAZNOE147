import { addLog,clamp } from './state.js';
import { livingPeople,livingRomancePeople,personAge } from './population.js';

export const citizenClothes=[
  {id:'worn',name:'Старая одежда',price:0,row:0},
  {id:'casual',name:'Повседневный комплект',price:4500,row:1},
  {id:'business',name:'Деловая одежда',price:18000,row:2},
  {id:'luxury',name:'Дорогой гардероб',price:140000,row:3}
];
const starts={
  sergey:[18000,12000,120000,28000,'Мастер домоуправления'],valera:[4500,7000,20000,24000,'Гаражный мастер'],tamara:[9000,0,120000,22000,'Пенсия'],
  azamat:[180000,45000,240000,90000,'Торговля'],lida:[42000,0,70000,48000,'Бухгалтер'],
  vera:[65000,0,110000,75000,'Редактор'],artur:[3500000,400000,8000000,450000,'Инвестиционная фирма'],
  minister:[12000000,0,30000000,850000,'Советник'],rosa:[85000,20000,110000,56000,'Свой прилавок'],
  pasha:[115000,10000,220000,78000,'Начальник смены'],zoya:[2000000,90000,5000000,320000,'Адвокатское бюро'],
  marina:[2500,9000,0,13000,'Разовые подработки'],nina:[35000,0,40000,44000,'Бухгалтер'],
  alisa:[20000,18000,12000,27000,'Работа в салоне'],viktoria:[500000,20000,800000,105000,'Организация вечеров'],
  irina:[42000,12000,25000,35000,'Администратор']
};
const enterpriseIds=new Set(['azamat','artur','rosa','zoya','viktoria']);
export const citizenWorth=life=>Math.round(life.cash+life.assets-life.debt);
export const citizenStatus=life=>life.bankrupt?'Разорение':citizenWorth(life)<25000?'Без опоры':citizenWorth(life)<250000?'Обычная жизнь':citizenWorth(life)<2000000?'Обеспеченность':'Большой достаток';
const rank=life=>life.bankrupt||citizenWorth(life)<25000?0:citizenWorth(life)<250000?1:citizenWorth(life)<2000000?2:3;
export function defaultCitizen(person,day=1){
  const inherited=person.portraitId||person.id;
  const [cash,debt,assets,salary,occupation]=starts[person.id]||[16000,0,30000,32000,person.role||'Работа в городе'];
  const life={cash,debt,assets,salary,occupation,skill:1,reputation:50,enterprise:enterpriseIds.has(person.id)?{capital:assets,condition:85}:null,pressure:0,bankrupt:false,wardrobe:['worn'],outfit:'worn',outfitUntil:0,lastActionDay:-100,lastMonth:Math.floor((day-1)/30),last:'',portraitId:inherited};
  const target=rank(life);life.wardrobe=citizenClothes.slice(0,target+1).map(x=>x.id);life.outfit=citizenClothes[target].id;
  return life;
}
export const citizenLife=(state,person)=>state.citizens?.[person.id]||defaultCitizen(person,person.arrivedDay||1);
export function ensureCitizen(state,person){
  if(!state.citizens||typeof state.citizens!=='object'||Array.isArray(state.citizens))state.citizens={};
  const defaults=defaultCitizen(person,person.arrivedDay||1);
  const saved=state.citizens[person.id];
  if(!saved||typeof saved!=='object')return state.citizens[person.id]=defaults;
  for(const [key,value] of Object.entries(defaults))if(saved[key]===undefined)saved[key]=value;
  if(!Array.isArray(saved.wardrobe))saved.wardrobe=[saved.outfit||'worn'];
  if(!citizenClothes.some(x=>x.id===saved.outfit))saved.outfit='worn';
  return saved;
}
export function seedCitizens(state){for(const p of [...livingPeople(state),...livingRomancePeople(state)])ensureCitizen(state,p);}
export function citizenActionInfo(state,person,action){
  const life=citizenLife(state,person),worth=Math.max(0,citizenWorth(life));
  const clothing=citizenClothes.find(x=>action===`gift-${x.id}`);
  if(clothing)return {cost:clothing.price,hours:1,cooldown:0,name:`Подарить: ${clothing.name}`};
  if(action.startsWith('wear-'))return {cost:0,hours:0,cooldown:0,name:'Выбрать одежду'};
  const values={support:[Math.round(clamp(life.debt||10000,5000,60000)),1,14,'Помочь деньгами'],introduce:[2500,2,30,'Найти работу через связи'],training:[6000,2,30,'Оплатить обучение'],fund:[Math.round(clamp(worth*.2,45000,500000)),3,60,'Помочь открыть дело'],competition:[Math.round(clamp(worth*.035,7000,180000)),3,14,'Потеснить его дело'],audit:[Math.round(clamp(worth*.025,12000,120000)),2,30,'Разобрать спорные сделки']};
  const v=values[action];return v?{cost:v[0],hours:v[1],cooldown:v[2],name:v[3]}:null;
}
export function citizenEligibility(state,person,action){
  const life=citizenLife(state,person),score=state.social?.[person.id]?.score??state.romance?.profiles?.[person.id]?.rapport??state.relations?.[person.id]??0;
  const partner=(state.romance?.partners||[]).includes(person.id)||state.romance?.partner===person.id;
  const info=citizenActionInfo(state,person,action);if(!info)return 'Неизвестное действие';
  if((action.startsWith('wear-')||action.startsWith('gift-'))&&!partner)return 'Человек сам выбирает свою одежду';
  if(action.startsWith('wear-'))return score<=-20?'Сначала помиритесь':life.wardrobe.includes(action.slice(5))?'':'Этого комплекта нет в гардеробе';
  if(action.startsWith('gift-'))return score<=-20?'Сначала помиритесь':life.wardrobe.includes(action.slice(5))?'Такой комплект уже есть в гардеробе':'';
  if(['competition','audit'].includes(action)){
    if(score>-20)return 'Это действие доступно против врага';
    if(!life.enterprise)return 'У этого человека сейчас нет своего дела';
    if(state.stats.business<8&&!Object.keys(state.businesses||{}).length)return 'Нужен опыт ведения дел';
  }else if(score<30&&!partner)return 'Нужно доверие друга или партнёра';
  if(action==='introduce'&&personAge(state,person)>=70)return 'Сейчас больше подойдут поддержка или своё дело';
  if(action==='introduce'&&state.stats.contacts<8)return 'Нужны связи в городе';
  if(action==='fund'&&life.enterprise)return 'Своё дело уже есть';
  if(state.day-(life.actionDays?.[action]??-1000)<info.cooldown)return 'Нужно время для следующего шага';
  return '';
}
function bankruptcy(life){
  life.assets=Math.max(0,Math.round(life.assets*.18));life.enterprise=null;life.salary=12000;life.occupation='Поиск заработка';life.bankrupt=true;life.outfit='worn';life.outfitUntil=0;
}
export function applyCitizenAction(state,person,action,rng=Math.random){
  const life=ensureCitizen(state,person),info=citizenActionInfo(state,person,action);
  const reason=citizenEligibility(state,person,action);if(reason)return {ok:false,message:reason};
  if(state.money<info.cost)return {ok:false,message:'Не хватает денег'};
  const oldStatus=citizenStatus(life);state.money-=info.cost;life.actionDays ||= {};life.actionDays[action]=state.day;
  let delta=0,tone='good',text='';
  if(action.startsWith('gift-')){const outfit=action.slice(5);if(!life.wardrobe.includes(outfit))life.wardrobe.push(outfit);life.outfit=outfit;life.outfitUntil=state.day+90;delta=5;text=`${person.name} получил новый комплект и сразу переоделся.`;}
  else if(action.startsWith('wear-')){life.outfit=action.slice(5);life.outfitUntil=state.day+90;text=`${person.name}: выбран ${citizenClothes.find(x=>x.id===life.outfit).name.toLowerCase()}.`;}
  else if(action==='support'){const paid=Math.min(life.debt,info.cost);life.debt-=paid;life.cash+=info.cost-paid;if(life.debt===0&&life.cash>=5000)life.bankrupt=false;delta=7;text=`${person.name}: помощь ${info.cost.toLocaleString('ru-RU')} ₽${paid?`, погашено долга ${paid.toLocaleString('ru-RU')} ₽`:''}.`;}
  else if(action==='training'){life.skill=Math.min(10,life.skill+1);life.reputation=Math.min(100,life.reputation+5);delta=6;text=`${person.name} закончил обучение. Теперь легче получить повышение или развивать дело.`;}
  else if(action==='introduce'){
    const worked=rng()<Math.min(.94,.6+state.stats.contacts*.003+life.skill*.02);
    if(worked){life.salary=Math.max(38000,Math.round(life.salary*1.2));life.occupation='Работа по рекомендации';life.bankrupt=false;delta=9;text=`${person.name} получил работу: ${life.salary.toLocaleString('ru-RU')} ₽ в месяц.`;}
    else{delta=1;tone='neutral';text=`${person.name} прошёл собеседование, но место досталось другому. Поиск продолжается.`;}
  }else if(action==='fund'){
    life.cash+=Math.round(info.cost*.2);life.assets+=Math.round(info.cost*.8);life.enterprise={capital:info.cost,condition:80};life.occupation='Своё небольшое дело';life.salary=Math.max(30000,Math.round(info.cost*.12));life.bankrupt=false;delta=12;text=`С твоей помощью ${person.name} открыл своё дело. Первый результат будет в конце месяца.`;
  }else{
    const worked=rng()<Math.min(.9,.45+state.stats.business*.002+state.skills.focus*.03-life.reputation*.002);
    delta=-10;
    if(worked){
      const loss=Math.round(Math.max(20000,citizenWorth(life)*(.08+rng()*.12))),cashLoss=Math.min(life.cash,loss);life.cash-=cashLoss;const remainder=loss-cashLoss;const assetLoss=Math.min(life.assets,Math.round(remainder*.6));life.assets-=assetLoss;life.debt+=remainder-assetLoss;
      life.pressure=clamp(life.pressure+(action==='competition'?35:24),0,100);life.reputation=Math.max(0,life.reputation-(action==='audit'?12:4));
      if(life.debt>Math.max(30000,life.assets*.5)&&life.cash<1000||life.pressure>=90&&life.cash<life.salary)bankruptcy(life);
      text=life.bankrupt?`${person.name} не смог закрыть обязательства. Дело закрылось, началось разорение.`:`${person.name}: потеряно ${loss.toLocaleString('ru-RU')} ₽, положение дела ухудшилось.`;
    }else{tone='bad';life.pressure=clamp(life.pressure+8,0,100);state.stats.business=Math.max(0,state.stats.business-2);text=`${person.name} отбил твою попытку и узнал, кто за ней стоял.`;}
    life.retaliation=(life.retaliation||0)+1;
  }
  const next=citizenStatus(life);if(next!==oldStatus)text+=` Положение: ${next.toLowerCase()}.`;
  life.last=text;return {ok:true,message:text,tone,delta,cost:info.cost,hours:info.hours};
}
export function citizensDay(state,rng=Math.random){
  seedCitizens(state);if(state.day%30!==0)return;
  for(const person of [...livingPeople(state),...livingRomancePeople(state)]){
    const life=ensureCitizen(state,person),month=Math.floor(state.day/30);if(life.lastMonth>=month)continue;life.lastMonth=month;
    const oldStatus=citizenStatus(life),age=personAge(state,person),known=state.social?.[person.id]?.met||state.dialogueProgress?.[person.id]||state.romance?.profiles?.[person.id]?.met;
    let event='';
    const factor=life.enterprise ? .55+rng()*.9 : 1;
    let income=Math.round(life.salary*factor*(1-life.pressure*.006));
    if(age>=65&&!life.enterprise){if(!life.retired){life.salary=Math.max(18000,Math.round(life.salary*.55));life.retired=true;}income=life.salary;life.occupation='Пенсия и небольшие занятия';}
    if(life.enterprise&&rng()<.035+life.pressure*.001){life.enterprise.condition-=15;income=Math.round(income*.4);event='Сорвалась крупная сделка.';}
    else if(age<65&&!life.enterprise&&rng()<.025&&life.skill>=2){life.salary=Math.round(life.salary*1.18);event=`Получено повышение: ${life.salary.toLocaleString('ru-RU')} ₽ в месяц.`;}
    const expenses=[17000,28000,65000,170000][rank(life)]+Math.round(life.debt*.015);
    life.cash+=income-expenses;
    if(life.cash<0){const gap=-life.cash,sold=Math.min(life.assets,gap);life.assets-=sold;life.debt+=gap-sold;life.cash=0;}
    if(!life.bankrupt&&life.debt>Math.max(30000,life.assets*.5)&&life.cash<1000){bankruptcy(life);event='Не удалось рассчитаться с долгами. Дело и часть имущества потеряны.';}
    if(life.bankrupt&&life.debt===0&&life.cash>25000){life.bankrupt=false;event='Удалось выбраться из долгов и начать заново.';}
    life.pressure=Math.max(0,life.pressure-8);
    const desired=citizenClothes[rank(life)];
    if(state.day>=life.outfitUntil&&life.outfit!==desired.id){
      if(life.wardrobe.includes(desired.id))life.outfit=desired.id;
      else if(life.cash>=desired.price*2){life.cash-=desired.price;life.wardrobe.push(desired.id);life.outfit=desired.id;event=event||'Обновлён гардероб на собственные деньги.';}
    }
    const nextStatus=citizenStatus(life);if(nextStatus!==oldStatus)event+=` Теперь: ${nextStatus.toLowerCase()}.`;
    life.lastMonthIncome=income;life.lastMonthExpenses=expenses;
    if(event){life.last=`${person.name}: ${event}`;if(known)addLog(state,life.last,life.bankrupt?'bad':'story');}
  }
}
