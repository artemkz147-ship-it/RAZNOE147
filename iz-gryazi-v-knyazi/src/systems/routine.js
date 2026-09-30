import { careers } from '../data/lifestyle.js';
import { businesses } from '../data/world.js';
import { adjust,addLog,addLedger } from './state.js';
export const careerShift=id=>({janitor:{start:6,end:15,energy:25,days:6},shop:{start:9,end:18,energy:22,days:6},mechanic:{start:8,end:17,energy:28,days:5},clerk:{start:9,end:18,energy:15,days:5},manager:{start:9,end:19,energy:21,days:5},director:{start:10,end:19,energy:19,days:5}}[id]);
export const salaryFor=(s,c)=>Math.round(c.salary*(.8+Math.min(.4,(s.skills[c.skill]-1)*.04))*(1+(s.careerRaises?.[c.id]||0)*.12));
export const businessDuty=(config,firm)=>firm.staff?{hours:1,energy:3}:{hours:['stall','canteen','cafe','pickup','laundry'].includes(config.id)?8:config.district==='yard'||config.district==='market'?6:4,energy:config.district==='industrial'?18:14};
export function hireCareer(s,id){
  s.employment={id,since:s.day,nextPay:s.day+30,worked:0,accrued:0,lastShift:0,reviewDay:s.day+30};
}
export function dismissCareer(s,reason,misconduct=false){
  const e=s.employment;if(!e)return;
  const career=careers.find(c=>c.id===e.id);
  if(e.accrued){const pay=Math.round(e.accrued);s.money+=pay;s.totalEarned+=pay;addLedger(s,'Расчёт при увольнении',pay,career.name);}
  s.careerBans||={};s.careerBans[e.id]={until:misconduct?null:s.day+90,reason};s.employment=null;
  addLog(s,`${career.name}: ${reason}`,'bad');
  if(!s.recentIncident)s.recentIncident={title:'Увольнение',text:reason,art:10};
}
export function routineDay(s,rng,{skip=false}={}){
  const e=s.employment,c=e&&careers.find(x=>x.id===e.id);let hours=0,energy=0;
  if(c&&e.lastShift!==s.day){
    e.lastShift=s.day;const shift=careerShift(c.id),weekday=(s.day-e.since)%7;
    if(weekday<shift.days){
      const exhausted=s.stats.health<25||s.vitals.illness>=4||s.conditions.hangover>0;
      if(exhausted){e.absences=(e.absences||0)+1;addLog(s,`${c.name}: пропустил смену из-за самочувствия.`,'bad');}
      else{e.worked++;e.lastWorkedDay=s.day;e.accrued+=salaryFor(s,c)/(Array.from({length:30},(_,i)=>i+1).filter(i=>i%7<shift.days).length);hours=shift.end;energy+=shift.energy;s.jobsDone++;s.vitals.strain=Math.min(100,s.vitals.strain+(c.id==='mechanic'||c.id==='janitor'?4:1));addLog(s,`Проснулся, отработал: ${c.name}. Закончил в ${shift.end}:00, энергия −${shift.energy}.`,'neutral');}
    }
    if(s.day>=e.nextPay){const pay=Math.round(e.accrued);s.money+=pay;s.totalEarned+=pay;addLedger(s,'Зарплата',pay,c.name);addLog(s,`${c.name}: зарплата ${pay} ₽.`,'good');e.accrued=0;e.nextPay+=30;s.careerMonths++;}
    if(s.day>=e.reviewDay){
      e.reviewDay+=30;const quality=(s.stats.health+s.stats.mood+s.stats.appeal+s.stats.respect)/4-s.stats.stress*.3;
      if((e.absences||0)>=5||rng()<.015+(quality<35?.08:0)){dismissCareer(s,'Из-за пропусков или качества работы договор расторгнут. Повторное собеседование возможно через три месяца.');}
      else if(quality>60&&rng()<.05){s.careerRaises||={};s.careerRaises[c.id]=(s.careerRaises[c.id]||0)+1;s.recentIncident||={title:'Повышение',text:'Начальник повысил оклад на 12%.',art:10};addLog(s,'Оклад вырос на 12%.','good');}
    }
  }
  for(const [id,firm] of Object.entries(s.businesses)){
    if(firm.lastDuty===s.day||firm.paused)continue;
    firm.lastDuty=s.day;const config=businesses.find(x=>x.id===id);if(!config)continue;
    const duty=businessDuty(config,firm);hours+=duty.hours;energy+=duty.energy;
    if(hours>22){firm.capacity=.45;addLog(s,`${config.name}: не хватило времени, часть заказов потеряна.`,'bad');}
    else firm.capacity=1;
    addLog(s,`${config.name}: ${firm.staff?'проверка работы команды':'личная работа'}, ${duty.hours} ч, энергия −${duty.energy}.`,'neutral');
  }
  adjust(s,{energy:-energy,stress:energy>35?4:0});

  s.lastRoutine={day:s.day,hours:Math.min(23,hours),energy};
}
