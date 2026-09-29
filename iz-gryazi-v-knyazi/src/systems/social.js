import { romancePeople } from '../data/romance.js';
import { addLog,clamp } from './state.js';
import { livingPeople,livingRomancePeople,isPresent } from './population.js';

export const activePartners=state=>[...new Set([...(state.romance?.partners||[]),state.romance?.partner].filter(Boolean))];
export function addPartner(romance,id){romance.partners=[...new Set([...(romance.partners||[]),romance.partner,id].filter(Boolean))];romance.partner=id;}
export function dropPartner(romance,id){romance.partners=[...new Set([...(romance.partners||[]),romance.partner].filter(Boolean))].filter(other=>other!==id);romance.partner=romance.partners.at(-1)||null;}
export function socialValue(state,id){
  if(Number.isFinite(state.social?.[id]?.score))return state.social[id].score;
  if(romancePeople.some(person=>person.id===id)||state.population?.residents?.some(person=>person.id===id&&person.romance))return state.romance?.profiles?.[id]?.rapport||0;
  return state.relations?.[id]||0;
}
export function socialGroup(state,id){
  if(activePartners(state).includes(id))return 'romance';
  const score=socialValue(state,id);
  return score<=-20?'enemies':score>=30?'friends':'contacts';
}
export function socialMet(state,id){
  return !!(state.social?.[id]?.met||state.dialogueProgress?.[id]||state.romance?.profiles?.[id]?.met);
}
export function changeSocial(state,id,delta){
  state.social ||= {};
  const old=state.social[id]||{score:socialValue(state,id),met:false,lastDay:0};
  const next={...old,score:clamp(old.score+delta,-100,100),met:true};
  state.social[id]=next;
  return next.score;
}
export function socialDay(state,rng){
  if(state.recentIncident||state.pending||state.romance?.conflict||state.jailDays||state.day<3||state.day%3!==0)return;
  const pool=Object.keys(state.social||{}).filter(id=>isPresent(state,id)&&socialMet(state,id)&&!activePartners(state).includes(id)&&Math.abs(socialValue(state,id))>=20);
  if(!pool.length||rng()>=.28)return;
  const id=pool[Math.floor(rng()*pool.length)],score=socialValue(state,id),person=[...livingPeople(state),...livingRomancePeople(state)].find(p=>p.id===id);
  if(!person)return;
  const friend=score>=30,portrait=romancePeople.find(p=>p.id===id)?.portrait;
  state.recentIncident=friend?{
    title:`Звонок от ${person.name}`,text:`${person.name} узнал о твоём тяжёлом дне и предлагает помощь. Можно принять её или справиться самому.`,portrait,image:portrait===undefined?`person:${id}`:undefined,art:0,socialId:id,
    choices:[{text:'Принять помощь',effect:{energy:9,stress:-3,contacts:1},socialDelta:3,reply:`${person.name} помог разобраться с делами. Сил стало больше.`},{text:'Поблагодарить и справиться самому',effect:{respect:1,mood:2},socialDelta:1,reply:'Ты поблагодарил за внимание и продолжил сам.'}]
  }:{
    title:`Неприятный слух`,text:`После вашей ссоры ${person.name} пересказал знакомым свою версию событий. История уже дошла до тебя.`,portrait,image:portrait===undefined?`person:${id}`:undefined,art:0,socialId:id,
    choices:[{text:'Спокойно поговорить лично',effect:{energy:-5,stress:-2},socialDelta:8,reply:'Разговор не был тёплым, но слух перестал расти.'},{text:'Ответить публично',effect:{respect:2,stress:5,contacts:-1},socialDelta:-7,reply:'Ты ответил жёстко. Люди услышали обе стороны.'}]
  };
  addLog(state,`${person.name}: ${friend?'предложил помощь':'распустил слух'}.`,'story');
}
