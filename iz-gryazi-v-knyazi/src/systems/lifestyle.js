import { diets } from '../data/lifestyle.js';
import { clamp,adjust,addLog } from './state.js';

export const ageOf = state => 30 + Math.floor((state.day-1)/360);
export const calendarOf = state => ({month:Math.floor((state.day-1)/30)%12+1,year:Math.floor((state.day-1)/360)+1,day:(state.day-1)%30+1});

export function dailyVitals(state,rng=Math.random){
  const v=state.vitals ||= {nutrition:60,immunity:70,fitness:35,exposure:0,strain:0,illness:0};
  const diet=diets.find(x=>x.id===state.diet)||diets[1];
  const shelter=state.home==='sofa'||state.home==='hostel';
  v.nutrition=clamp(v.nutrition+(diet.id==='expired'?-5:diet.id==='basic'?0:2),0,100);
  v.immunity=clamp(v.immunity+(shelter?-2:1)+(diet.id==='expired'?-2:1)+(state.conditions?.hangover? -2:0),0,100);
  v.exposure=clamp(v.exposure+(shelter?3:-3),0,100);
  v.strain=clamp(v.strain+(state.stats.stress>65?2:-2)+(state.conditions?.back?2:0),0,100);
  v.fitness=clamp(v.fitness-(state.day%5===0?1:0),0,100);
  adjust(state,{health:diet.health,energy:diet.energy,mood:diet.mood});
  const illnessChance=(v.immunity<25?.055:0)+(v.exposure>45?.035:0)+(v.nutrition<15?.04:0);
  if(state.stats.health<55&&rng()<illnessChance){
    v.illness=clamp(v.illness+1,0,10);adjust(state,{health:-6,energy:-6});
    addLog(state,'Ночью поднялась температура. Условия жизни и питание сказались на здоровье.','bad');
    if(!state.recentIncident)state.recentIncident={title:'Ночная температура',text:'Ты проснулся с жаром. Впереди был рабочий день, но тело требует внимания.',art:0,choices:[{text:'Остаться дома и восстановиться',effect:{health:8,energy:12,stress:-3},reply:'Ты дал себе время отлежаться.'},{text:'Сходить к врачу · 1 700 ₽',cost:1700,effect:{health:18,energy:5,stress:-5},reply:'Врач помог быстрее прийти в себя.'}]};
  }
  else if(v.illness>0&&state.stats.health>65)v.illness--;
  const age=ageOf(state);
  if(age>60&&state.day%30===0){const agePressure=Math.min(9,Math.floor((age-60)/5));v.immunity=clamp(v.immunity-agePressure,0,100);if(age>75)adjust(state,{health:-Math.ceil((age-75)/8)});}
  const danger=state.stats.health<18?(0.015+v.illness*.006+v.strain*.0002):0;
  const ageRisk=age>65&&state.stats.health<55?(age-65)*.00008:age>82?(age-82)*.000018:0;
  if(!state.death&&(danger||ageRisk)&&rng()<danger+ageRisk){
    state.death={day:state.day,age,cause:v.illness?'Осложнения болезни':'Подорванное здоровье'};
    addLog(state,`${state.death.cause}. Жизнь закончилась в ${age} лет.`,'bad');
  }
}
