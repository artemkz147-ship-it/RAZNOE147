import {adjust,clamp,addLog,addLedger} from './state.js';
import {recordHarm,endLife} from './endings.js';

export function applyOccurrence(s,event){
 const o=event.occurrence;if(!o)return;
 if(o.effect?.health<0)recordHarm(s,event.cause||'injury',event.title+': '+event.text);
 adjust(s,o.effect);
 for(const [key,value] of Object.entries(o.condition||{}))s.conditions[key]=Math.max(s.conditions[key]||0,value);
 if(o.illness)s.vitals.illness=clamp(s.vitals.illness+o.illness,0,10);
 if(o.vehicleFault&&s.vehicle!=='feet'){s.vehicleFaults||={};s.vehicleFaults[s.vehicle]=event.title;}
 if(o.loss){const loss=Math.min(Math.max(0,s.money),o.loss);s.money-=loss;addLedger(s,'Происшествие',-loss,event.title);}
 addLog(s,event.title+': '+(o.log||event.text),'bad');
 if(s.stats.health<=0)endLife(s,event.cause||'injury');
}
export function applyImmediateChoice(s,event,choice){
 if((choice.effect?.health??choice.effects?.health??0)<0)recordHarm(s,event.cause||'injury',event.title+': '+(choice.reply||choice.log||choice.text));
 if(choice.repairVehicle&&s.vehicleFaults)delete s.vehicleFaults[s.vehicle];
}
