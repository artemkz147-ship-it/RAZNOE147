import { districts,jobs,homes,vehicles,businesses,upgrades } from '../data/world.js';
import { people } from '../data/people.js';
import { nextDialogue } from '../data/dialogues.js';
import { successRoute,successRoutes } from '../data/goals.js';
import { events } from '../data/events.js';
import { incidents } from '../data/incidents.js';
import { investments } from '../data/investments.js';
import { crimes } from '../data/crime.js';
import { activities } from '../data/activities.js';
import { casinoGames,casinoOutcome,casinoRemaining } from '../data/casino.js';
import { createCasinoTable,actCasinoTable } from './casinoTable.js';
import { romancePeople,romanceAccess,romanceGifts,giftTaste } from '../data/romance.js';
import { romanceDay,romanceConflictChoices } from './romance.js';
import { freshState,adjust,addLog,clamp } from './state.js';
import { byId,districtUnlocked,travelOptions,gainSkill,dailySettlement,settleMatureInvestments,netWorth,businessStrategies } from './economy.js';
import { saveGame } from './save.js';

const success = (message,tone='good') => ({ok:true,message,tone});
const fail = message => ({ok:false,message,tone:'bad'});
const homeRank = id => homes.findIndex(h=>h.id===id);

export class GameEngine {
  constructor(state=freshState(),rng=Math.random) {
    this.state=state; this.rng=rng; this.listeners=new Set();
  }
  subscribe(fn) { this.listeners.add(fn); return ()=>this.listeners.delete(fn); }
  emit(result) { const s=this.state;s.achievedRoutes ||= [];for(const route of successRoutes(s)){if(!s.achievedRoutes.includes(route.id)){s.achievedRoutes.push(route.id);addLog(s,`Достигнут путь: ${route.name}. Другие направления можно развивать одновременно.`,'story');}}s.ending=s.achievedRoutes.length>0;saveGame(s); for (const fn of this.listeners) fn(s,result); return result; }
  guard() {
    if (this.state.jailDays>0) return fail(`Ты под арестом. Осталось ${this.state.jailDays} дн.`);
    if (this.state.pending) return fail('Сначала прими решение в открытой истории.');
    if (this.state.recentIncident) return fail('Сначала разберись с неожиданным событием.');
    if (this.state.romance?.conflict) return fail('Сначала закончи личный разговор.');
    if (this.state.casinoTable) return fail('Сначала закончи партию за игровым столом.');
    return null;
  }
  spend(cost) { if (this.state.money<cost) return false; this.state.money-=cost; return true; }
  tick(hours) {
    const s=this.state;
    s.hour += hours;
    while (s.hour>=24) {
      s.hour-=24; s.day++;
      dailySettlement(s,{rng:this.rng});
      romanceDay(s,this.rng);
      settleMatureInvestments(s,this.rng);
      if (s.day%6===0) this.crisis();
      if (!s.pending && !s.jailDays && this.rng()<.42) this.queueEvent();
    }
  }
  maybeIncident(context,chance) {
    const s=this.state;
    if(s.recentIncident||this.rng()>=chance)return null;
    const pool=incidents.filter(item=>item.context===context&&(!item.test||item.test(s))&&!s.incidentHistory.slice(-3).includes(item.id));
    if(!pool.length)return null;
    const item=pool[Math.floor(this.rng()*pool.length)];
    s.recentIncident={id:item.id};s.incidentHistory.push(item.id);
    s.incidentHistory=s.incidentHistory.slice(-30);
    addLog(s,`Случай: ${item.title}.`,'story');
    return item;
  }
  resolveIncident(index) {
    const s=this.state,item=incidents.find(x=>x.id===s.recentIncident?.id);
    if(s.recentIncident&&!s.recentIncident.id){
      const custom=s.recentIncident,choice=custom.choices?.[index];
      if(choice?.cost&&!this.spend(choice.cost))return this.emit(fail('На это не хватает денег.'));
      if(choice){adjust(s,choice.effect);if(choice.rapport){const profile=s.romance?.profiles[choice.rapport.id];if(profile)profile.rapport=clamp(profile.rapport+choice.rapport.delta,0,100);}addLog(s,`${custom.title}: ${choice.reply}`,'story');}
      s.recentIncident=null;return this.emit(success(choice?.reply||'Продолжить.'));
    }
    if(!item)return this.emit(fail('Событие уже завершилось.'));
    const choice=item.choices[index];
    if(!choice)return this.emit(fail('Выбери решение.'));
    if(choice.cost&&!this.spend(choice.cost))return this.emit(fail('На это не хватает денег.'));
    adjust(s,choice.effect);
    for(const [key,delta] of Object.entries(choice.condition||{}))s.conditions[key]=clamp((s.conditions[key]||0)+delta,0,100);
    s.recentIncident=null;
    addLog(s,`${item.title}: ${choice.reply}`,(choice.effect?.health||0)<0?'bad':'neutral');
    return this.emit(success(choice.reply,(choice.effect?.health||0)<0?'bad':'good'));
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
  resolveChoice(index) {
    const s=this.state,p=s.pending;
    if (!p) return this.emit(fail('Сейчас нет решения.'));
    const data=events.find(x=>x.id===p.id);
    const choice=data?.choices[index];
    if (!choice) return this.emit(fail('Такого варианта нет.'));
    if (choice.cost && !this.spend(choice.cost)) return this.emit(fail('На это решение не хватает денег.'));
    adjust(s,choice.effects||choice.effect);
    addLog(s,choice.log||choice.text,'story');
    s.pending=null;
    return this.emit(success('Решение принято.'));
  }
  travel(id,mode='walk') {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state,d=byId(districts,id);
    if (!d) return this.emit(fail('Район не найден.'));
    if (s.district===id) return this.emit(fail('Ты уже здесь.'));
    if (!districtUnlocked(s,d)) return this.emit(fail('Район пока закрыт: нужны деньги, уважение или связи.'));
    const trip=travelOptions(s,id).find(x=>x.id===mode);
    if(!trip)return this.emit(fail('Такого способа добраться нет.'));
    if(s.stats.energy<trip.energy)return this.emit(fail(`Нужно ${trip.energy} энергии. Передохни или выспись.`));
    if(!this.spend(trip.cost))return this.emit(fail('Не хватает денег на билет или топливо. Можно пойти пешком.'));
    const distance=Math.abs(d.tier-(byId(districts,s.district)?.tier||0));
    const caught=mode==='fare-dodge'&&this.rng()<trip.risk;
    s.district=id;s.visitedDistricts ||= ['yard'];if(!s.visitedDistricts.includes(id))s.visitedDistricts.push(id);adjust(s,{energy:-trip.energy,stress:caught?5:0});
    if(mode==='walk'){s.conditions.walkTrips++;s.conditions.shoes=clamp(s.conditions.shoes-(s.upgrades.includes('boots')?3:6)*Math.max(1,distance),0,100);}
    this.tick(trip.hours);
    if(caught){
      if(s.money>=trip.fine){s.money-=trip.fine;addLog(s,`Контролёр поймал без билета: штраф ${trip.fine} ₽ (десять билетов).`,'bad');s.recentIncident={title:'Проверка билета',text:`Контролёр выписал штраф ${trip.fine} ₽. Поездка оказалась дороже десяти билетов.`,art:2};}
      else {s.jailDays+=1;addLog(s,'Контролёр поймал без билета. Штраф оплатить нечем — сутки ареста.','bad');s.recentIncident={title:'Билет оказался дорогим',text:'Денег на штраф не было. Тебя отправили под арест на сутки.',art:2};}
    } else this.maybeIncident(mode==='walk'?'walk':mode==='bus'?'bus':'ride',trip.risk);
    addLog(s,`${trip.name}: ${d.name}, ${trip.hours} ч, ${trip.cost} ₽, энергия −${trip.energy}.`,'neutral');
    return this.emit(success(`Теперь ты в районе «${d.name}». ${trip.name}: ${trip.cost} ₽, ${trip.hours} ч.`,caught?'bad':'good'));
  }
  jobReady(id) {
    const j=byId(jobs,id),s=this.state;
    if(s.recentIncident)return fail('Сначала реши, что делать в неожиданной ситуации.');
    if (!j) return fail('Работа не найдена.');
    if (s.district!==j.district) return fail('Эта работа находится в другом районе.');
    if (s.stats.energy<j.energy) return fail(`Нужно ${j.energy} энергии, сейчас ${s.stats.energy}. Сделай передышку (+22) или выспись.`);
    if (s.stats.health<12) return fail('Здоровье слишком низкое.');
    if(['center','glass','heights'].includes(j.district)&&!['clean-shirt','office-suit','tailored-suit','cashmere-coat'].some(x=>s.upgrades.includes(x)))return fail('Для этой работы нужна хотя бы чистая рубашка. Купи её в разделе «Вещи».');
    if(s.conditions.hangover>0&&['center','glass','heights'].includes(j.district))return fail('С перегаром на эту смену не пустят. Выспись и восстановись.');
    if(s.conditions.back>0&&['loader','warehouse','packing','eventsetup'].includes(j.id))return fail('Спина ещё болит после тяжёлой смены. Отдохни или сходи к врачу.');
    if(s.conditions.shoes<15&&['courier','leaflets','streetpromo'].includes(j.id))return fail('Для смены на ногах ботинки уже не годятся. Почини их во дворе.');
    if(j.id==='beg'&&['office-suit','tailored-suit','cashmere-coat'].some(x=>s.upgrades.includes(x)))return fail('В дорогом костюме прохожие не верят, что тебе нужна мелочь.');
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
    const accident=this.rng()<j.risk*(1-performance*.6)*(1+s.stats.stress*.004+s.stats.crime*.003+(j.id==='loader'?s.conditions.loaderShifts*.08:0));
    s.money+=reward+tip; s.totalEarned+=reward+tip; s.jobsDone++;
    adjust(s,{energy:-j.energy,stress:Math.round(2+j.hours*.6-performance*3),mood:performance>.65?2:-2,respect:performance>.74?2:0,health:accident?-Math.ceil(j.energy*.25):0});
    gainSkill(s,j.skill,performance>.75?2:1);
    this.tick(j.hours);
    addLog(s,`${j.name}: заработано ${(reward+tip).toLocaleString('ru-RU')} ₽${accident?', но досталось здоровью':''}.`,accident?'bad':'good');
    if(j.id==='loader')s.conditions.loaderShifts++;
    this.maybeIncident(j.id==='loader'?'loader':['center','glass','heights'].includes(j.district)?'office':'job',j.id==='loader'?Math.min(.42,.08+s.conditions.loaderShifts*.045):.13);
    return this.emit(success(`+${(reward+tip).toLocaleString('ru-RU')} ₽${tip?' включая чаевые':''}`,accident?'bad':'good'));
  }
  crimeReady(id) {
    const gig=byId(crimes,id),s=this.state;
    if(s.recentIncident)return fail('Сначала реши, что делать в неожиданной ситуации.');
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
    const arrestChance=clamp(gig.arrest*(1-performance*.38)*(1-s.stats.contacts*.0015),.04,.88);
    const injuryChance=clamp(gig.injury*(1-performance*.32),.02,.7);
    const arrested=this.rng()<arrestChance;
    const injured=this.rng()<injuryChance;
    const payout=Math.round(gig.pay*(.48+performance*.85)*(1+(s.skills[gig.skill]-1)*.045));
    adjust(s,{energy:-gig.energy,stress:arrested?15:7,health:injured?-Math.round(9+gig.energy*.55):0,crime:arrested?2:5,respect:arrested?-4:0});
    s.heat=0;s.crimesDone++;
    this.tick(gig.hours);
    if(arrested){
      s.arrestCount++;const penalty=this.rng();
      if(penalty<.36){s.jailDays+=gig.jail;s.money-=gig.fine;addLog(s,`${gig.name}: задержание, штраф ${gig.fine.toLocaleString('ru-RU')} ₽ и ${gig.jail} дн. ареста.`,'bad');return this.emit(success(`Задержание: штраф ${gig.fine.toLocaleString('ru-RU')} ₽ и ${gig.jail} дн. ареста.`,'bad'));}
      if(penalty<.78){s.money-=gig.fine;addLog(s,`${gig.name}: поймали и отпустили после штрафа ${gig.fine.toLocaleString('ru-RU')} ₽.`,'bad');return this.emit(success(`Задержание: штраф ${gig.fine.toLocaleString('ru-RU')} ₽, без ареста.`,'bad'));}
      adjust(s,{health:-Math.max(8,Math.round(gig.energy*.55)),stress:8});addLog(s,`${gig.name}: остановили и избили. Денег не взяли, здоровье пострадало.`,'bad');return this.emit(success('Сделка сорвалась: досталось здоровью.','bad'));
    }
    s.money+=payout;s.totalEarned+=payout;
    addLog(s,`${gig.name}: получено ${payout.toLocaleString('ru-RU')} ₽.${injured?' Пришлось лечить травму.':''}`,injured?'bad':'good');
    this.maybeIncident('crime',.14);
    return this.emit(success(`+${payout.toLocaleString('ru-RU')} ₽${injured?' · получена травма':''}`,injured?'bad':'good'));
  }
  startCasino(id,amount){
    const blocked=this.guard();if(blocked)return this.emit(blocked);
    const s=this.state,stake=Number(amount),game=byId(casinoGames,id);
    if(!game)return this.emit(fail('Игровой стол не найден.'));
    if(!Number.isSafeInteger(stake)||stake<50)return this.emit(fail('Минимальная ставка — 50 ₽.'));
    if(s.stats.energy<4)return this.emit(fail('Для партии нужны 4 энергии.'));
    if(s.money<stake)return this.emit(fail('Не хватает денег на ставку.'));
    if(stake>casinoRemaining(s))return this.emit(fail('Дневной лимит ставок исчерпан.'));
    const dayLimit=casinoRemaining(s)+((s.casinoDaily.day===s.day&&s.casinoDaily.wagered)||0);
    if(s.casinoDaily.day!==s.day)s.casinoDaily={day:s.day,wagered:0,limit:dayLimit};
    else s.casinoDaily.limit ||= dayLimit;
    s.money-=stake;s.casinoDaily.wagered+=stake;s.casino.wagered+=stake;
    s.casinoTable=createCasinoTable(id,stake,this.rng);
    return this.emit(success(`${game.name}: партия началась. Ставка ${stake.toLocaleString('ru-RU')} ₽.`,'neutral'));
  }
  casinoAct(action,value){
    const s=this.state,table=s.casinoTable;if(!table)return this.emit(fail('Нет открытой партии.'));
    const extra=['double','raise'].includes(action)?table.stake:0;
    if(extra&&(s.money<extra||casinoRemaining(s)<extra))return this.emit(fail('На удвоение не хватает денег или дневного лимита.'));
    const move=actCasinoTable(table,action,value,this.rng);
    if(move.error)return this.emit(fail(move.error));
    if(move.extraStake){s.money-=move.extraStake;s.casinoDaily.wagered+=move.extraStake;s.casino.wagered+=move.extraStake;}
    if(move.done){
      s.money+=table.returned;s.casino.rounds++;s.casino.returned+=table.returned;
      const net=table.returned-table.wager;if(net>0){s.casino.wins++;s.totalEarned+=net;}
      adjust(s,{energy:-4,stress:net<0?5:-2,mood:net<0?-4:4});
      const game=byId(casinoGames,table.id);s.casino.history.unshift({day:s.day,game:game.name,result:table.result,net});s.casino.history=s.casino.history.slice(0,8);
      addLog(s,`${game.name}: ${table.result} ${net>=0?'+':''}${net.toLocaleString('ru-RU')} ₽.`,net>=0?'good':'bad');
      this.tick(1);
      return this.emit(success(`${table.result} ${net>=0?'+':''}${net.toLocaleString('ru-RU')} ₽.`,net>=0?'good':'bad'));
    }
    return this.emit(success(move.message||'Ход принят.','neutral'));
  }
  closeCasino(){
    const s=this.state,table=s.casinoTable;if(!table)return this.emit(fail('Нет открытой партии.'));
    if(table.phase!=='done'){
      table.returned=0;table.result='Партия прервана. Ставка осталась в банке.';table.phase='done';
      s.casino.rounds++;s.casino.history.unshift({day:s.day,game:byId(casinoGames,table.id).name,result:table.result,net:-table.wager});s.casino.history=s.casino.history.slice(0,8);
      adjust(s,{energy:-4,stress:4,mood:-3});addLog(s,table.result,'bad');
    }
    s.casinoTable=null;return this.emit(success('Ты вышел из-за стола.','neutral'));
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
    if(id==='beg'&&['office-suit','tailored-suit','cashmere-coat'].some(x=>s.upgrades.includes(x)))return this.emit(fail('В дорогом костюме прохожие не верят, что тебе нужна мелочь.'));
    if (!this.spend(a.cost)) return this.emit(fail('Не хватает денег.'));
    const effect={...a.effect};
    if (a.restoreSleep) {
      const home=byId(homes,s.home);
      const target=clamp(75+Math.round(home.restore*.2)+Math.round(s.stats.life*.08),0,100);
      effect.energy=Math.max(0,target-s.stats.energy);
    }
    if(id==='rest')s.lastRestDay=s.day;
    if(id==='drink')s.conditions.hangover=1;
    if(id==='sleep')s.conditions.hangover=0;
    if(id==='clinic')s.conditions.back=0;
    if(id==='yardrepair')s.conditions.shoes=100;
    let outcome=a.description;
    if(id==='beg'){const coins=20+Math.floor(this.rng()*100);s.money+=coins;s.totalEarned+=coins;outcome=`За два часа собрал ${coins} ₽. Никакой гарантии на завтра.`;}
    adjust(s,effect); this.tick(a.hours); addLog(s,outcome,a.cost?'neutral':'good');
    if(id==='walk'){s.conditions.walkTrips++;s.conditions.shoes=clamp(s.conditions.shoes-(s.upgrades.includes('boots')?3:7),0,100);this.maybeIncident('walk',.08+(100-s.conditions.shoes)*.0012);}
    if(id==='sleep')this.maybeIncident('daily',.09);
    return this.emit(success(outcome));
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
  romanceAction(id,action){
    const blocked=this.guard();if(blocked)return this.emit(blocked);
    const s=this.state,person=byId(romancePeople,id);if(!person)return this.emit(fail('Этого человека нет в городе.'));
    const r=s.romance,p=r.profiles[id]||{rapport:0,meetings:0,lastDay:0,days:0,spent:0,appearance:0,last:person.description,met:false};
    if(action==='separate'){
      if(r.partner!==id)return this.emit(fail('Вы не вместе.'));
      r.partner=null;p.married=false;p.last='Вы решили разойтись.';r.profiles[id]=p;adjust(s,{stress:5,mood:-4});addLog(s,`${person.name}: вы расстались.`,'bad');return this.emit(success(p.last,'bad'));
    }
    if(action==='commit'){
      if(r.partner)return this.emit(fail('Сначала реши отношения с нынешним партнёром.'));
      if(!p.met||p.rapport<person.commit)return this.emit(fail(`Нужно доверие ${person.commit}. Сейчас ${p.rapport}.`));
      if(id==='nina'&&(s.jobsDone<8||homes.findIndex(x=>x.id===s.home)<2||r.betrayedNina))return this.emit(fail('Нине нужны устойчивость, честность и своё жильё. После измены доверие не вернуть подарком.'));
      r.partner=id;p.days=0;p.everPartner=true;p.last=`Вы с ${person.name} решили быть вместе.`;r.profiles[id]=p;adjust(s,{mood:8,stress:-3});addLog(s,p.last,'good');return this.emit(success(p.last));
    }
    if(action==='marry'){
      if(r.partner!==id||p.days<8||p.rapport<80)return this.emit(fail('Для общего будущего нужны время и доверие 80.'));
      if(id==='nina'&&r.betrayedNina)return this.emit(fail('Нина не готова после измены.'));
      p.married=true;p.last=`Вы с ${person.name} поженились.`;r.profiles[id]=p;adjust(s,{mood:10,respect:3});addLog(s,p.last,'good');return this.emit(success(p.last));
    }
    const gift=action.startsWith('gift-')?romanceGifts.find(x=>x.id===action.slice(5)):null;
    if(!['meet','talk','date'].includes(action)&&!gift)return this.emit(fail('Неизвестное действие.'));
    if(!p.met){
      const reason=romanceAccess(s,person);if(reason)return this.emit(fail(reason));
      if(person.district&&s.district!==person.district)return this.emit(fail('Сначала доберись в её район для знакомства.'));
      if(action!=='meet')return this.emit(fail('Сначала познакомьтесь.'));
    }
    if(p.lastDay===s.day)return this.emit(fail('Сегодня вы уже провели время вместе. Продолжи завтра.'));
    const cost=action==='date'?person.dateCost:gift?Math.round(person.giftCost*gift.factor):0;
    if(s.money<cost)return this.emit(fail(`Нужно ${cost.toLocaleString('ru-RU')} ₽.`));
    if(s.stats.energy<6)return this.emit(fail('Нужны силы на встречу. Переведи дух или выспись.'));
    const affair=!!(r.partner&&r.partner!==id&&action!=='meet');
    s.money-=cost;p.spent+=cost;p.met=true;p.meetings++;p.lastDay=s.day;
    const gain=gift?giftTaste[id][gift.id]:action==='meet'?6:action==='talk'?5:action==='date'?(id==='nina'?9:11):0;
    p.rapport=clamp(p.rapport+gain,0,100);
    const line=person.lines[(p.meetings-1)%person.lines.length];p.last=`${line} ${gift?`${gift.name}: ${gain>=0?'понравилось':'не попало в характер'}.`:action==='date'?'Вы провели вечер вместе.':action==='talk'||p.meetings>1?'Вы поговорили.':'Вы познакомились.'}`;
    if(gift){p.gifts=(p.gifts||0)+1;p.lastGift=gift.id;}
    r.profiles[id]=p;r.history.unshift({day:s.day,id,action,text:p.last});r.history=r.history.slice(0,20);
    adjust(s,{energy:action==='date'?-9:-6,mood:gift?2:4,stress:action==='date'?-2:0});
    if(affair){r.affairs=(r.affairs||0)+1;const partner=byId(romancePeople,r.partner);r.conflict={kind:'affair',partnerId:r.partner,targetId:id,title:`Тайная встреча раскрыта`,text:`${partner.name} узнала о твоей встрече с ${person.name}. Реши, что сказать.`,day:s.day};addLog(s,r.conflict.text,'bad');}
    this.tick(action==='date'?3:1);addLog(s,`${person.name}: ${p.last} Доверие ${p.rapport}/100.`,'story');
    return this.emit(success(`${person.name}: ${p.last} Доверие ${p.rapport}/100.`));
  }
  resolveRomanceConflict(index){
    const s=this.state,r=s.romance,c=r?.conflict;if(!c)return this.emit(fail('Ссоры сейчас нет.'));
    const choice=romanceConflictChoices(s)[index];if(!choice)return this.emit(fail('Выбери ответ.'));
    if(s.money<choice.cost)return this.emit(fail('Не хватает денег на этот ответ.'));
    const p=r.profiles[c.partnerId];if(!p){r.conflict=null;return this.emit(fail('Разговор уже закончился.'));}
    s.money-=choice.cost;p.spent=(p.spent||0)+choice.cost;p.rapport=clamp(p.rapport+choice.rapport,0,100);
    adjust(s,choice.effect);if(c.kind==='affair'&&c.partnerId==='nina')r.betrayedNina=true;
    const separated=choice.leave||p.rapport<8;
    if(separated){r.partner=null;p.married=false;p.last=`Вы расстались. ${choice.reply}`;}
    else p.last=choice.reply;
    r.history.unshift({day:s.day,id:c.partnerId,action:c.kind,text:p.last});r.history=r.history.slice(0,20);
    r.conflict=null;addLog(s,`${byId(romancePeople,c.partnerId).name}: ${p.last} Доверие ${p.rapport}/100.`,separated?'bad':'story');
    return this.emit(success(p.last,separated?'bad':'good'));
  }
  person(id,action,choiceIndex) {
    const blocked=this.guard(); if (blocked) return this.emit(blocked);
    const s=this.state,p=byId(people,id);
    if (!p||s.district!==p.district && !(p.id==='valera' && s.district==='market') && !(p.id==='azamat' && s.district==='industrial') && !(p.id==='lida' && s.district==='center') && !(p.id==='vera' && s.district==='glass') && !(p.id==='artur' && s.district==='heights')) return this.emit(fail('Этого человека здесь нет.'));
    if (s.stats.respect<p.threshold&&s.stats.contacts<Math.ceil(p.threshold/3)&&netWorth(s)<p.threshold*18000) return this.emit(fail('Нужно больше уважения, связей или денег, чтобы заинтересовать этого человека.'));
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
  summary() { return {netWorth:netWorth(this.state),success:successRoute(this.state)?.name||null}; }
}
