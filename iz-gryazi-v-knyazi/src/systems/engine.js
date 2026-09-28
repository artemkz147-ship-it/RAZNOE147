import { districts,jobs,homes,vehicles,businesses,upgrades } from '../data/world.js';
import { people } from '../data/people.js';
import { nextDialogue } from '../data/dialogues.js';
import { chapters } from '../data/story.js';
import { events } from '../data/events.js';
import { investments } from '../data/investments.js';
import { crimes } from '../data/crime.js';
import { activities } from '../data/activities.js';
import { casinoGames,casinoOutcome,casinoRemaining } from '../data/casino.js';
import { freshState,adjust,addLog,clamp } from './state.js';
import { byId,districtUnlocked,travelPrice,gainSkill,dailySettlement,settleMatureInvestments,netWorth,businessStrategies } from './economy.js';
import { saveGame } from './save.js';

const success = (message,tone='good') => ({ok:true,message,tone});
const fail = message => ({ok:false,message,tone:'bad'});
const homeRank = id => homes.findIndex(h=>h.id===id);

export class GameEngine {
  constructor(state=freshState(),rng=Math.random) {
    this.state=state; this.rng=rng; this.listeners=new Set();
  }
  subscribe(fn) { this.listeners.add(fn); return ()=>this.listeners.delete(fn); }
  emit(result) { saveGame(this.state); for (const fn of this.listeners) fn(this.state,result); return result; }
  guard() {
    if (this.state.jailDays>0) return fail(`Ты под арестом. Осталось ${this.state.jailDays} дн.`);
    if (this.state.pending) return fail('Сначала прими решение в открытой истории.');
    return null;
  }
  spend(cost) { if (this.state.money<cost) return false; this.state.money-=cost; return true; }
  tick(hours) {
    const s=this.state;
    s.hour += hours;
    while (s.hour>=24) {
      s.hour-=24; s.day++;
      dailySettlement(s,{rng:this.rng});
      settleMatureInvestments(s,this.rng);
      if (s.day%6===0) this.crisis();
      if (!s.pending && !s.jailDays && this.rng()<.42) this.queueEvent();
    }
    this.checkStory();
  }
  crisis() {
    const s=this.state;
    if (s.day<12) return;
    const roll=this.rng();
    if (roll<.38 && Object.keys(s.businesses).length) {
      const cost=Math.round(1300+s.day*220+Object.keys(s.businesses).length*700);
      adjust(s,{money:-cost,stress:4});
      addLog(s,`Кризис поставок: срочный расход ${cost.toLocaleString('ru-RU')} ₽.`,'bad');
    } else if (roll<.65) {
      adjust(s,{mood:-4,stress:3});
      addLog(s,'Городская неделя «новых правил»: в итоге всё по-старому, но дороже.','bad');
    }
  }
  queueEvent() {
    const s=this.state;
    const eligible=events.filter(e=>s.day>=e.minDay && (!e.needBusiness||Object.keys(s.businesses).length) && (!e.needVehicle||s.vehicle!=='feet') && !s.eventHistory.slice(-5).includes(e.id));
    if (!eligible.length) return;
    const event=eligible[Math.floor(this.rng()*eligible.length)];
    s.pending={type:'event',id:event.id}; s.eventHistory.push(event.id);
    addLog(s,`Событие: ${event.title}.`,'story');
  }
  meets(chapter) {
    const s=this.state, n=chapter.need;
    return (!n.day||s.day>=n.day) && (!n.money||s.money>=n.money) && (!n.respect||s.stats.respect>=n.respect)
      && (!n.fame||s.stats.fame>=n.fame) && (!n.home||homeRank(s.home)>=homeRank(n.home))
      && (!n.businessCount||Object.keys(s.businesses).length>=n.businessCount);
  }
  checkStory() {
    const s=this.state;
    if (s.pending || s.story>=chapters.length) return;
    const chapter=chapters[s.story];
    if (this.meets(chapter)) { s.pending={type:'story',id:chapter.id}; addLog(s,`Открыта глава: ${chapter.title}.`,'story'); }
  }
  resolveChoice(index) {
    const s=this.state,p=s.pending;
    if (!p) return this.emit(fail('Сейчас нет решения.'));
    const data=p.type==='story'?chapters.find(x=>x.id===p.id):events.find(x=>x.id===p.id);
    const choice=data?.choices[index];
    if (!choice) return this.emit(fail('Такого варианта нет.'));
    if (choice.cost && !this.spend(choice.cost)) return this.emit(fail('На это решение не хватает денег.'));
    adjust(s,choice.effects||choice.effect);
    if (p.type==='story') {
      s.story++; if (choice.flag) s.flags.push(choice.flag);
      if (s.story===chapters.length) s.ending=true;
      addLog(s,`Глава ${p.id}: ${choice.text}.`,'story');
    } else addLog(s,choice.log||choice.text,'story');
    s.pending=null;
    return this.emit(success(p.type==='story'?'История продолжается.':'Решение принято.'));
  }
  travel(id) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state,d=byId(districts,id);
    if (!d) return this.emit(fail('Район не найден.'));
    if (s.district===id) return this.emit(fail('Ты уже здесь.'));
    if (!districtUnlocked(s,d)) return this.emit(fail('Район пока закрыт: нужны уважение и сюжетный прогресс.'));
    const price=travelPrice(s,id),time=Math.max(1,Math.ceil((d.tier+1)*(byId(vehicles,s.vehicle)?.timeFactor||1)));
    if (!this.spend(price)) return this.emit(fail('Не хватает денег на дорогу.'));
    s.district=id; adjust(s,{energy:-Math.max(3,time*4),stress:d.tier>2?1:0}); this.tick(time);
    addLog(s,`Прибыл в район «${d.name}». Дорога: ${price} ₽, ${time} ч.`,'neutral');
    return this.emit(success(`Теперь ты в районе «${d.name}».`));
  }
  jobReady(id) {
    const j=byId(jobs,id),s=this.state;
    if (!j) return fail('Работа не найдена.');
    if (s.district!==j.district) return fail('Эта работа находится в другом районе.');
    if (s.stats.energy<j.energy) return fail(`Нужно ${j.energy} энергии, сейчас ${s.stats.energy}. Сделай передышку (+22) или выспись.`);
    if (s.stats.health<12) return fail('Здоровье слишком низкое.');
    if (s.hour+j.hours>26) return fail('Поздно для этой смены. Выспись.');
    return success('Можно начинать.');
  }
  completeJob(id,score) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const ready=this.jobReady(id); if (!ready.ok) return this.emit(ready);
    const j=byId(jobs,id),s=this.state;
    const performance=clamp(score,0,1);
    const skill=1+(s.skills[j.skill]-1)*.07;
    const mood=.82+s.stats.mood*.003;
    const resilience=.72+s.stats.health*.0025-s.stats.stress*.0015+s.stats.life*.0009;
    const social=j.skill==='charm'?1+s.stats.appeal*.0015+s.stats.contacts*.001:1;
    const notoriety=j.id==='reseller'?1+s.stats.crime*.002:1;
    const reward=Math.round(j.pay*(.48+performance*.85)*skill*mood*resilience*social*notoriety);
    const tip=performance>.83?Math.round(j.pay*.13):0;
    const accident=this.rng()<j.risk*(1-performance*.6)*(1+s.stats.stress*.004+s.stats.crime*.003);
    s.money+=reward+tip; s.totalEarned+=reward+tip; s.jobsDone++;
    adjust(s,{energy:-j.energy,stress:Math.round(2+j.hours*.6-performance*3),mood:performance>.65?2:-2,respect:performance>.74?2:0,health:accident?-Math.ceil(j.energy*.25):0});
    gainSkill(s,j.skill,performance>.75?2:1);
    this.tick(j.hours);
    addLog(s,`${j.name}: заработано ${(reward+tip).toLocaleString('ru-RU')} ₽${accident?', но досталось здоровью':''}.`,accident?'bad':'good');
    return this.emit(success(`+${(reward+tip).toLocaleString('ru-RU')} ₽${tip?' включая чаевые':''}`,accident?'bad':'good'));
  }
  crimeReady(id) {
    const gig=byId(crimes,id),s=this.state;
    if (!gig) return fail('Такого заказа нет.');
    if (s.jailDays) return fail('Сначала выйди на свободу.');
    if (s.pending) return fail('Сначала прими решение в истории.');
    if (s.district!==gig.district) return fail('Этот заказ в другом районе.');
    if (s.stats.energy<gig.energy) return fail(`Нужно ${gig.energy} энергии, сейчас ${s.stats.energy}. Отдохни или выспись.`);
    if (s.stats.health<20) return fail('Со сломанными рёбрами это плохая идея.');
    return success('Можно начинать.');
  }
  completeCrime(id,score) {
    const ready=this.crimeReady(id);if(!ready.ok)return this.emit(ready);
    const gig=byId(crimes,id),s=this.state,performance=clamp(score,0,1);
    const arrestChance=clamp(gig.arrest*(1+s.heat/120)*(1-performance*.38)*(1-s.stats.contacts*.0015),.04,.88);
    const injuryChance=clamp(gig.injury*(1-performance*.32),.02,.7);
    const arrested=this.rng()<arrestChance;
    const injured=this.rng()<injuryChance;
    const payout=Math.round(gig.pay*(.48+performance*.85)*(1+(s.skills[gig.skill]-1)*.045));
    adjust(s,{energy:-gig.energy,stress:arrested?15:7,health:injured?-Math.round(9+gig.energy*.55):0,crime:arrested?2:5,respect:arrested?-4:0});
    s.heat=clamp(s.heat+gig.heat,0,100);s.crimesDone++;
    this.tick(gig.hours);
    if(arrested){
      s.arrestCount++;s.jailDays+=gig.jail;s.money-=gig.fine;s.heat=Math.max(10,s.heat-25);
      addLog(s,`${gig.name}: задержание. Штраф ${gig.fine.toLocaleString('ru-RU')} ₽, срок ${gig.jail} дн.${injured?' Здоровье пострадало.':''}`,'bad');
      return this.emit(success(`Задержание: −${gig.fine.toLocaleString('ru-RU')} ₽ и ${gig.jail} дн. ареста.`, 'bad'));
    }
    s.money+=payout;s.totalEarned+=payout;
    addLog(s,`${gig.name}: получено ${payout.toLocaleString('ru-RU')} ₽. Розыск +${gig.heat}.${injured?' Пришлось лечить травму.':''}`,injured?'bad':'good');
    return this.emit(success(`+${payout.toLocaleString('ru-RU')} ₽${injured?' · получена травма':''}`,injured?'bad':'good'));
  }
  playCasino(id,amount) {
    const blocked=this.guard();if(blocked)return this.emit(blocked);
    const s=this.state,game=byId(casinoGames,id),stake=Number(amount);
    if(!game)return this.emit(fail('Такой игры в казино нет.'));
    if(!Number.isSafeInteger(stake)||stake<50)return this.emit(fail('Минимальная ставка — 50 ₽.'));
    if(s.stats.energy<4)return this.emit(fail('Нужны 4 энергии. Переведи дух или выспись.'));
    if(s.money<stake)return this.emit(fail('Не хватает денег на ставку.'));
    if(stake>casinoRemaining(s))return this.emit(fail('Дневной лимит ставок исчерпан.'));
    if(s.casinoDaily.day!==s.day)s.casinoDaily={day:s.day,wagered:0,limit:casinoRemaining(s)};
    else s.casinoDaily.limit ||= casinoRemaining(s)+s.casinoDaily.wagered;
    const outcome=casinoOutcome(id,this.rng),returned=Math.round(stake*outcome.gross),net=returned-stake;
    s.money+=net;s.casinoDaily.wagered+=stake;s.casino.rounds++;s.casino.wagered+=stake;s.casino.returned+=returned;
    if(net>0){s.casino.wins++;s.totalEarned+=net;}
    adjust(s,{energy:-4,stress:net<0?4:-2,mood:net<0?-3:3});
    const label=`${game.name}: ${outcome.result}. ${net>=0?'+':''}${net.toLocaleString('ru-RU')} ₽.`;
    s.casino.history.unshift({day:s.day,game:game.name,result:outcome.result,net});s.casino.history=s.casino.history.slice(0,8);
    this.tick(1);addLog(s,label,net>=0?'good':'bad');
    return this.emit(success(label,net>=0?'good':'bad'));
  }
  serveSentence() {
    const s=this.state;if(!s.jailDays)return this.emit(fail('Ты уже на свободе.'));
    s.jailDays--;adjust(s,{health:-3,mood:-5,stress:3,energy:12});
    this.tick(24);addLog(s,s.jailDays?`День в изоляторе. Осталось ${s.jailDays} дн.`:'Вышел на свободу. Город делает вид, что ничего не было.','bad');
    return this.emit(success(s.jailDays?`Осталось ${s.jailDays} дн. ареста.`:'Ты снова на свободе.',s.jailDays?'bad':'good'));
  }
  activity(id) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state;
    const a=byId(activities,id); if (!a) return this.emit(fail('Неизвестное действие.'));
    if (a.district && a.district!==s.district) return this.emit(fail('Это занятие доступно в другом районе.'));
    if (id==='rest' && s.lastRestDay===s.day) return this.emit(fail('Передышка сегодня уже была. Выспись, чтобы вернуть силы.'));
    if (!this.spend(a.cost)) return this.emit(fail('Не хватает денег.'));
    const effect={...a.effect};
    if (a.restoreSleep) {
      const home=byId(homes,s.home);
      const target=clamp(75+Math.round(home.restore*.2)+Math.round(s.stats.life*.08),0,100);
      effect.energy=Math.max(0,target-s.stats.energy);
    }
    if(id==='rest')s.lastRestDay=s.day;
    adjust(s,effect); this.tick(a.hours); addLog(s,a.description,a.cost?'neutral':'good');
    return this.emit(success(a.description));
  }
  buy(kind,id) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state,items={home:homes,vehicle:vehicles,upgrade:upgrades,business:businesses}[kind],item=byId(items||[],id);
    if (!item) return this.emit(fail('Такого товара нет.'));
    if (kind==='business' && !districtUnlocked(s,byId(districts,item.district))) return this.emit(fail('Сначала открой район этого бизнеса.'));
    if (kind==='home' && s.ownedHomes.includes(id) || kind==='vehicle' && s.ownedVehicles.includes(id) || kind==='upgrade' && s.upgrades.includes(id) || kind==='business' && s.businesses[id]) return this.emit(fail('Это уже куплено.'));
    if (!this.spend(item.price)) return this.emit(fail('Не хватает денег на покупку.'));
    if (kind==='home') { s.ownedHomes.push(id); s.home=id; adjust(s,{respect:Math.ceil(item.prestige*.25),mood:6}); }
    if (kind==='vehicle') { s.ownedVehicles.push(id); s.vehicle=id; adjust(s,{respect:Math.ceil(item.prestige*.2),appeal:Math.ceil(item.prestige*.12)}); }
    if (kind==='upgrade') { s.upgrades.push(id); s.skills[item.stat]+=item.amount; adjust(s,{respect:2}); }
    if (kind==='business') { s.businesses[id]={level:1,staff:false,condition:100,strategy:'normal'}; adjust(s,{respect:6,business:3,stress:4}); }
    this.tick(1); addLog(s,`Куплено: ${item.name} за ${item.price.toLocaleString('ru-RU')} ₽.`,'good');
    return this.emit(success(`Куплено: ${item.name}.`));
  }
  equip(kind,id) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state,owned=kind==='home'?s.ownedHomes:s.ownedVehicles;
    if (!owned?.includes(id)) return this.emit(fail('Сначала купи это.'));
    s[kind]=id; this.tick(1);
    return this.emit(success('Выбор изменён.'));
  }
  manageBusiness(id,action) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state,firm=s.businesses[id],item=byId(businesses,id);
    if (!firm||!item) return this.emit(fail('Бизнес не найден.'));
    if (action.startsWith('strategy-')) {
      const choice=action.slice(9);
      if (!businessStrategies[choice]) return this.emit(fail('Неизвестный режим работы.'));
      if ((firm.strategy||'normal')===choice) return this.emit(fail('Этот режим уже выбран.'));
      firm.strategy=choice;this.tick(1);
      addLog(s,`${item.name}: теперь работает в режиме «${businessStrategies[choice].label}».`,'neutral');
      return this.emit(success(`Режим бизнеса: ${businessStrategies[choice].label}.`));
    }
    if (action==='upgrade') { const cost=Math.round(item.upgrade*Math.pow(1.65,firm.level-1)); if(firm.level>=5) return this.emit(fail('Достигнут предел развития.')); if(!this.spend(cost))return this.emit(fail('Не хватает денег.')); firm.level++; adjust(s,{business:3}); }
    else if (action==='staff') { if(firm.staff)return this.emit(fail('Команда уже нанята.')); if(!this.spend(item.staff*3))return this.emit(fail('Не хватает денег.')); firm.staff=true; adjust(s,{respect:2}); }
    else if (action==='repair') { const cost=Math.round(item.upkeep*3); if(firm.condition>=100)return this.emit(fail('Ремонт пока не нужен.')); if(!this.spend(cost))return this.emit(fail('Не хватает денег.')); firm.condition=100; }
    else return this.emit(fail('Неизвестное действие.'));
    this.tick(2); addLog(s,`${item.name}: ${action==='upgrade'?'расширение':action==='staff'?'наём команды':'ремонт'}.`,'good');
    return this.emit(success(`${item.name}: дело развивается.`));
  }
  person(id,action,choiceIndex) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state,p=byId(people,id);
    if (!p||s.district!==p.district && !(p.id==='valera' && s.district==='market') && !(p.id==='azamat' && s.district==='industrial') && !(p.id==='lida' && s.district==='center') && !(p.id==='vera' && s.district==='glass') && !(p.id==='artur' && s.district==='heights')) return this.emit(fail('Этого человека здесь нет.'));
    if (s.stats.respect<p.threshold) return this.emit(fail('Он пока не хочет разговаривать.'));
    const rel=s.relations[id]||0;
    if (action==='talk') {
      const scene=nextDialogue(s,id),choice=scene?.choices[choiceIndex];
      if(!scene)return this.emit(fail('Все темы уже обсуждены. Новые дела с этим человеком доступны через услуги.'));
      if(!choice)return this.emit(fail('Выбери ответ в разговоре.'));
      if(s.stats.energy<5)return this.emit(fail('На разговор нужны 5 энергии. Переведи дух (+22) или выспись.'));
      if(choice.cost&&!this.spend(choice.cost))return this.emit(fail('На этот ответ не хватает денег.'));
      const before=s.relations[id]||0;
      adjust(s,choice.effect);
      adjust(s,{energy:-5});
      s.relations[id]=clamp(before+choice.relation,0,100);
      s.dialogueProgress[id]=(s.dialogueProgress[id]||0)+1;
      s.dialogueLast[id]=choice.reply;
      this.tick(1);
      addLog(s,`${p.name} — ${scene.topic}: ${choice.reply} Отношения +${s.relations[id]-before}.`,'story');
      return this.emit({...success(choice.reply),dialogueReply:choice.reply,dialogueTopic:scene.topic,relationGain:s.relations[id]-before,effects:choice.effect});
    }
    if (action==='favor') { if(rel<2)return this.emit(fail('Сначала наладь отношения в разговоре.')); const price=Math.round(p.cost*(1-Math.min(.22,s.stats.contacts*.002+s.stats.appeal*.001))); if(!this.spend(price))return this.emit(fail('Не хватает денег.')); adjust(s,p.effect); s.relations[id]=clamp(rel+p.relation,0,100); this.tick(2); addLog(s,`${p.name}: ${p.favor}. Услуга обошлась в ${price.toLocaleString('ru-RU')} ₽.`,'good'); return this.emit(success(`${p.name} оценил помощь.`)); }
    return this.emit(fail('Неизвестный выбор.'));
  }
  invest(id,amount) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state,item=byId(investments,id),value=Math.floor(Number(amount));
    if (!item||!Number.isFinite(value)||value<item.min) return this.emit(fail('Сумма ниже минимальной.'));
    if (!this.spend(value)) return this.emit(fail('Не хватает денег для вложения.'));
    s.investments ||= [];
    s.investments.push({id,amount:value,maturity:s.day+item.days});
    this.tick(1); addLog(s,`Вложено ${value.toLocaleString('ru-RU')} ₽: ${item.name}.`,'neutral');
    return this.emit(success('Вложение принято. Результат придёт позже.'));
  }
  payDebt(amount) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const n=Math.max(0,Math.floor(Number(amount)));
    if (!n||!this.state.debt) return this.emit(fail('Погашать нечего.'));
    if (this.state.money<n) return this.emit(fail('Не хватает денег.'));
    const paid=Math.min(n,this.state.debt);
    this.state.money-=paid; this.state.debt-=paid; this.tick(1);
    return this.emit(success(`Погашено ${paid.toLocaleString('ru-RU')} ₽ долга.`));
  }
  replaceState(state) { this.state=state; return this.emit(success('Сохранение загружено.')); }
  summary() { return {netWorth:netWorth(this.state),story:chapters[this.state.story]||null}; }
}
