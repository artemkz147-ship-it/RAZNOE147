(()=>{
'use strict';
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const iso=d=>{const x=new Date(d);x.setMinutes(x.getMinutes()-x.getTimezoneOffset());return x.toISOString().slice(0,10)};
const dayMs=86400000;
state.meals=state.meals||{};state.reminder=state.reminder||{enabled:false,time:'20:30'};state.strengthHistory=state.strengthHistory||[];save();

function dparse(s){return new Date(s+'T12:00:00')}
function avg(a){return a.length?a.reduce((x,y)=>x+y,0)/a.length:null}
function inLast(date,n,offset=0){const now=dparse(iso(new Date()));const x=dparse(date);const diff=Math.floor((now-x)/dayMs);return diff>=offset&&diff<offset+n}
function records(n,offset=0){return state.days.filter(x=>inLast(x.date,n,offset))}
function weightAvg(n,offset=0){const a=records(n,offset).filter(x=>x.weight).map(x=>+x.weight);return avg(a)}
function currentMonthIndex(){const start=dparse(state.profile.start);const now=new Date();return Math.max(1,Math.min(12,Math.floor((now-start)/(30.4375*dayMs))+1))}
function gymGoal(){return 3}
function phaseNow(){return phase(weekNo())}
function esc(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}

function addTopSettings(){
 const top=$('.top');if(!top||$('#settingsOpen'))return;
 const badge=$('#weekBadge');const box=document.createElement('div');box.className='top-tools';
 if(badge){badge.parentNode.insertBefore(box,badge);box.appendChild(badge)}
 const b=document.createElement('button');b.id='settingsOpen';b.className='iconbtn settings-open';b.type='button';b.setAttribute('aria-label','Настройки');b.textContent='⚙';box.appendChild(b);
 b.addEventListener('click',()=>openPage('settings'));
}
function openPage(id){$$('.nav button').forEach(x=>x.classList.remove('on'));$$('.page').forEach(x=>x.classList.remove('on'));const p=$('#'+id);if(p)p.classList.add('on');window.scrollTo(0,0);renderEnhanced()}

function weeklyData(){
 const last=records(7),prev=records(7,7),w1=weightAvg(7),w0=weightAvg(7,7);
 const steps=last.filter(x=>x.steps).map(x=>+x.steps),prot=last.filter(x=>x.protein).map(x=>+x.protein),sleep=last.filter(x=>x.sleep).map(x=>+x.sleep);
 const food=last.filter(x=>x.food).length,act=last.filter(x=>x.activity).length;
 return{last,prev,w1,w0,delta:w1!=null&&w0!=null?w1-w0:null,steps:avg(steps),prot:avg(prot),sleep:avg(sleep),food,act,logged:last.length};
}
function reportScore(r){let parts=0,total=0;const p=phaseNow();if(r.steps!=null){total++;if(r.steps>=p.steps*.9)parts++}if(r.prot!=null){total++;if(r.prot>=state.profile.protein*.9)parts++}if(r.sleep!=null){total++;if(r.sleep>=6.8)parts++}if(r.logged){total++;if(r.food>=Math.max(1,r.logged*.75))parts++}return total?Math.round(parts/total*100):0}
function renderWeeklyReport(){
 let host=$('#weeklyReport');if(!host){host=document.createElement('div');host.id='weeklyReport';host.className='card section hero-report';const phaseCard=$('#phaseCard');phaseCard?.insertAdjacentElement('afterend',host)}
 const r=weeklyData(),score=reportScore(r),delta=r.delta==null?'—':(r.delta>0?'+':'')+r.delta.toFixed(2)+' кг';
 host.innerHTML=`<div class="row between"><div><h2>Отчёт за 7 дней</h2><div class="small muted">Сравнение с предыдущей неделей</div></div><div class="score ${score>=75?'goodscore':'midscore'}">${score}%</div></div><div class="report-grid"><div class="report-stat"><span>Средний вес</span><b>${r.w1==null?'—':r.w1.toFixed(1)+' кг'}</b><span>${delta}</span></div><div class="report-stat"><span>Шаги / день</span><b>${r.steps==null?'—':Math.round(r.steps).toLocaleString('ru-RU')}</b><span>цель ${phaseNow().steps.toLocaleString('ru-RU')}</span></div><div class="report-stat"><span>Белок / день</span><b>${r.prot==null?'—':Math.round(r.prot)+' г'}</b><span>цель ${state.profile.protein} г</span></div><div class="report-stat"><span>Сон</span><b>${r.sleep==null?'—':r.sleep.toFixed(1)+' ч'}</b><span>${r.act} активных дней</span></div></div><div class="small muted" style="margin-top:9px">Заполнено дней: ${r.logged}/7 · питание по плану: ${r.food}/7</div>`;
}

function smartAdvice(){
 const l=weightAvg(7),p=weightAvg(7,7),r14=records(14),food=r14.filter(x=>x.food).length,stepVals=r14.filter(x=>x.steps).map(x=>+x.steps),sleepVals=r14.filter(x=>x.sleep).map(x=>+x.sleep);
 if(l==null||p==null||r14.filter(x=>x.weight).length<5)return{kind:'hold',title:'Пока собираем данные',text:'Нужно хотя бы 5 взвешиваний, распределённых примерно на 2 недели. Калории пока не меняй.',change:0};
 const delta=l-p,pct=(-delta/p)*100,adh=r14.length?food/r14.length:0,st=avg(stepVals),sl=avg(sleepVals),goal=phaseNow().steps;
 if(r14.length<9)return{kind:'hold',title:'Не спеши менять калории',text:`Вес меняется на ${delta.toFixed(2)} кг/нед, но заполнено только ${r14.length}/14 дней. Сначала собери больше данных.`,change:0};
 if(adh<.7)return{kind:'warn',title:'Сначала стабильность',text:`Среднее изменение ${delta.toFixed(2)} кг/нед. Питание отмечено по плану только в ${Math.round(adh*100)}% заполненных дней — калории пока не режем.`,change:0};
 if(pct<.2 && (st==null||st>=goal*.85))return{kind:'warn',title:'Можно слегка уменьшить',text:`Средний вес почти стоит: ${delta.toFixed(2)} кг/нед (${pct.toFixed(2)}%). При хорошем соблюдении плана попробуй −150 ккал и оцени ещё 2 недели.`,change:-150};
 if(pct>1.0 || (sl!=null&&sl<6.2))return{kind:'warn',title:'Темп слишком жёсткий',text:`Изменение ${delta.toFixed(2)} кг/нед (${pct.toFixed(2)}%). Если падают силы, сон или постоянно голодно — добавь 150 ккал.`,change:150};
 return{kind:'good',title:'Оставляем как есть',text:`Средний темп ${delta.toFixed(2)} кг/нед (${pct.toFixed(2)}% массы). Сейчас лучше не трогать ${state.profile.calories} ккал.`,change:0};
}
function renderSmart(){
 let host=$('#smartV11');if(!host){host=document.createElement('div');host.id='smartV11';host.className='card section smart-card';const advice=$('#calAdvice')?.closest('.card');advice?.insertAdjacentElement('beforebegin',host)}
 const a=smartAdvice();host.className='card section smart-card '+(a.kind==='warn'?'warnsmart':'hold');host.innerHTML=`<div class="row between"><div><h2>Умная корректировка</h2><div class="small muted">7 дней против предыдущих 7</div></div><span class="badge">v1.1</span></div><b>${a.title}</b><div class="small" style="margin-top:5px">${a.text}</div>${a.change?`<button id="applySmart" class="btn ${a.change<0?'warn':'good'} section">Применить ${a.change>0?'+':''}${a.change} ккал</button>`:''}`;
 $('#applySmart')?.addEventListener('click',()=>{state.profile.calories=Math.max(1800,Math.min(3200,state.profile.calories+a.change));save();renderAll();renderEnhanced();notice(`Новая цель: ${state.profile.calories} ккал`)})
}

function lineChart(points,key,cls,label){
 const data=points.filter(x=>x[key]!=null).slice(-12);if(data.length<2)return`<div class="small muted">Нужно минимум 2 записи для графика.</div>`;
 const vals=data.map(x=>+x[key]),min=Math.min(...vals),max=Math.max(...vals),range=Math.max(.5,max-min),W=320,H=150,pad=28;
 const xy=data.map((x,i)=>({x:pad+i*(W-pad*2)/(data.length-1),y:H-pad-((+x[key]-min)/range)*(H-pad*2),v:+x[key],d:x.date}));
 const poly=xy.map(p=>`${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
 let grid='';for(let i=0;i<4;i++){const y=pad+i*(H-pad*2)/3;const val=max-i*range/3;grid+=`<line class="gridline" x1="${pad}" y1="${y}" x2="${W-pad}" y2="${y}"/><text class="axistext" x="2" y="${y+3}">${val.toFixed(1)}</text>`}
 const dots=xy.map((p,i)=>`<circle class="dot" cx="${p.x}" cy="${p.y}" r="4"/><text class="axistext" x="${p.x-12}" y="${H-5}">${dparse(p.d).toLocaleDateString('ru-RU',{day:'2-digit',month:'2-digit'})}</text>${i===xy.length-1?`<text class="axistext" x="${p.x-18}" y="${p.y-8}">${p.v.toFixed(1)}</text>`:''}`).join('');
 return`<div class="chart-wrap"><svg class="fchart ${cls||''}" viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(label)}">${grid}<polyline class="line" points="${poly}"/>${dots}</svg></div>`
}
function renderCharts(){
 let host=$('#chartsV11');if(!host){host=document.createElement('div');host.id='chartsV11';host.className='card section';const measureCard=$('#waist')?.closest('.card');measureCard?.insertAdjacentElement('beforebegin',host)}
 const weights=[...state.days].filter(x=>x.weight).sort((a,b)=>a.date.localeCompare(b.date));const waists=[...state.measures].filter(x=>x.waist).sort((a,b)=>a.date.localeCompare(b.date));
 host.innerHTML=`<h2>Графики</h2><div class="small muted">Последние 12 записей</div><h3 style="margin-top:10px">Вес, кг</h3>${lineChart(weights,'weight','','График веса')}<h3 style="margin-top:12px">Талия, см</h3>${lineChart(waists,'waist','waist','График талии')}`;
}

let calOffset=0;
function renderCalendar(){
 let host=$('#calendarV11');if(!host){host=document.createElement('div');host.id='calendarV11';host.className='card section';const hist=$('#dayHistory')?.closest('.card');hist?.insertAdjacentElement('beforebegin',host)}
 const base=new Date();base.setDate(1);base.setMonth(base.getMonth()+calOffset);const y=base.getFullYear(),m=base.getMonth(),first=(new Date(y,m,1).getDay()+6)%7,count=new Date(y,m+1,0).getDate(),names=['Пн','Вт','Ср','Чт','Пт','Сб','Вс'];let cells=names.map(n=>`<div class="calname">${n}</div>`).join('');for(let i=0;i<first;i++)cells+='<div class="calday empty"></div>';
 for(let day=1;day<=count;day++){const ds=`${y}-${String(m+1).padStart(2,'0')}-${String(day).padStart(2,'0')}`,r=state.days.find(x=>x.date===ds),wo=state.workouts.some(x=>x.date===ds),today=ds===iso(new Date());cells+=`<div class="calday ${today?'today':''}"><b>${day}</b><div class="dots">${r?.weight?'<i class="dot w"></i>':''}${r?.food?'<i class="dot f"></i>':''}${(r?.activity||wo)?'<i class="dot a"></i>':''}</div></div>`}
 host.innerHTML=`<div class="calendar-head"><button id="calPrev" class="btn">‹</button><div><h2 style="margin:0">${base.toLocaleDateString('ru-RU',{month:'long',year:'numeric'})}</h2></div><button id="calNext" class="btn">›</button></div><div class="calendar-grid">${cells}</div><div class="legend"><span><i class="lw"></i>вес</span><span><i class="lf"></i>питание</span><span><i class="la"></i>тренировка/активность</span></div>`;
 $('#calPrev').addEventListener('click',()=>{calOffset--;renderCalendar()});$('#calNext').addEventListener('click',()=>{calOffset++;renderCalendar()})
}

const monthFocus=['Режим и техника','Закрепить 3 тренировки','Стабильные шаги','Дефицит без голода','Сохранять силу','Уверенный ритм','Рост рабочих весов','Гравитрон','Негативные подтягивания','Рекомпозиция','Рельеф и пресс','Первое чистое подтягивание'];
function renderYearRoad(){
 let host=$('#yearRoadV11');if(!host){host=document.createElement('div');host.id='yearRoadV11';host.className='card section';const yearCard=$('#weeks')?.closest('.card');yearCard?.insertAdjacentElement('beforebegin',host)}const now=currentMonthIndex();host.innerHTML=`<div class="row between"><div><h2>12 месяцев</h2><div class="small muted">Главный фокус каждого месяца</div></div><span class="badge">месяц ${now}</span></div><div class="month-road">${monthFocus.map((x,i)=>`<div class="month-card ${i+1===now?'now':''}"><b>${i+1}</b><span>${x}</span></div>`).join('')}</div>`;
}

function todayMealData(){
 const dow=new Date().getDay(),w=weekNo(),variants=[['Курица + рис + овощи','Курица + картофель + капуста'],['Бедро без кожи + гречка','Куриный фарш + макароны + томат'],['Курица + картофель + овощи','Бедро без кожи + рис + капуста'],['Куриный фарш + гречка','Курица + макароны + овощи']],v=variants[(w-1)%variants.length];
 if(dow===0)return[{k:'Завтрак',t:'3 яйца + творог 200 г + хлеб 70–80 г'},{k:'Читмил',t:'Один полноценный вкусный приём пищи, ориентир 800–1100 ккал'},{k:'Ужин',t:'Курица 200 г + большой салат/овощи'}];
 return[{k:'Завтрак',t:'3 яйца + творог 200 г + хлеб 70–80 г'},{k:'Обед',t:v[dow<=3?0:1]+' · мясо 180–200 г, гарнир по плану'},{k:'Перекус',t:'Кефир 500 мл + яблоко или банан'},{k:'Ужин',t:v[dow<=3?1:0]+' · овощи 250–300 г'}]
}
function renderTodayFood(){
 let host=$('#todayFoodV11');if(!host){host=document.createElement('div');host.id='todayFoodV11';host.className='card section';const main=$('#food .card');main?.insertAdjacentElement('beforebegin',host)}const key=iso(new Date()),data=todayMealData(),checks=state.meals[key]||{};host.innerHTML=`<div class="row between"><div><h2>Что есть сегодня</h2><div class="small muted">${state.profile.calories} ккал · ${state.profile.protein} г белка</div></div><span class="badge">${new Date().toLocaleDateString('ru-RU',{weekday:'short'})}</span></div><div class="meal-checks">${data.map((x,i)=>`<label class="meal-check"><input type="checkbox" data-meal="${i}" ${checks[i]?'checked':''}><span><b>${x.k}</b><br>${x.t}</span></label>`).join('')}</div><div class="small muted" style="margin-top:9px">Без рыбы и овсянки. Масло учитывай отдельно: 10 г = заметная часть дневных калорий.</div>`;
 $$('[data-meal]').forEach(x=>x.addEventListener('change',()=>{state.meals[key]=state.meals[key]||{};state.meals[key][x.dataset.meal]=x.checked;save()}))
}

function figurePanel(type,finish,x){
 const label=finish?'Финиш':'Старт',blue=finish?'#1677ff':'#16a34a';let body='';
 const head=(cx,cy)=>`<circle class="joint" cx="${cx}" cy="${cy}" r="5"/>`;
 if(type==='press'){body=`<path class="machine" d="M${x+18} 98v-65h32v65M${x+18} 67h48M${x+66} 55v34"/>${head(x+55,42)}<path class="body" d="M${x+55} 48v28l-16 22M${x+55} 76l18 22M${x+54} 57${finish?`l38 1M${x+54} 57l38 1`:`l21 13 17-13M${x+54} 57l21 13`}"/>`;}
 else if(type==='pull'){body=`<path class="machine" d="M${x+18} 100V18h70v82M${x+35} 20h50M${x+52} 83h38"/>${head(x+60,48)}<path class="body" d="M${x+60} 54v26l-15 18M${x+60} 80l18 18${finish?`M${x+60} 60l-22-18M${x+60} 60l22-18M${x+37} 38h47`:`M${x+60} 58l-22-34M${x+60} 58l22-34M${x+35} 21h50`}"/>`;}
 else if(type==='leg'){body=`<path class="machine" d="M${x+15} 96h90M${x+25} 88l25-40h28M${x+95} 25l18 60"/>${head(x+58,45)}<path class="body" d="M${x+58} 51l-8 26M${x+50} 77${finish?`l36 2 20-26`:`l24-8 22-19`}M${x+50} 77l-20 10"/>`;}
 else if(type==='row'){body=`<path class="machine" d="M${x+15} 92h110M${x+103} 25v65M${x+103} 55H${x+82}"/>${head(x+52,50)}<path class="body" d="M${x+52} 56v24l-18 12M${x+52} 80l25 12${finish?`M${x+53} 61l30-6`:`M${x+53} 61l44-6`}"/>`;}
 else if(type==='overhead'){body=`<path class="machine" d="M${x+22} 98V40h32v58"/>${head(x+58,45)}<path class="body" d="M${x+58} 51v27l-17 20M${x+58} 78l18 20${finish?`M${x+58} 58l-18-35M${x+58} 58l18-35`:`M${x+58} 58l-24-4M${x+58} 58l24-4`}"/>`;}
 else if(type==='plank'){body=`${head(x+34,62)}<path class="body" d="M${x+41} 64l55 8 28 18M${x+58} 67l-14 23M${x+103} 73l25 17"/>`;}
 else if(type==='bridge'){body=`${head(x+28,82)}<path class="body" d="M${x+35} 82${finish?`l52-28 34 34`:`l52 0 34 6`}M${x+86} ${finish?54:82}l-10 25"/>`;}
 else if(type==='raise'){body=`${head(x+60,35)}<path class="body" d="M${x+60} 42v34l-17 22M${x+60} 76l18 22${finish?`M${x+60} 51H${x+23}M${x+60} 51h37`:`M${x+60} 51l-18 30M${x+60} 51l18 30`}"/>`;}
 else if(type==='crunch'){body=`${head(x+34,finish?65:82)}<path class="body" d="M${x+40} ${finish?68:83}${finish?`q25-25 48 3`:`h48`}M${x+87} ${finish?71:83}l24-25 23 25"/>`;}
 else if(type==='knees'){body=`<path class="machine" d="M${x+25} 20h75M${x+28} 18v85M${x+98} 18v85"/>${head(x+62,44)}<path class="body" d="M${x+62} 50v28M${x+62} 52l-22-32M${x+62} 52l22-32${finish?`M${x+62} 78l24-8 10-18M${x+62} 78l-20-8-10-18`:`M${x+62} 78l15 20M${x+62} 78l-15 20`}"/>`;}
 else if(type==='curl'){body=`<path class="machine" d="M${x+15} 70h85M${x+30} 70v25M${x+85} 70v25"/>${head(x+35,55)}<path class="body" d="M${x+41} 58l45 10M${x+86} 68${finish?`l18-28`:`l28 10`}"/>`;}
 else if(type==='extension'){body=`<path class="machine" d="M${x+18} 98V48h35v50"/>${head(x+58,43)}<path class="body" d="M${x+58} 50v28M${x+58} 78${finish?`l42 0`:`l25 16`}M${x+58} 78l-17 20"/>`;}
 else{body=`${head(x+60,38)}<path class="body" d="M${x+60} 45v33l-18 20M${x+60} 78l18 20${finish?`M${x+60} 54l30-12M${x+60} 54l-30-12`:`M${x+60} 54l18 24M${x+60} 54l-18 24`}"/>`;}
 return`<rect class="panel" x="${x+2}" y="4" width="145" height="108" rx="10"/><text class="cap2" x="${x+10}" y="18" fill="${blue}">${label}</text>${body}`;
}
function motionSVG(type){return`<svg class="motion-svg" viewBox="0 0 304 118" role="img" aria-label="Стартовая и конечная позиция упражнения">${figurePanel(type,false,0)}${figurePanel(type,true,152)}<path class="move" d="M145 58h14m-5-5 5 5-5 5"/></svg>`}
function enhanceWorkout(){
 const k=$('#workoutSelect')?.value||'A';$$('#exerciseList .exercise').forEach((el,i)=>{const type=workouts[k]?.[i]?.[7];if(!type)return;const old=el.querySelector('.techsvg');if(old){const wrap=document.createElement('div');wrap.className='motion-demo';wrap.innerHTML=motionSVG(type);old.parentNode.insertBefore(wrap,old);old.remove()}})
}

function renderReminder(){
 const set=$('#settings');if(!set)return;let host=$('#reminderV11');if(!host){host=document.createElement('div');host.id='reminderV11';host.className='reminder-box';const first=set.querySelector('.card');first?.appendChild(host)}
 const native=!!window.FormaAndroid;host.innerHTML=`<h2>Напоминание</h2><div class="small muted">Системное уведомление раз в день. После перезагрузки телефона восстановится автоматически.</div><div class="grid g2 section"><div><label>Время</label><input id="remTime" type="time" value="${esc(state.reminder.time||'20:30')}"></div><div><label>Статус</label><div class="tip ${state.reminder.enabled?'greenbox':''}">${state.reminder.enabled?'Включено':'Выключено'}</div></div></div><div class="action-row"><button id="remOn" class="btn good">Включить</button><button id="remOff" class="btn">Выключить</button></div>${native?'':'<div class="small danger section">Системный мост недоступен в этой сборке.</div>'}<div class="versionline">Форма 365 · версия 1.1.0</div>`;
 $('#remOn').addEventListener('click',()=>{const t=$('#remTime').value||'20:30',[h,m]=t.split(':').map(Number);state.reminder={enabled:true,time:t};save();if(window.FormaAndroid){try{FormaAndroid.requestNotificationPermission();FormaAndroid.setDailyReminder(h,m,state.profile.gymDays)}catch(e){}}renderReminder();notice('Напоминание включено')});
 $('#remOff').addEventListener('click',()=>{state.reminder.enabled=false;save();if(window.FormaAndroid){try{FormaAndroid.cancelDailyReminder()}catch(e){}}renderReminder();notice('Напоминание выключено')});
}

function renderTrainingSummary(){
 let host=$('#trainingSummaryV11');if(!host){host=document.createElement('div');host.id='trainingSummaryV11';host.className='card section';const training=$('#training .card');training?.insertAdjacentElement('beforebegin',host)}
 const last14=state.workouts.filter(x=>inLast(x.date,14)),last=last14.slice(-1)[0];host.innerHTML=`<div class="row between"><div><h2>Тренировочный ритм</h2><div class="small muted">Последние 14 дней</div></div><span class="badge">${last14.length}/6</span></div><div class="report-grid"><div class="report-stat"><span>Выполнено</span><b>${last14.length}</b><span>из ~6</span></div><div class="report-stat"><span>Всего</span><b>${state.workouts.length}</b><span>тренировок</span></div><div class="report-stat"><span>Последняя</span><b>${last?last.type:'—'}</b><span>${last?dparse(last.date).toLocaleDateString('ru-RU'):'нет'}</span></div><div class="report-stat"><span>Неделя</span><b>${weekNo()}</b><span>${weekNo()%4===0?'разгрузка':'рабочая'}</span></div></div>`;
}

function bindRefresh(){
 ['saveDay','saveWorkout','saveMeasure','saveSettings','applyMinus','applyPlus','resetAll'].forEach(id=>$('#'+id)?.addEventListener('click',()=>setTimeout(renderEnhanced,40)));
 $$('.nav button').forEach(b=>b.addEventListener('click',()=>setTimeout(renderEnhanced,20)));
 $('#workoutSelect')?.addEventListener('change',()=>setTimeout(enhanceWorkout,20));
}
function renderEnhanced(){addTopSettings();renderWeeklyReport();renderSmart();renderCharts();renderCalendar();renderYearRoad();renderTodayFood();renderReminder();renderTrainingSummary();enhanceWorkout()}
bindRefresh();renderEnhanced();
})();
