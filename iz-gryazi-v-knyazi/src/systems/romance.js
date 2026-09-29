import { netWorth } from './economy.js';
import { adjust,addLog,clamp } from './state.js';
import { livingRomancePeople,isPresent } from './population.js';
import { activePartners,dropPartner,changeSocial } from './social.js';

const notice=(state,title,text,portrait,choices)=>{if(!state.recentIncident)state.recentIncident={title,text,portrait,art:portrait,choices};};
export function romanceConflictChoices(state){
  const c=state.romance?.conflict;if(!c)return [];
  const person=livingRomancePeople(state).find(x=>x.id===c.partnerId);
  if(c.kind==='affair')return [
    {text:'Признаться честно',cost:0,rapport:person.id==='nina'?-100:-18,effect:{stress:6,respect:person.id==='nina'?-2:0},leave:person.id==='nina',reply:person.id==='nina'?'Нина ушла. Предательство перечеркнуло доверие.':'Правда прозвучала больно, но разговор состоялся.'},
    {text:'Скрыть встречу',cost:0,rapport:person.id==='nina'?-100:-30,effect:{stress:11,contacts:-2,respect:-4},leave:person.id==='nina',reply:'Ложь быстро стала ещё одной причиной не доверять тебе.'},
    {text:'Разорвать отношения',cost:0,rapport:-35,effect:{mood:-7,stress:5},leave:true,reply:'Ты сам поставил точку в этих отношениях.'}
  ];
  const repair=Math.round(person.giftCost*2);
  return [
    {text:'Выслушать и признать свою часть',cost:0,rapport:8,effect:{energy:-4,stress:-3},reply:'Разговор был трудным, но вы услышали друг друга.'},
    {text:'Уйти и остыть',cost:0,rapport:-6,effect:{stress:-2,mood:-2},reply:'Тишина помогла тебе, но другой стороне стало обиднее.'},
    {text:`Загладить подарком · ${repair.toLocaleString('ru-RU')} ₽`,cost:repair,rapport:person.id==='nina'?-4:person.id==='viktoria'?9:5,effect:{mood:2,stress:1},reply:person.id==='nina'?'Нина хотела разговора, а не покупки прощения.':'Подарок смягчил вечер, но причину ссоры не стёр.'}
  ];
}
export function romanceDay(state,rng){
  const r=state.romance;if(!r)return;
  const partners=activePartners(state);
  if(!partners.length){
    if(netWorth(state)>=100000&&isPresent(state,'irina')&&!r.profiles.irina?.met&&rng()<.035){
      r.profiles.irina={rapport:0,meetings:0,lastDay:0,days:0,spent:0,appearance:0,last:'Ирина будто знала, где тебя искать.',met:true};
      notice(state,'Неожиданное знакомство','Ирина встретила тебя у выхода. Говорит, что это совпадение.',4,[{text:'Поговорить спокойно',effect:{contacts:1,stress:1},rapport:{id:'irina',delta:4},reply:'Разговор закончился, вопросы — нет.'},{text:'Уйти',effect:{stress:3,energy:-2},reply:'Она проводила взглядом до поворота.'}]);
    }
    return;
  }
  for(const id of partners)romancePartnerDay(state,rng,id);
}
function romancePartnerDay(state,rng,id){
  const r=state.romance,p=r.profiles[id];if(!p){dropPartner(r,id);return;}
  p.days++;p.appearance=Math.min(2,Math.floor(p.days/8));
  const apart=state.day-(p.lastDay||state.day),conflictChance=.07+(apart>=3?.15:0)+(p.rapport<25?.1:0)+(state.stats.stress>70?.1:0);
  if(id.startsWith('resident-')&&p.days%7===0){p.rapport=clamp(p.rapport+(apart<3?1:-2),0,100);adjust(state,{mood:apart<3?2:-1});}
  if(p.days>=2&&!r.conflict&&rng()<conflictChance){
    const cause=apart>=3?'Ты давно не находил времени на встречу.':state.stats.stress>70?'Напряжение после тяжёлого дня перешло в разговор на повышенных тонах.':'Неосторожная фраза испортила общий вечер.';
    r.conflict={kind:'quarrel',partnerId:id,title:`Ссора с ${livingRomancePeople(state).find(x=>x.id===id)?.name}`,text:cause,day:state.day};
    p.quarrels=(p.quarrels||0)+1;addLog(state,`${r.conflict.title}: ${cause}`,'bad');
  }
  if(id==='marina'){
    if(rng()<.38){const cost=Math.min(state.money,120+Math.floor(rng()*220));state.money-=cost;adjust(state,{mood:2,stress:3,energy:-2});p.last=`Марина предложила спонтанный вечер. Ушло ${cost} ₽.`;addLog(state,p.last,'neutral');}
    if(p.days>=10&&state.money<300&&rng()<.22){adjust(state,{stress:7,mood:-4});p.last='Очередная ссора из-за планов, которых никто не строил.';}
  }
  if(id==='nina'){
    if(r.betrayedNina){dropPartner(r,id);changeSocial(state,id,-70);p.rapport=0;p.last='Нина узнала о встречах за её спиной и ушла.';notice(state,'Разговор без возврата',p.last,1,[{text:'Признать ошибку',effect:{stress:7,respect:-2},reply:'Ты хотя бы сказал правду.'},{text:'Оправдываться',effect:{stress:10,mood:-5},reply:'Нина не стала спорить.'}]);addLog(state,p.last,'bad');}
    else{adjust(state,{mood:p.married?4:2,stress:p.married?-4:-2,health:p.married?1:0});if(p.days%5===0){p.rapport=clamp(p.rapport+2,0,100);p.last='Нина помогла пережить тяжёлую неделю. Вы стали ближе.';addLog(state,p.last,'good');}}
  }
  if(id==='alisa'&&p.days>=7){
    if(rng()<.35){const cost=Math.min(state.money,250+Math.floor(rng()*850));state.money-=cost;adjust(state,{stress:4,mood:-2});p.last=`Алиса снова передумала насчёт общих планов. На спонтанный выход ушло ${cost} ₽.`;addLog(state,p.last,'bad');}
    if(p.days>=17&&rng()<.13){adjust(state,{stress:8,appeal:-2});p.last='Быт сделал разговоры колючими. Вам обоим стало тесно.';notice(state,'Остывший вечер',p.last,2,[{text:'Поговорить честно',effect:{stress:-3,contacts:1},rapport:{id:'alisa',delta:3},reply:'Удалось услышать друг друга.'},{text:'Отложить разговор',effect:{stress:5},reply:'Молчание стало громче.'}]);}
  }
  if(id==='viktoria'){
    if(netWorth(state)<500000){dropPartner(r,id);changeSocial(state,id,-35);p.last='Доходы упали. Виктория сказала, что у вас теперь разные планы.';notice(state,'Разные планы',p.last,3,[{text:'Отпустить',effect:{stress:4},reply:'Она уехала без долгого разговора.'},{text:'Попытаться удержать',effect:{stress:9,mood:-4},reply:'Решение она уже приняла.'}]);addLog(state,p.last,'bad');}
    else if(p.spent>=250000&&rng()<.12){const loss=Math.floor(Math.max(0,state.money)*.5);state.money-=loss;dropPartner(r,id);changeSocial(state,id,-65);p.last=`Виктория исчезла вместе с ${loss.toLocaleString('ru-RU')} ₽. Общий счёт оказался не таким уж общим.`;notice(state,'Пустой счёт',p.last,3,[{text:'Сохранить документы',effect:{stress:5,business:1},reply:'Остались выписки и тяжёлый урок.'},{text:'Закрыть эту историю',effect:{stress:8,mood:-5},reply:'Деньги не вернулись.'}]);addLog(state,p.last,'bad');}
    else if(rng()<.28){const cost=Math.min(state.money,1500+Math.floor(rng()*4500));state.money-=cost;p.spent+=cost;adjust(state,{mood:1,stress:2});p.last=`Очередной дорогой вечер: ${cost} ₽.`;addLog(state,p.last,'neutral');}
  }
  if(id==='irina'){
    if(p.days>=6&&rng()<.14){dropPartner(r,id);changeSocial(state,id,-80);adjust(state,{health:-32,respect:-14,stress:16});p.last='После тяжёлой ссоры ты попал в больницу. Ирина разнесла по знакомым выдуманные истории.';notice(state,'После ссоры',p.last,4,[{text:'Лечиться и собирать доказательства',effect:{health:8,stress:-3},reply:'Здоровье медленно возвращается.'},{text:'Сразу опровергать слухи',effect:{respect:4,energy:-8},reply:'Часть знакомых поверила тебе.'}]);addLog(state,p.last,'bad');}
    else if(rng()<.35){adjust(state,{stress:5,contacts:-1});p.last='Ирина требовала отчёта о каждом звонке. Разговор затянулся до ночи.';addLog(state,p.last,'bad');}
  }
}
