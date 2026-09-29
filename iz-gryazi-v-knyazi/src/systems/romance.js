import { netWorth } from './economy.js';
import { adjust,addLog,clamp } from './state.js';

const notice=(state,title,text,portrait,choices)=>{if(!state.recentIncident)state.recentIncident={title,text,portrait,art:portrait,choices};};
export function romanceDay(state,rng){
  const r=state.romance;if(!r)return;
  const id=r.partner;
  if(!id){
    if(netWorth(state)>=100000&&!r.profiles.irina?.met&&rng()<.035){
      r.profiles.irina={rapport:0,meetings:0,lastDay:0,days:0,spent:0,appearance:0,last:'Ирина будто знала, где тебя искать.',met:true};
      notice(state,'Неожиданное знакомство','Ирина встретила тебя у выхода. Говорит, что это совпадение.',4,[{text:'Поговорить спокойно',effect:{contacts:1,stress:1},rapport:{id:'irina',delta:4},reply:'Разговор закончился, вопросы — нет.'},{text:'Уйти',effect:{stress:3,energy:-2},reply:'Она проводила взглядом до поворота.'}]);
    }
    return;
  }
  const p=r.profiles[id];if(!p){r.partner=null;return;}
  p.days++;p.appearance=Math.min(2,Math.floor(p.days/8));
  if(id==='marina'){
    if(rng()<.38){const cost=Math.min(state.money,120+Math.floor(rng()*220));state.money-=cost;adjust(state,{mood:2,stress:3,energy:-2});p.last=`Марина предложила спонтанный вечер. Ушло ${cost} ₽.`;addLog(state,p.last,'neutral');}
    if(p.days>=10&&state.money<300&&rng()<.22){adjust(state,{stress:7,mood:-4});p.last='Очередная ссора из-за планов, которых никто не строил.';}
  }
  if(id==='nina'){
    if(r.betrayedNina){r.partner=null;p.rapport=0;p.last='Нина узнала о встречах за её спиной и ушла.';notice(state,'Разговор без возврата',p.last,1,[{text:'Признать ошибку',effect:{stress:7,respect:-2},reply:'Ты хотя бы сказал правду.'},{text:'Оправдываться',effect:{stress:10,mood:-5},reply:'Нина не стала спорить.'}]);addLog(state,p.last,'bad');}
    else{adjust(state,{mood:p.married?4:2,stress:p.married?-4:-2,health:p.married?1:0});if(p.days%5===0){p.rapport=clamp(p.rapport+2,0,100);p.last='Нина помогла пережить тяжёлую неделю. Вы стали ближе.';addLog(state,p.last,'good');}}
  }
  if(id==='alisa'&&p.days>=7){
    if(rng()<.35){const cost=Math.min(state.money,250+Math.floor(rng()*850));state.money-=cost;adjust(state,{stress:4,mood:-2});p.last=`Алиса снова передумала насчёт общих планов. На спонтанный выход ушло ${cost} ₽.`;addLog(state,p.last,'bad');}
    if(p.days>=17&&rng()<.13){adjust(state,{stress:8,appeal:-2});p.last='Быт сделал разговоры колючими. Вам обоим стало тесно.';notice(state,'Остывший вечер',p.last,2,[{text:'Поговорить честно',effect:{stress:-3,contacts:1},rapport:{id:'alisa',delta:3},reply:'Удалось услышать друг друга.'},{text:'Отложить разговор',effect:{stress:5},reply:'Молчание стало громче.'}]);}
  }
  if(id==='viktoria'){
    if(netWorth(state)<500000){r.partner=null;p.last='Доходы упали. Виктория сказала, что у вас теперь разные планы.';notice(state,'Разные планы',p.last,3,[{text:'Отпустить',effect:{stress:4},reply:'Она уехала без долгого разговора.'},{text:'Попытаться удержать',effect:{stress:9,mood:-4},reply:'Решение она уже приняла.'}]);addLog(state,p.last,'bad');}
    else if(p.spent>=250000&&rng()<.12){const loss=Math.floor(Math.max(0,state.money)*.5);state.money-=loss;r.partner=null;p.last=`Виктория исчезла вместе с ${loss.toLocaleString('ru-RU')} ₽. Общий счёт оказался не таким уж общим.`;notice(state,'Пустой счёт',p.last,3,[{text:'Сохранить документы',effect:{stress:5,business:1},reply:'Остались выписки и тяжёлый урок.'},{text:'Закрыть эту историю',effect:{stress:8,mood:-5},reply:'Деньги не вернулись.'}]);addLog(state,p.last,'bad');}
    else if(rng()<.28){const cost=Math.min(state.money,1500+Math.floor(rng()*4500));state.money-=cost;p.spent+=cost;adjust(state,{mood:1,stress:2});p.last=`Очередной дорогой вечер: ${cost} ₽.`;addLog(state,p.last,'neutral');}
  }
  if(id==='irina'){
    if(p.days>=6&&rng()<.14){r.partner=null;adjust(state,{health:-32,respect:-14,stress:16});p.last='После тяжёлой ссоры ты попал в больницу. Ирина разнесла по знакомым выдуманные истории.';notice(state,'После ссоры',p.last,4,[{text:'Лечиться и собирать доказательства',effect:{health:8,stress:-3},reply:'Здоровье медленно возвращается.'},{text:'Сразу опровергать слухи',effect:{respect:4,energy:-8},reply:'Часть знакомых поверила тебе.'}]);addLog(state,p.last,'bad');}
    else if(rng()<.35){adjust(state,{stress:5,contacts:-1});p.last='Ирина требовала отчёта о каждом звонке. Разговор затянулся до ночи.';addLog(state,p.last,'bad');}
  }
}
