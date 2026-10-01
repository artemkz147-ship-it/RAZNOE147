import {endLife} from './endings.js';
import { diets } from '../data/lifestyle.js';
import { clamp,adjust,addLog } from './state.js';
import {recordLifeFactor} from './lifeHistory.js';
import {encounterAllowed,recordEncounter} from './encounters.js';

export const ageOf = state => 30 + Math.floor((state.day-1)/360);
export const calendarOf = state => ({month:Math.floor((state.day-1)/30)%12+1,year:Math.floor((state.day-1)/360)+1,day:(state.day-1)%30+1});

export function receiveMedicalCare(state){
  recordLifeFactor(state,'care','Обратился к врачу: получил лечение и время на восстановление');
  if(state.employment&&(state.vitals.illness||state.conditions.back||state.stats.health<45)){
    state.employment.medicalUntil=Math.max(state.employment.medicalUntil||0,state.day+2);
    addLog(state,'Врач оформил освобождение от работы до дня '+state.employment.medicalUntil+'.','good');
  }
  state.conditions.back=0;
  state.lastHarm=null;
  state.vitals.illness=0;
  state.vitals.immunity=clamp(state.vitals.immunity+18,0,100);
}

export function dailyVitals(state,rng=Math.random,{fed=false,prison=false}={}){
  if(state.death)return;
  const v=state.vitals ||= {nutrition:60,immunity:70,fitness:35,exposure:0,strain:0,illness:0};
  v.unfedDays=fed||state.lastMealDay>0&&state.lastMealDay>=state.day-1?0:(v.unfedDays||0)+1;
  const diet=fed?(diets.find(x=>x.id===state.diet)||diets[1]):null;
  const shelter=!prison&&['station','heating-main'].includes(state.home);
  if(diet?.id==='expired')recordLifeFactor(state,'food','Питался уценёнными и просроченными продуктами',diet.health);
  if(shelter)recordLifeFactor(state,'cold',state.home==='station'?'Ночевал на вокзале без своей комнаты':'Ночевал у теплотрассы');
  if(!fed&&v.nutrition===0)recordLifeFactor(state,'hunger','Оставался без еды с нулевой сытостью');
  if(v.illness>0)recordLifeFactor(state,'illness','Болезнь сохранялась без полного восстановления');
  if(v.illness>0&&state.day%7===0)recordLifeFactor(state,'neglect','Прошла неделя болезни без лечения');
  if(state.lastRoutine?.energy>35||v.strain>65)recordLifeFactor(state,'strain','Регулярная нагрузка оставляла мало времени на восстановление');
  if(prison)recordLifeFactor(state,'prison','Здоровье ухудшалось во время заключения');
  v.nutrition=clamp(v.nutrition+(diet?(diet.id==='expired'?-5:diet.id==='basic'?0:2):-9),0,100);
  v.immunity=clamp(v.immunity+(shelter?-2:1)+(diet?.id==='expired'?-2:diet?1:-3)+(state.conditions?.hangover? -2:0),0,100);
  v.exposure=clamp(v.exposure+(shelter?3:-3),0,100);
  v.strain=clamp(v.strain+(state.stats.stress>65?2:-2)+(state.conditions?.back?2:0),0,100);
  v.fitness=clamp(v.fitness-(state.day%5===0?1:0),0,100);
  adjust(state,diet?{health:diet.health,energy:diet.energy,mood:diet.mood}:{health:-2,energy:-5,mood:-4,stress:3});
  if(!fed&&v.nutrition===0){
    adjust(state,{health:-6,energy:-8,mood:-3});
    if(state.stats.health<=0){endLife(state,prison?'prison':'hunger');return;}
    if(v.unfedDays>=7&&rng()<Math.min(.75,.18+(v.unfedDays-7)*.07)){
      endLife(state,'hunger');return;
    }
    if(v.unfedDays>=2&&!state.recentIncident&&rng()<Math.min(.8,.35+(v.unfedDays-2)*.08))state.recentIncident={title:'Потерял сознание',interruptSkip:true,text:'Несколько дней без еды закончились обмороком. Прохожий вызвал помощь.',art:16,choices:[{text:'Принять помощь',feed:true,effect:{health:5,energy:12},reply:'Тебя напоили и накормили. Силы понемногу возвращаются.'}]};
  }
  if(state.stats.health<=0){endLife(state,prison?'prison':undefined);return;}
  const illnessChance=(v.immunity<25?.055:0)+(v.exposure>45?.035:0)+(v.nutrition<15?.04:0);
  if(encounterAllowed(state,'night-fever',14)&&state.stats.health<55&&rng()<illnessChance){
    recordEncounter(state,'night-fever');
    v.illness=clamp(v.illness+1,0,10);adjust(state,{health:-6,energy:-6});
    if(state.stats.health<=0){endLife(state,prison?'prison':'illness');return;}
    addLog(state,'Ночью поднялась температура. Условия жизни и питание сказались на здоровье.','bad');
    if(!state.recentIncident)state.recentIncident={title:'Ночная температура',interruptSkip:true,text:'Ты проснулся с жаром. Впереди был рабочий день, но тело требует внимания.',art:16,choices:[{text:'Остаться дома и восстановиться',effect:{health:8,energy:12,stress:-3},reply:'Ты дал себе время отлежаться.'},{text:'Сходить к врачу · 1 700 ₽',cost:1700,medical:true,effect:{health:18,energy:5,stress:-5},reply:'Врач назначил лечение и время на восстановление.'}]};
  }
  else if(v.illness>0&&state.stats.health>65)v.illness--;
  const age=ageOf(state);
  if(age>60&&state.day%30===0){const agePressure=Math.min(9,Math.floor((age-60)/5));v.immunity=clamp(v.immunity-agePressure,0,100);if(age>75){adjust(state,{health:-Math.ceil((age-75)/8)});if(state.stats.health<=0){endLife(state,prison?'prison':'age');return;}}}
  if(state.stats.health<=0){endLife(state,prison?'prison':undefined);return;}
  const danger=state.stats.health<18?(0.015+v.illness*.006+v.strain*.0002):0;
  const ageRisk=age>65&&state.stats.health<55?(age-65)*.00008:age>82?(age-82)*.000018:0;
  if(!state.death&&(danger||ageRisk)&&rng()<danger+ageRisk){
    endLife(state,prison?'prison':age>82&&state.stats.health>=40?'age':undefined);
  }
}
