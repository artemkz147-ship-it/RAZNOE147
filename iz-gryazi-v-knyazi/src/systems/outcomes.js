import {adjust,clamp,addLog,addLedger} from './state.js';
import {dismissCareer} from './routine.js';
import {changeSocial} from './social.js';
import {receiveMedicalCare} from './lifestyle.js';
import {recordHarm,endLife} from './endings.js';
import {recordLifeFactor} from './lifeHistory.js';

export function choiceOutcome(s,event,choice,rng,before={money:s.money,health:s.stats.health,energy:s.stats.energy}){
 const branch=choice.risk?(rng()<choice.risk.chance?choice.risk.bad:choice.good):null;
 const consequence=branch||choice;
 if(branch){adjust(s,branch.effect);if(branch.effect?.money)addLedger(s,'Последствия решения',branch.effect.money,event.title);}
 if(consequence.loss){const loss=Math.min(Math.max(0,s.money),consequence.loss);s.money-=loss;if(loss)addLedger(s,'Происшествие',-loss,event.title);}
 if(consequence.effect?.health<0)recordHarm(s,consequence.cause||event.cause||'injury',event.title+': '+consequence.reply);
 if(consequence.neglect)recordLifeFactor(s,'neglect',event.title+': не обработал травму');
 for(const [key,value] of Object.entries(consequence.condition||{}))s.conditions[key]=Math.max(s.conditions[key]||0,value);
 if(consequence.illness)s.vitals.illness=clamp(s.vitals.illness+consequence.illness,0,10);
 if(consequence.medicalLeave&&s.employment)s.employment.medicalUntil=Math.max(s.employment.medicalUntil||0,s.day+consequence.medicalLeave);
 if(consequence.medical)receiveMedicalCare(s);
 if(consequence.fire&&s.employment){dismissCareer(s,consequence.reply,true);if(s.recentIncident?.title==='Увольнение')s.recentIncident=null;}
 if(consequence.pause&&s.businesses[event.firmId])s.businesses[event.firmId].paused=true;
 if(consequence.sentence){s.jailDays+=consequence.sentence;recordLifeFactor(s,'prison',event.title+': назначено заключение');}
 if(choice.feed){s.lastMealDay=s.day;s.vitals.unfedDays=0;s.vitals.nutrition=clamp(s.vitals.nutrition+40,0,100);}
 if(choice.skill)s.skills[choice.skill]++;
 if(choice.socialId&&choice.socialDelta)changeSocial(s,choice.socialId,choice.socialDelta);
 if(choice.hours){s.hour=Math.min(23,s.hour+choice.hours);addLog(s,'Дополнительная работа: '+choice.hours+' ч.','neutral');}
 const text=consequence.reply||consequence.log||choice.reply||choice.log||'Решение принято.';
 addLog(s,event.title+': '+text,branch===choice.risk?.bad?'bad':'story');
 if(s.stats.health<=0){endLife(s,consequence.cause||event.cause);return;}
 s.outcome={title:consequence.title||'Итог: '+event.title,text,scene:consequence.scene||event.scene,day:s.day,tone:branch===choice.risk?.bad?'bad':'neutral',changes:{money:s.money-before.money,health:s.stats.health-before.health,energy:s.stats.energy-before.energy}};
}
