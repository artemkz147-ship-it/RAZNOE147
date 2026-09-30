import { romancePeople } from '../data/romance.js';
import { addLog,clamp } from './state.js';
import { livingPeople,livingRomancePeople,isPresent } from './population.js';
import { citizenLife,citizenWorth } from './citizens.js';

export const activePartners=state=>[...new Set([...(state.romance?.partners||[]),state.romance?.partner].filter(Boolean))];
export function addPartner(romance,id){romance.partners=[...new Set([...(romance.partners||[]),romance.partner,id].filter(Boolean))];romance.partner=id;}
export function dropPartner(romance,id){romance.partners=[...new Set([...(romance.partners||[]),romance.partner].filter(Boolean))].filter(other=>other!==id);romance.partner=romance.partners.at(-1)||null;if(romance.conflict?.partnerId===id)romance.conflict=null;if(romance.profiles?.[id])romance.profiles[id].married=false;}
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
  const pool=Object.keys(state.social||{}).filter(id=>isPresent(state,id)&&socialMet(state,id)&&!activePartners(state).includes(id)&&(socialValue(state,id)>=30||socialValue(state,id)<=-20));
  if(!pool.length||rng()>=.28)return;
  const responding=pool.filter(id=>state.citizens?.[id]?.retaliation&&socialValue(state,id)<0);
  const candidates=responding.length?responding:pool;
  const id=candidates[Math.floor(rng()*candidates.length)],score=socialValue(state,id),person=[...livingPeople(state),...livingRomancePeople(state)].find(p=>p.id===id);
  if(!person)return;
  const friend=score>=30,portrait=romancePeople.find(p=>p.id===id)?.portrait;
  const life=citizenLife(state,person),art={portrait,image:portrait===undefined?`person:${id}`:undefined,art:0,socialId:id};
  if(friend&&(life.bankrupt||life.debt>life.cash+10000)&&rng()<.55){
    const amount=Math.round(Math.min(15000,Math.max(3000,life.debt*.2))),paid=Math.min(amount,life.debt);
    state.recentIncident={...art,title:`${person.name}: трудный месяц`,text:`Доходов не хватило на обязательства. ${person.name} просит помочь пережить этот месяц.`,choices:[{text:`Помочь · ${amount.toLocaleString('ru-RU')} ₽`,cost:amount,npcEffect:{debt:-paid,cash:amount-paid},effect:{mood:3},socialDelta:6,reply:'Часть долга закрыта. Человеку стало легче начать заново.'},{text:'Помочь поиском заработка',npcEffect:{reputation:3},effect:{energy:-6,contacts:1},socialDelta:3,reply:'Вы нашли несколько предложений. Теперь предстоит пройти собеседования.'}]};
    addLog(state,`${person.name} попросил помощи после трудного месяца.`,'story');return;
  }
  if(friend&&citizenWorth(life)>250000&&life.cash>20000&&state.money<20000&&rng()<.5){
    const amount=Math.round(Math.min(20000,life.cash*.06));
    state.recentIncident={...art,title:`Поддержка от ${person.name}`,text:`Дела у ${person.name} пошли лучше. Теперь он может помочь тебе пройти трудный период.`,choices:[{text:'Принять поддержку',effect:{money:amount,stress:-4},npcEffect:{cash:-amount},socialDelta:2,reply:`Друг передал ${amount.toLocaleString('ru-RU')} ₽. Умение поддерживать друг друга оказалось полезнее гордости.`},{text:'Поблагодарить',effect:{mood:3,respect:1},socialDelta:2,reply:'Ты поблагодарил за поддержку.'}]};
    addLog(state,`${person.name} предложил финансовую поддержку.`,'story');return;
  }
  if(!friend&&life.retaliation&&rng()<.6){
    const loss=Math.min(state.money,Math.round(1000+Math.max(0,citizenWorth(life))*.003));
    state.recentIncident={...art,title:`Ответный ход ${person.name}`,text:life.bankrupt?`${person.name} винит тебя в разорении. Старая вражда дошла до твоих знакомых.`:`${person.name} ответил на давление и переманил часть твоих контактов.`,choices:[{text:'Искать компромисс',cost:Math.min(5000,loss),npcEffect:{retaliation:-1},effect:{energy:-6,stress:-2},socialDelta:9,reply:'Через общих знакомых удалось немного снизить напряжение.'},{text:'Продолжить конфликт',effect:{money:-loss,contacts:-2,stress:8},socialDelta:-6,reply:`Конфликт обошёлся в ${loss.toLocaleString('ru-RU')} ₽ и часть связей. История ещё не закончилась.`}]};
    addLog(state,`${person.name}: ответ на твой предыдущий ход.`,'bad');return;
  }
  state.recentIncident=friend?{
    title:`Встреча с ${person.name}`,text:`${person.name} узнал о твоём тяжёлом дне и предлагает помощь. Можно принять её или справиться самому.`,portrait,image:portrait===undefined?`person:${id}`:undefined,art:0,socialId:id,
    choices:[{text:'Принять помощь',effect:{energy:9,stress:-3,contacts:1},socialDelta:3,reply:`${person.name} помог разобраться с делами. Сил стало больше.`},{text:'Поблагодарить и справиться самому',effect:{respect:1,mood:2},socialDelta:1,reply:'Ты поблагодарил за внимание и продолжил сам.'}]
  }:{
    title:`Неприятный слух`,text:`После вашей ссоры ${person.name} пересказал знакомым свою версию событий. История уже дошла до тебя.`,portrait,image:portrait===undefined?`person:${id}`:undefined,art:0,socialId:id,
    choices:[{text:'Спокойно поговорить лично',effect:{energy:-5,stress:-2},socialDelta:8,reply:'Разговор не был тёплым, но слух перестал расти.'},{text:'Ответить публично',effect:{respect:2,stress:5,contacts:-1},socialDelta:-7,reply:'Ты ответил жёстко. Люди услышали обе стороны.'}]
  };
  addLog(state,`${person.name}: ${friend?'предложил помощь':'распустил слух'}.`,'story');
}
