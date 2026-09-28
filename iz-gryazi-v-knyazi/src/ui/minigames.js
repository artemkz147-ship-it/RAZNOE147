const words=['ЛАРЁК','ПОДЪЕЗД','ЧЕК','СКЛАД','ЧАЙ','ШИНОМОНТАЖ','ДОГОВОР','МАРШРУТ'];
const rand=(min,max)=>Math.floor(Math.random()*(max-min+1))+min;
const esc=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;');

export class MiniGame {
  constructor(job,finish) {
    this.job=job; this.finish=finish; this.round=0; this.scores=[]; this.active=true;
    this.target=rand(23,77); this.marker=0; this.direction=1;
    this.sequence=Array.from({length:4},()=>rand(0,5)); this.guess=0; this.revealed=false;
    this.route=[]; this.auditChoice=rand(0,2); this.auditCorrect=0;
    this.offer=95; this.bargainIdeal=rand(75,125);
    this.clock=null; this.timeout=null;
  }
  title() { return {timing:'ПОЙМАЙ МОМЕНТ',memory:'ЗАПОМНИ ЗАКАЗ',route:'ПРОЛОЖИ МАРШРУТ',bargain:'ДОГОВОРИСЬ',audit:'НАЙДИ ОШИБКУ'}[this.job.game]; }
  description() { return {timing:'Останови метку в освещённой зоне. Три попытки.',memory:'Запомни последовательность и повтори её.',route:'Выбери три участка с наименьшими потерями.',bargain:'Назови цену. Чем ближе к ожиданию клиента, тем выше результат.',audit:'Проверь накладную и найди поле, которое не сходится.'}[this.job.game]; }
  render() {
    let body='';
    if(this.job.game==='timing') body=`<div class="timing-track"><div class="timing-target" style="left:${this.target-8}%"></div><div class="timing-marker" id="timing-marker" style="left:${this.marker}%"></div></div><p class="mini-hint">Попытка ${this.round+1}/3 · Нажми кнопку или пробел</p><button class="primary big" data-mini="timing">СТОП</button>`;
    if(this.job.game==='memory') body=`<div class="memory-display" id="memory-display">${this.revealed?'?':'СМОТРИ ВНИМАТЕЛЬНО'}</div><p class="mini-hint">${this.revealed?`Слово ${this.guess+1} из ${this.sequence.length}`:'Слова появятся одно за другим'}</p><div class="memory-options">${this.revealed?words.slice(0,6).map((w,i)=>`<button data-mini="memory" data-value="${i}">${w}</button>`).join(''):''}</div>`;
    if(this.job.game==='route') {
      const options=this.routeOptions();
      body=`<div class="route-map"><div class="route-line"><span>СКЛАД</span><i></i><span>КЛИЕНТ</span></div><p>Участок ${this.round+1}/3 · пробка, бензин и дедлайн</p></div><div class="route-options">${options.map((o,i)=>`<button data-mini="route" data-value="${i}"><strong>${o.name}</strong><span>${o.time} мин · ${o.fuel} ₽ · риск ${o.risk}%</span></button>`).join('')}</div>`;
    }
    if(this.job.game==='bargain') body=`<div class="bargain-panel"><div class="bargain-face">${this.offer>this.bargainIdeal+15?'Хм. Вы смелый.':this.offer<this.bargainIdeal-15?'Звучит подозрительно дёшево.':'Я слушаю...'}</div><label for="offer">Предложение: <strong id="offer-value">${this.offer}%</strong> от базовой цены</label><input id="offer" type="range" min="60" max="140" value="${this.offer}" data-mini-input="offer"><div class="offer-scale"><span>ДЁШЕВО</span><span>ДОРОГО</span></div></div><button class="primary big" data-mini="bargain">ПОДАТЬ ПРЕДЛОЖЕНИЕ</button>`;
    if(this.job.game==='audit') {
      const rows=this.auditRows();
      body=`<div class="audit-docs"><div><h4>ЗАКАЗ</h4>${rows.map((r,i)=>`<p>${r.label}<strong>${r.left}</strong></p>`).join('')}</div><div><h4>НАКЛАДНАЯ</h4>${rows.map((r,i)=>`<button data-mini="audit" data-value="${i}">${r.label}<strong>${r.right}</strong></button>`).join('')}</div></div><p class="mini-hint">Проверка ${this.round+1}/3 · нажми на неверную строку справа</p>`;
    }
    return `<div class="mini-shell"><div class="mini-heading"><span class="eyebrow">СМЕНА / ${esc(this.job.name.toUpperCase())}</span><h2>${this.title()}</h2><p>${this.description()}</p></div><div class="mini-progress">${[0,1,2].map(i=>`<span class="${i<this.round?'done':i===this.round?'active':''}"></span>`).join('')}</div>${body}<button class="text-button mini-exit" data-mini="exit">Прервать смену</button></div>`;
  }
  routeOptions() {
    if(!this.currentRoute) {
      this.currentRoute=[
        {name:'Через дворы',time:rand(12,23),fuel:rand(80,150),risk:rand(22,43)},
        {name:'По проспекту',time:rand(15,28),fuel:rand(100,180),risk:rand(8,22)},
        {name:'Через промзону',time:rand(10,25),fuel:rand(120,210),risk:rand(15,35)}
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
      if(index<this.sequence.length){if(el)el.textContent=words[this.sequence[index]]; index++; this.timeout=setTimeout(show,1050);}
      else {this.revealed=true;this.onRender();}
    };
    this.timeout=setTimeout(show,700);
  }
  input(type,value) {
    if(!this.active)return;
    if(type==='exit') {this.destroy();this.finish(null);return;}
    if(type!==this.job.game)return;
    if(type==='timing') {
      const diff=Math.abs(this.marker-this.target);
      this.scores.push(Math.max(0,1-diff/46));this.round++;
      this.target=rand(23,77);
      if(this.round>=3)return this.complete();
    }
    if(type==='memory') {
      if(!this.revealed)return;
      this.scores.push(Number(value)===this.sequence[this.guess]?1:0);
      this.guess++;
      if(this.guess>=this.sequence.length)return this.complete();
    }
    if(type==='route') {
      const options=this.routeOptions(),best=Math.min(...options.map(o=>o.time*8+o.fuel+o.risk*3));
      const chosen=options[Number(value)],cost=chosen.time*8+chosen.fuel+chosen.risk*3;
      this.scores.push(Math.max(.1,1-(cost-best)/220));this.round++;this.currentRoute=null;
      if(this.round>=3)return this.complete();
    }
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
  complete() { const score=this.scores.reduce((a,b)=>a+b,0)/this.scores.length;this.destroy();this.finish(score); }
  destroy() {this.active=false;clearInterval(this.clock);clearTimeout(this.timeout);}
}
