import {diagnosticRound} from '../data/diagnostics.js';
const words=['ЛАРЁК','ПОДЪЕЗД','ЧЕК','СКЛАД','ЧАЙ','ШИНОМОНТАЖ','ДОГОВОР','МАРШРУТ'];
const rand=(min,max)=>Math.floor(Math.random()*(max-min+1))+min;
const esc=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;');

export class MiniGame {
  constructor(job,finish) {
    this.job=job; this.finish=finish; this.round=0; this.scores=[]; this.active=true;
    this.target=rand(23,77); this.marker=0; this.direction=1;
    this.sequence=Array.from({length:4},()=>rand(0,5)); this.guess=0; this.revealed=false;this.memoryResults=[];this.finishing=false;
    this.route=[]; this.auditChoice=rand(0,2); this.auditCorrect=0;
    this.offer=95; this.bargainIdeal=rand(75,125);
    this.alert=0;this.loot=0;this.sortData=null;this.cipherData=null;
    this.clock=null; this.timeout=null;
  }
  title() { return {repair:'НАЙДИ И УСТРАНИ СБОЙ',timing:'ПОЙМАЙ МОМЕНТ',memory:'ЗАПОМНИ ЗАКАЗ',route:'ПРОЛОЖИ МАРШРУТ',bargain:'ДОГОВОРИСЬ',audit:'НАЙДИ ОШИБКУ',sort:'СОБЕРИ ЗАКАЗ',cipher:'РАЗБЕРИ ШИФР',stealth:'ОЦЕНИ ОБСТАНОВКУ'}[this.job.game]; }
  description() { return {repair:'Сверь показания с нормой. Найди причину, затем выбери ремонт.',timing:'Останови метку в освещённой зоне. Три попытки.',memory:'Запомни четыре слова по порядку. Перед первым будет время подготовиться.',route:'Выбери три участка по времени, усталости и риску.',bargain:'Назови цену. Чем ближе к ожиданию клиента, тем выше результат.',audit:'Проверь накладную и найди поле, которое не сходится.',sort:'Сверь адрес, вес и пломбу. Три заказа подряд.',cipher:'Восстанови правило числового ряда. Три проверки.',stealth:'Выбирай темп и осторожность. Шум растёт от каждого шага.'}[this.job.game]; }
  sortRound() {
    if(this.sortData)return this.sortData;
    const places=['ДВОР','РЫНОК','ПРОМЗОНА','ЦЕНТР'],destination=rand(0,3),limit=rand(6,13),correct=rand(0,3);
    const valid={destination,weight:limit-rand(1,4),seal:true};
    const decoys=[{destination:(destination+1)%4,weight:valid.weight,seal:true},{destination,weight:limit+rand(1,4),seal:true},{destination,weight:valid.weight,seal:false}];
    const cards=Array.from({length:4},(_,i)=>i===correct?valid:decoys[i<correct?i:i-1]);
    this.sortData={places,destination,limit,correct,cards};return this.sortData;
  }
  cipherRound() {
    if(this.cipherData)return this.cipherData;
    const start=rand(11,28),step=rand(3,8),answer=start+step*3,correct=rand(0,3);
    const options=[answer-step,answer+step,answer+2,answer-2];options[correct]=answer;
    this.cipherData={start,step,answer,correct,options};return this.cipherData;
  }
  repairRound(){return this.diagnostic||=(diagnosticRound(this.job,this.round));}
  renderRepair(){
    const q=this.repairRound(),stage=this.repairStage||'diagnose';
    const readings='<div class="diagnostic-readings"><small>ПОКАЗАНИЯ · УЗЕЛ '+(this.round+1)+'/3</small>'+q.readings.map(r=>'<div><span>'+r.name+'</span><strong>'+r.value+' '+r.unit+'</strong><small>Норма '+r.min+'–'+r.max+'</small></div>').join('')+'</div>';
    if(stage==='feedback')return readings+'<div class="repair-feedback '+(this.repairCorrect?'good':'bad')+'"><strong>'+(this.repairCorrect?'Узел восстановлен':'Сбой остался')+'</strong><p>'+(this.repairCorrect?q.reply:'Причина — '+q.readings.find(r=>r.id===q.fault).name.toLowerCase()+'. Выбранный ремонт её не устранил.')+'</p><small>Диагностика: '+(this.chosenFault===q.fault?'верная':'ошибочная')+'</small></div><button data-mini="repair-next" class="primary big">'+(this.round===2?'ЗАКОНЧИТЬ РЕМОНТ':'СЛЕДУЮЩИЙ УЗЕЛ')+'</button>';
    return readings+'<h4>'+(stage==='diagnose'?'ГДЕ ПРИЧИНА':'ЧТО ДЕЛАТЬ')+'</h4><div class="diagnostic-options">'+(stage==='diagnose'?q.readings.map(r=>'<button data-mini="diagnose" data-value="'+r.id+'">'+r.name+'</button>'):q.repairs.map(r=>'<button data-mini="repair" data-value="'+r.id+'">'+r.name+'</button>')).join('')+'</div>';
  }
  render() {
    let body='';
    if(this.job.game==='repair')body=this.renderRepair();
    if(this.job.game==='timing') body=`<div class="timing-track"><div class="timing-target" style="left:${this.target-8}%"></div><div class="timing-marker" id="timing-marker" style="left:${this.marker}%"></div></div><p class="mini-hint">Попытка ${this.round+1}/3 · Нажми кнопку или пробел</p><button class="primary big" data-mini="timing">СТОП</button>`;
    if(this.job.game==='memory') body=`<div class="memory-checks" aria-label="Ответы">${this.sequence.map((_,i)=>`<span class="${this.memoryResults[i]===true?'correct':this.memoryResults[i]===false?'wrong':i===this.guess&&this.revealed?'current':'waiting'}">${i+1}</span>`).join('')}</div><div class="memory-display" id="memory-display">${this.revealed?'?':'ПРИГОТОВЬСЯ'}</div><p class="mini-hint">${this.revealed?this.finishing?'Проверка закончена':`Слово ${this.guess+1} из ${this.sequence.length}`:'Слова появятся одно за другим'}</p><div class="memory-options">${this.revealed&&!this.finishing?words.slice(0,6).map((w,i)=>`<button data-mini="memory" data-value="${i}">${w}</button>`).join(''):''}</div>`;
    if(this.job.game==='sort') {const q=this.sortRound();body=`<div class="sort-order"><small>НАКЛАДНАЯ ${this.round+1}/3</small><strong>${q.places[q.destination]} · ДО ${q.limit} КГ</strong><p>Адрес верный, вес не выше лимита, пломба целая.</p></div><div class="sort-cards">${q.cards.map((x,i)=>`<button data-mini="sort" data-value="${i}"><span class="parcel-mark">0${i+1}</span><strong>${q.places[x.destination]}</strong><small>${x.weight} кг · пломба ${x.seal?'целая':'сорвана'}</small></button>`).join('')}</div>`;}
    if(this.job.game==='cipher'){const q=this.cipherRound();body=`<div class="cipher-paper"><small>ЗАПИСЬ ${this.round+1}/3</small><strong>${q.start} · ${q.start+q.step} · ${q.start+q.step*2} · ?</strong><p>Какое число продолжит правило?</p></div><div class="cipher-options">${q.options.map((x,i)=>`<button data-mini="cipher" data-value="${i}">${x}</button>`).join('')}</div>`;}
    if(this.job.game==='stealth') {const stage=['НАБЛЮДЕНИЕ','ДЕЙСТВИЕ','ВЫХОД'][this.round];const options=[{name:'Подождать удобный момент',gain:12,noise:5,detail:'Медленно · тихо'},{name:'Действовать по плану',gain:27,noise:18,detail:'Средний темп · средний шум'},{name:'Пойти напролом',gain:44,noise:38,detail:'Быстро · громко'}];body=`<div class="stealth-status"><span>ЭТАП ${this.round+1}/3 · ${stage}</span><strong>ВНИМАНИЕ ${this.alert}%</strong><div><i style="width:${this.alert}%"></i></div><small>Потенциальная добыча ${this.loot}%</small></div><div class="stealth-options">${options.map((x,i)=>`<button data-mini="stealth" data-value="${i}"><strong>${x.name}</strong><span>ДОБЫЧА +${x.gain} · ШУМ +${x.noise}</span><small>${x.detail}</small></button>`).join('')}</div>`;}
    if(this.job.game==='route') {
      const options=this.routeOptions();
      body=`<div class="route-map"><div class="route-line"><span>СТАРТ</span><i></i><span>АДРЕС</span></div><p>Участок ${this.round+1}/3 · оцени время, усталость и неприятности</p></div><div class="route-options">${options.map((o,i)=>`<button data-mini="route" data-value="${i}"><strong>${o.name}</strong><span>${o.time} мин · ${o.effort} усталость · риск ${o.risk}%</span></button>`).join('')}</div>`;
    }
    if(this.job.game==='bargain') body=`<div class="bargain-panel"><div class="bargain-face">${this.offer>this.bargainIdeal+15?'Хм. Вы смелый.':this.offer<this.bargainIdeal-15?'Звучит подозрительно дёшево.':'Я слушаю...'}</div><label for="offer">Предложение: <strong id="offer-value">${this.offer}%</strong> от базовой цены</label><input id="offer" type="range" min="60" max="140" value="${this.offer}" data-mini-input="offer"><div class="offer-scale"><span>ДЁШЕВО</span><span>ДОРОГО</span></div></div><button class="primary big" data-mini="bargain">ПОДАТЬ ПРЕДЛОЖЕНИЕ</button>`;
    if(this.job.game==='audit') {
      const rows=this.auditRows();
      body=`<div class="audit-docs"><div><h4>ЗАКАЗ</h4>${rows.map((r,i)=>`<p>${r.label}<strong>${r.left}</strong></p>`).join('')}</div><div><h4>НАКЛАДНАЯ</h4>${rows.map((r,i)=>`<button data-mini="audit" data-value="${i}">${r.label}<strong>${r.right}</strong></button>`).join('')}</div></div><p class="mini-hint">Проверка ${this.round+1}/3 · нажми на неверную строку справа</p>`;
    }
    return `<div class="mini-shell"><div class="mini-heading"><span class="eyebrow">${'arrest' in this.job?'ЗАКАЗ':'СМЕНА'} / ${esc(this.job.name.toUpperCase())}</span><h2>${this.title()}</h2><p>${this.description()}</p></div>${this.job.game==='memory'?'':`<div class="mini-progress">${[0,1,2].map(i=>`<span class="${i<this.round?'done':i===this.round?'active':''}"></span>`).join('')}</div>`}${body}<button class="text-button mini-exit" data-mini="exit">Прервать ${'arrest' in this.job?'заказ':'смену'}</button></div>`;
  }
  routeOptions() {
    if(!this.currentRoute) {
      this.currentRoute=[
        {name:'Через дворы',time:rand(12,23),effort:rand(5,9),risk:rand(11,26)},
        {name:'По главной улице',time:rand(17,28),effort:rand(2,5),risk:rand(2,10)},
        {name:'Короткий переулок',time:rand(10,20),effort:rand(7,11),risk:rand(15,31)}
      ];
    }
    return this.currentRoute;
  }
  auditRows() {
    if(!this.currentAudit) {
      const base=rand(4,18),item=rand(2,9),sum=base*item;
      this.auditCorrect=rand(0,2);
      this.currentAudit=[
        {label:'КОРОБКИ',left:`${base} шт.`,right:`${base+(this.auditCorrect===0?1:0)} шт.`},
        {label:'ЦЕНА',left:`${item*100} ₽`,right:`${(item+(this.auditCorrect===1?1:0))*100} ₽`},
        {label:'ИТОГО',left:`${sum*100} ₽`,right:`${(sum+(this.auditCorrect===2?item:0))*100} ₽`}
      ];
    }
    return this.currentAudit;
  }
  start(onRender) {
    this.onRender=onRender;
    if(this.job.game==='timing') this.clock=setInterval(()=>{this.marker+=this.direction*1.9;if(this.marker>=99||this.marker<=1)this.direction*=-1;const el=document.getElementById('timing-marker');if(el)el.style.left=`${this.marker}%`;},18);
    if(this.job.game==='memory') this.showMemory();
  }
  showMemory() {
    let index=0;
    const show=()=>{
      if(!this.active)return;
      const el=document.getElementById('memory-display');
      if(index<this.sequence.length){if(el)el.textContent=words[this.sequence[index]]; index++; this.timeout=setTimeout(show,1400);}
      else {this.revealed=true;this.onRender();}
    };
    this.timeout=setTimeout(show,1800);
  }
  input(type,value) {
    if(!this.active||this.finishing)return;
    if(type==='exit') {this.destroy();this.finish(null);return;}
    if(type!==this.job.game&&!(this.job.game==='repair'&&['diagnose','repair-next'].includes(type)))return;
    if(type==='timing') {
      const diff=Math.abs(this.marker-this.target);
      this.scores.push(Math.max(0,1-diff/46));this.round++;
      this.target=rand(23,77);
      if(this.round>=3)return this.complete();
    }
    if(type==='memory') {
      if(!this.revealed)return;
      const correct=Number(value)===this.sequence[this.guess];this.scores.push(correct?1:0);this.memoryResults.push(correct);
      this.guess++;
      if(this.guess>=this.sequence.length){this.finishing=true;this.onRender();this.timeout=setTimeout(()=>this.complete(),850);return;}
    }
    if(type==='diagnose'&&this.job.game==='repair'){
      const q=this.repairRound();if(this.repairStage&&this.repairStage!=='diagnose'||!q.readings.some(r=>r.id===value))return;
      this.chosenFault=value;this.repairStage='repair';this.onRender();return;
    }
    if(type==='repair'&&this.job.game==='repair'){
      if(this.repairStage!=='repair')return;const q=this.repairRound(),choice=q.repairs.find(r=>r.id===value);if(!choice)return;
      this.repairCorrect=choice.fault===q.fault;this.scores.push((this.chosenFault===q.fault?.35:0)+(this.repairCorrect?.65:0));this.repairStage='feedback';this.onRender();return;
    }
    if(type==='repair-next'&&this.job.game==='repair'){
      if(this.repairStage!=='feedback')return;this.round++;this.diagnostic=null;this.repairStage='diagnose';if(this.round>=3)return this.complete();this.onRender();return;
    }
    if(type==='route') {
      const options=this.routeOptions(),best=Math.min(...options.map(o=>o.time*5+o.effort*9+o.risk*3));
      const chosen=options[Number(value)];if(!chosen)return;
      const cost=chosen.time*5+chosen.effort*9+chosen.risk*3;
      this.scores.push(Math.max(.1,1-(cost-best)/190));this.round++;this.currentRoute=null;
      if(this.round>=3)return this.complete();
    }
    if(type==='sort'){const q=this.sortRound();this.scores.push(Number(value)===q.correct?1:.1);this.round++;this.sortData=null;if(this.round>=3)return this.complete();}
    if(type==='cipher'){const q=this.cipherRound();this.scores.push(Number(value)===q.correct?1:.1);this.round++;this.cipherData=null;if(this.round>=3)return this.complete();}
    if(type==='stealth'){const opts=[{gain:12,noise:5},{gain:27,noise:18},{gain:44,noise:38}],choice=opts[Number(value)];if(!choice)return;this.loot+=choice.gain;this.alert=Math.min(100,this.alert+choice.noise+this.round*4);this.scores.push(Math.max(.05,.48+choice.gain/65-choice.noise/90));this.round++;if(this.alert>=100)return this.complete();if(this.round>=3)return this.complete();}
    if(type==='bargain') {
      const diff=Math.abs(this.offer-this.bargainIdeal);
      this.scores.push(Math.max(.05,1-diff/55));
      if(this.offer>this.bargainIdeal+27)this.scores[0]*=.5;
      return this.complete();
    }
    if(type==='audit') {
      this.scores.push(Number(value)===this.auditCorrect?1:0);
      this.round++;this.currentAudit=null;
      if(this.round>=3)return this.complete();
    }
    this.onRender();
  }
  setOffer(value) {this.offer=Number(value);const el=document.getElementById('offer-value');if(el)el.textContent=`${this.offer}%`;const face=document.querySelector('.bargain-face');if(face)face.textContent=this.offer>this.bargainIdeal+15?'Хм. Вы смелый.':this.offer<this.bargainIdeal-15?'Звучит подозрительно дёшево.':'Я слушаю...';}
  complete() { let score=this.scores.reduce((a,b)=>a+b,0)/this.scores.length;if(this.job.game==='stealth')score=this.alert>=100?.05:Math.max(.1,Math.min(1,score*.5+this.loot/140-this.alert/260));this.destroy();this.finish(score); }
  destroy() {this.active=false;clearInterval(this.clock);clearTimeout(this.timeout);}
}
