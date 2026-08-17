(() => {
  const {QUESTIONS,CATEGORIES,shuffle,mulberry32}=window.QUIZ_DATA;
  const Y=window.YandexBridge;
  const $=s=>document.querySelector(s);
  const screen=$('#screen');
  const toast=$('#toast');
  const modalRoot=$('#modalRoot');
  const keys=['A','B','C','D'];

  const MODES={
    classic:{name:'Классика',icon:'◆',tag:'10 вопросов',desc:'Сбалансированный раунд по всем темам.',count:10,perQuestion:20},
    blitz:{name:'Блиц',icon:'⚡',tag:'60 секунд',desc:'Отвечай как можно быстрее, пока идёт общий таймер.',count:999,totalTime:60},
    survival:{name:'Выживание',icon:'♥',tag:'3 жизни',desc:'Одна ошибка стоит жизни. Продержись максимально долго.',count:35,perQuestion:18,hearts:3},
    ladder:{name:'Лестница знаний',icon:'▲',tag:'15 ступеней',desc:'Сложность растёт от простого к экспертному.',count:15,perQuestion:20,ladder:true},
    truefalse:{name:'Верю / не верю',icon:'◐',tag:'быстрый режим',desc:'Только утверждения: верно или неверно.',count:15,perQuestion:11,filter:q=>q.type==='tf'},
    hardcore:{name:'Эксперт',icon:'✦',tag:'12 сложных',desc:'Меньше времени, больше сложных вопросов и очков.',count:12,perQuestion:13,filter:q=>q.difficulty!=='easy',multiplier:1.35},
    marathon:{name:'Марафон',icon:'∞',tag:'25 вопросов',desc:'Длинная сессия с растущим бонусом за серии.',count:25,perQuestion:22,multiplier:1.15},
    daily:{name:'Испытание дня',icon:'☀',tag:'1 раз в день',desc:'Одинаковый набор для всех игроков сегодня.',count:12,perQuestion:18,daily:true,multiplier:1.25}
  };

  const DEFAULT={
    v:1,xp:0,coins:120,bestScore:0,ratingPoints:0,totalAnswers:0,correctAnswers:0,gamesPlayed:0,level:1,
    streak:0,lastPlayDate:'',dailyDate:'',dailyProgress:{answers:0,correct:0,games:0,bestCombo:0,score:0,categories:[]},dailyClaimed:[],dailyChest:false,dailyPlayed:'',
    categoryStats:{},modeStats:{},achievements:[],adCounter:0,settings:{reducedMotion:false}
  };
  let state=structuredClone(DEFAULT);
  let session=null;
  let saveTimer=null;
  let tickTimer=null;
  let toastTimer=null;
  let currentPage='home';

  const esc=s=>String(s??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const fmt=n=>new Intl.NumberFormat('ru-RU').format(Math.floor(n||0));
  const dayKey=()=>new Date(Y.serverTime()).toISOString().slice(0,10);
  const levelOf=xp=>Math.floor(Math.sqrt((xp||0)/260))+1;
  const levelBase=l=>Math.pow(Math.max(0,l-1),2)*260;
  const levelNext=l=>Math.pow(l,2)*260;
  const ratingScore=()=>Math.max(0,Math.floor(state.ratingPoints+state.xp*.35+state.bestScore*.15));

  function mergeState(base,incoming){
    if(!incoming||typeof incoming!=='object')return base;
    const next={...base,...incoming};
    next.settings={...base.settings,...incoming.settings};
    next.dailyProgress={...base.dailyProgress,...incoming.dailyProgress};
    next.categoryStats={...base.categoryStats,...incoming.categoryStats};
    next.modeStats={...base.modeStats,...incoming.modeStats};
    next.achievements=Array.isArray(incoming.achievements)?incoming.achievements:[];
    next.dailyClaimed=Array.isArray(incoming.dailyClaimed)?incoming.dailyClaimed:[];
    return next;
  }
  function loadLocal(){try{const raw=localStorage.getItem('umniversum-save-v1');if(raw)state=mergeState(structuredClone(DEFAULT),JSON.parse(raw))}catch(e){console.warn(e)}}
  function saveLocal(){try{localStorage.setItem('umniversum-save-v1',JSON.stringify(state))}catch(e){console.warn(e)}}
  function queueSave(force=false){
    saveLocal();clearTimeout(saveTimer);
    if(force){Y.save(state);return}
    saveTimer=setTimeout(()=>Y.save(state),2500);
  }
  function syncHeader(){
    state.level=levelOf(state.xp);
    $('#levelValue').textContent=state.level;$('#coinValue').textContent=fmt(state.coins);$('#streakValue').textContent=state.streak;
  }
  function setActiveNav(page){document.querySelectorAll('.nav-btn').forEach(b=>b.classList.toggle('active',b.dataset.action===page || (page==='categories'&&b.dataset.action==='modes')))}
  function showToast(text){toast.textContent=text;toast.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>toast.classList.remove('show'),1800)}
  function showModal(title,body,actions=[['Закрыть','ghost','close']]){
    modalRoot.innerHTML=`<div class="modal-backdrop"><div class="modal"><h3>${esc(title)}</h3><p>${body}</p><div class="modal-actions">${actions.map(a=>`<button class="${a[1]}" data-modal="${a[2]}">${esc(a[0])}</button>`).join('')}</div></div></div>`;
  }
  function closeModal(){modalRoot.innerHTML=''}

  function resetDailyIfNeeded(){
    const d=dayKey();
    if(state.dailyDate!==d){
      state.dailyDate=d;state.dailyProgress={answers:0,correct:0,games:0,bestCombo:0,score:0,categories:[]};state.dailyClaimed=[];state.dailyChest=false;queueSave();
    }
  }
  function updateStreak(){
    const today=dayKey();if(state.lastPlayDate===today)return;
    const y=new Date(today+'T00:00:00Z');y.setUTCDate(y.getUTCDate()-1);const yesterday=y.toISOString().slice(0,10);
    state.streak=state.lastPlayDate===yesterday?state.streak+1:1;state.lastPlayDate=today;
  }

  function dailyQuests(){
    resetDailyIfNeeded();
    const seed=Number(dayKey().replaceAll('-',''));const rng=mulberry32(seed);
    const pool=[
      {id:'answers',icon:'❓',title:'Разминка',text:'Ответить на 20 вопросов',target:20,value:()=>state.dailyProgress.answers,reward:90},
      {id:'correct',icon:'✓',title:'Точный ум',text:'Дать 15 верных ответов',target:15,value:()=>state.dailyProgress.correct,reward:110},
      {id:'games',icon:'◆',title:'В движении',text:'Завершить 2 игровых раунда',target:2,value:()=>state.dailyProgress.games,reward:100},
      {id:'combo',icon:'🔥',title:'Серия',text:'Сделать серию из 6 верных ответов',target:6,value:()=>state.dailyProgress.bestCombo,reward:120},
      {id:'score',icon:'★',title:'Охота за очками',text:'Набрать 2500 очков за день',target:2500,value:()=>state.dailyProgress.score,reward:120},
      {id:'categories',icon:'◎',title:'Кругозор',text:'Ответить в 5 разных темах',target:5,value:()=>state.dailyProgress.categories.length,reward:100}
    ];
    return shuffle(pool,rng).slice(0,3);
  }
  function claimQuest(id){
    const q=dailyQuests().find(x=>x.id===id);if(!q)return;
    if(q.value()<q.target){showToast('Задание ещё не выполнено');return}
    if(state.dailyClaimed.includes(id)){showToast('Награда уже получена');return}
    state.dailyClaimed.push(id);state.coins+=q.reward;state.xp+=q.reward*2;showToast(`+${q.reward} монет`);checkAchievements();queueSave();syncHeader();renderDaily();
  }
  function claimDailyChest(){
    const qs=dailyQuests();if(state.dailyChest)return;
    if(!qs.every(q=>state.dailyClaimed.includes(q.id))){showToast('Сначала забери все 3 награды');return}
    state.dailyChest=true;state.coins+=350;state.xp+=500;showToast('Сундук дня: +350 монет, +500 XP');queueSave();syncHeader();renderDaily();
  }

  const ACHIEVEMENTS=[
    {id:'first',icon:'🌱',name:'Первый шаг',desc:'Завершить первый раунд',test:()=>state.gamesPlayed>=1},
    {id:'q100',icon:'🧠',name:'Сотня',desc:'Ответить на 100 вопросов',test:()=>state.totalAnswers>=100},
    {id:'correct100',icon:'🎯',name:'Знаток',desc:'100 верных ответов',test:()=>state.correctAnswers>=100},
    {id:'combo10',icon:'🔥',name:'Без ошибки',desc:'Серия из 10 верных',test:()=>Object.values(state.modeStats).some(x=>(x.bestCombo||0)>=10)},
    {id:'games10',icon:'🏁',name:'Постоянный игрок',desc:'Завершить 10 раундов',test:()=>state.gamesPlayed>=10},
    {id:'score5k',icon:'💎',name:'Пять тысяч',desc:'Набрать 5000 очков за раунд',test:()=>state.bestScore>=5000},
    {id:'explorer',icon:'🌍',name:'Энциклопедист',desc:'Сыграть в 10 темах',test:()=>Object.keys(state.categoryStats).length>=10},
    {id:'streak7',icon:'☀',name:'Неделя знаний',desc:'Серия входов 7 дней',test:()=>state.streak>=7},
    {id:'daily',icon:'📅',name:'Испытатель',desc:'Пройти испытание дня',test:()=>!!state.dailyPlayed}
  ];
  function checkAchievements(){
    const unlocked=[];for(const a of ACHIEVEMENTS){if(!state.achievements.includes(a.id)&&a.test()){state.achievements.push(a.id);state.coins+=75;unlocked.push(a)}}
    if(unlocked.length)showToast(`Достижение: ${unlocked[0].name} · +75`);
  }

  function renderHome(){
    currentPage='home';setActiveNav('home');syncHeader();resetDailyIfNeeded();
    const acc=state.totalAnswers?Math.round(state.correctAnswers/state.totalAnswers*100):0;
    screen.innerHTML=`
      <section class="hero">
        <div class="hero-card">
          <div class="eyebrow">${fmt(QUESTIONS.length)} вопросов · 19 тем</div>
          <h1>Проверь, насколько широк твой кругозор</h1>
          <p>Быстрые раунды, выживание, лестница сложности, тематические испытания, ежедневные задания и общий рейтинг игроков.</p>
          <div class="hero-actions"><button class="primary" data-mode="classic">Играть сейчас</button><button class="secondary" data-action="modes">Все режимы</button></div>
        </div>
        <div class="daily-preview">
          <div class="day-orb">☀</div><div class="eyebrow">Испытание дня</div><h3>${state.dailyPlayed===dayKey()?'Сегодня пройдено':'12 вопросов для всех'}</h3>
          <p>${state.dailyPlayed===dayKey()?'Можно переиграть ради результата, но награда дня уже засчитана.':'Набор меняется каждые сутки по серверному времени.'}</p>
          <button class="primary" data-mode="daily">${state.dailyPlayed===dayKey()?'Переиграть':'Начать испытание'}</button>
        </div>
      </section>
      <div class="section-head"><div><h2>Популярные режимы</h2><p>Меняй темп и правила игры</p></div><button data-action="modes">Показать все</button></div>
      <section class="mode-grid">${['classic','blitz','survival','ladder'].map(modeCard).join('')}</section>
      <div class="section-head"><div><h2>Твоя статистика</h2><p>Прогресс сохраняется автоматически</p></div></div>
      <section class="stat-grid">
        <div class="metric"><small>Лучший результат</small><b>${fmt(state.bestScore)}</b><div class="trend">★ личный рекорд</div></div>
        <div class="metric"><small>Точность</small><b>${acc}%</b><div class="trend">${fmt(state.correctAnswers)} верных</div></div>
        <div class="metric"><small>Раундов</small><b>${fmt(state.gamesPlayed)}</b><div class="trend">${fmt(state.totalAnswers)} ответов</div></div>
        <div class="metric"><small>Рейтинг-очки</small><b>${fmt(ratingScore())}</b><div class="trend">глобальный счёт</div></div>
      </section>
      <div class="section-head"><div><h2>Темы</h2><p>От науки и истории до кино и логики</p></div><button data-action="categories">Все темы</button></div>
      <section class="category-grid">${Object.entries(CATEGORIES).slice(0,10).map(([id,c])=>categoryCard(id,c)).join('')}</section>`;
  }
  function modeCard(id){const m=MODES[id];return `<button class="mode-card" data-mode="${id}"><span class="mode-icon">${m.icon}</span><h3>${m.name}</h3><p>${m.desc}</p><span class="mode-tag">${m.tag}</span></button>`}
  function categoryCard(id,c){const count=QUESTIONS.filter(q=>q.category===id).length;return `<button class="category-card" data-category="${id}"><span class="cat-emoji">${c.emoji}</span><b>${c.name}</b><small>${count} вопросов</small></button>`}

  function renderModes(){
    currentPage='modes';setActiveNav('modes');
    screen.innerHTML=`<div class="page-head"><div><h1>Режимы игры</h1><p>Каждый режим меняет темп, риск и награды.</p></div></div><section class="mode-grid">${Object.keys(MODES).map(modeCard).join('')}</section><div class="section-head"><div><h2>Тематические раунды</h2><p>Выбери одну область знаний</p></div></div><section class="category-grid">${Object.entries(CATEGORIES).map(([id,c])=>categoryCard(id,c)).join('')}</section>`;
  }
  function renderCategories(){currentPage='categories';setActiveNav('modes');screen.innerHTML=`<div class="page-head"><div><h1>Все темы</h1><p>${Object.keys(CATEGORIES).length} направлений знаний</p></div></div><section class="category-grid">${Object.entries(CATEGORIES).map(([id,c])=>categoryCard(id,c)).join('')}</section>`}

  function renderDaily(){
    currentPage='daily';setActiveNav('daily');resetDailyIfNeeded();const qs=dailyQuests();const allClaimed=qs.every(q=>state.dailyClaimed.includes(q.id));
    screen.innerHTML=`<div class="page-head"><div><h1>Ежедневные задания</h1><p>Сброс в новые календарные сутки по серверному времени.</p></div><button class="primary" data-mode="daily">Испытание дня</button></div>
      <div class="content-card"><div class="quest-list">${qs.map(q=>{const val=Math.min(q.value(),q.target),done=val>=q.target,claimed=state.dailyClaimed.includes(q.id);return `<div class="quest"><div class="quest-icon">${q.icon}</div><div><h4>${q.title}</h4><p>${q.text}</p><div class="progress"><i style="width:${clamp(val/q.target*100,0,100)}%"></i></div></div><div class="quest-reward"><b>+${q.reward} ●</b><button class="${claimed?'ghost':done?'primary':'secondary'}" data-claim="${q.id}" ${!done||claimed?'disabled':''}>${claimed?'Получено':done?'Забрать':`${val}/${q.target}`}</button></div></div>`}).join('')}</div></div>
      <div class="section-head"><div><h2>Сундук дня</h2><p>Открой после получения всех трёх наград</p></div></div>
      <div class="league-banner"><div class="league-icon">🎁</div><div><h3>${state.dailyChest?'Сундук открыт':'+350 монет и +500 XP'}</h3><p>${state.dailyChest?'Возвращайся завтра за новой серией заданий.':allClaimed?'Все задания закрыты — награда готова.':'Выполни три задания и забери общий бонус.'}</p></div><button class="primary" style="margin-left:auto" data-chest ${!allClaimed||state.dailyChest?'disabled':''}>${state.dailyChest?'Получено':'Открыть'}</button></div>`;
  }

  async function renderRating(){
    currentPage='rating';setActiveNav('rating');
    screen.innerHTML=`<div class="page-head"><div><h1>Рейтинг игроков</h1><p>Глобальный лидерборд Яндекс Игр · техническое имя global_score</p></div></div><div class="league-banner"><div class="league-icon">♛</div><div><h3>${leagueName(ratingScore())}</h3><p>Твой рейтинг-счёт: ${fmt(ratingScore())}</p></div>${Y.isAuthorized()?'':'<button class="primary" data-auth style="margin-left:auto">Войти</button>'}</div><div class="content-card" id="leaderWrap"><div class="empty">Загружаем таблицу лидеров…</div></div>`;
    const lb=await Y.getLeaderboard();if(currentPage!=='rating')return;
    const wrap=$('#leaderWrap');
    if(!lb?.entries?.length){wrap.innerHTML=`<div class="empty">${Y.local?'В локальном запуске глобальный рейтинг недоступен. После загрузки в Яндекс Игры здесь появятся реальные игроки.':'Лидерборд пока пуст или ещё не создан в Консоли Яндекс Игр.'}</div>`;return}
    wrap.innerHTML=`<div class="leaderboard">${lb.entries.map(e=>`<div class="leader-row ${e.player?.uniqueID===Y.player?.getUniqueID?.()?'me':''}"><div class="leader-rank">${e.rank}</div><div class="leader-name"><b>${esc(e.player?.publicName||'Игрок')}</b><small>${leagueName(e.score)}</small></div><div class="leader-score">${fmt(e.score)}</div></div>`).join('')}</div>`;
  }
  function leagueName(score){if(score>=60000)return 'Лига Гениев';if(score>=25000)return 'Алмазная лига';if(score>=10000)return 'Золотая лига';if(score>=4000)return 'Серебряная лига';return 'Бронзовая лига'}

  function renderProfile(){
    currentPage='profile';setActiveNav('profile');syncHeader();const l=state.level,from=levelBase(l),to=levelNext(l),pct=clamp((state.xp-from)/(to-from)*100,0,100);const acc=state.totalAnswers?Math.round(state.correctAnswers/state.totalAnswers*100):0;
    screen.innerHTML=`<div class="page-head"><div><h1>Профиль</h1><p>Твой прогресс, достижения и статистика.</p></div></div><div class="profile-grid">
      <div class="content-card profile-card"><div class="avatar">🧠</div><h2>${Y.isAuthorized()?esc(Y.player?.getName?.()||'Игрок'):'Гость'}</h2><p style="color:var(--muted)">Уровень ${l} · ${leagueName(ratingScore())}</p><div class="xp-track"><i style="width:${pct}%"></i></div><small>${fmt(state.xp-from)} / ${fmt(to-from)} XP до следующего уровня</small><div class="action-row" style="margin-top:20px">${Y.isAuthorized()?'<button class="secondary" disabled>Яндекс ID подключён</button>':'<button class="primary" data-auth>Войти в Яндекс</button>'}</div></div>
      <div><section class="stat-grid"><div class="metric"><small>Точность</small><b>${acc}%</b></div><div class="metric"><small>Серия дней</small><b>${state.streak} 🔥</b></div><div class="metric"><small>Лучший счёт</small><b>${fmt(state.bestScore)}</b></div><div class="metric"><small>Монеты</small><b>${fmt(state.coins)}</b></div></section><div class="section-head"><div><h2>Достижения</h2><p>${state.achievements.length} из ${ACHIEVEMENTS.length}</p></div></div><div class="badges-grid">${ACHIEVEMENTS.map(a=>`<div class="badge ${state.achievements.includes(a.id)?'':'locked'}"><div class="bicon">${a.icon}</div><b>${a.name}</b><small>${a.desc}</small></div>`).join('')}</div></div>
    </div>`;
  }

  function renderShop(){
    currentPage='shop';setActiveNav('');screen.innerHTML=`<div class="page-head"><div><h1>Помощь в викторине</h1><p>Монеты зарабатываются игрой и ежедневными заданиями.</p></div></div><section class="mode-grid">
      <div class="mode-card"><span class="mode-icon">½</span><h3>50 / 50</h3><p>В каждом раунде один бесплатный шанс убрать два неверных ответа.</p><span class="mode-tag">встроено в раунд</span></div>
      <div class="mode-card"><span class="mode-icon">↻</span><h3>Пропуск</h3><p>Меняет текущий вопрос без штрафа за ошибку.</p><span class="mode-tag">50 монет</span></div>
      <div class="mode-card"><span class="mode-icon">♥</span><h3>Вторая жизнь</h3><p>В режиме выживания можно вернуть одну жизнь за rewarded video.</p><span class="mode-tag">по желанию</span></div>
      <div class="mode-card"><span class="mode-icon">×2</span><h3>Двойная награда</h3><p>После раунда можно удвоить заработанные монеты за rewarded video.</p><span class="mode-tag">по желанию</span></div></section>`;
  }

  function buildSession(modeId,category=null){
    const m=MODES[modeId]||MODES.classic;let pool=QUESTIONS;
    if(category)pool=pool.filter(q=>q.category===category);if(m.filter)pool=pool.filter(m.filter);
    let rng;if(m.daily)rng=mulberry32(Number(dayKey().replaceAll('-',''))+147);else rng=mulberry32((Date.now()^Math.floor(Math.random()*1e9))>>>0);
    let selected=shuffle(pool,rng);
    if(m.ladder){const easy=shuffle(pool.filter(q=>q.difficulty==='easy'),rng),medium=shuffle(pool.filter(q=>q.difficulty==='medium'),rng),hard=shuffle(pool.filter(q=>q.difficulty==='hard'),rng);selected=[...easy.slice(0,5),...medium.slice(0,6),...hard.slice(0,4)];if(selected.length<m.count)selected.push(...shuffle(pool.filter(q=>!selected.includes(q)),rng).slice(0,m.count-selected.length))}
    else selected=selected.slice(0,Math.min(m.count,selected.length));
    session={modeId,category,mode:m,questions:selected,index:0,score:0,correct:0,wrong:0,combo:0,maxCombo:0,hearts:m.hearts??null,startedAt:Date.now(),questionStarted:Date.now(),remaining:m.totalTime??m.perQuestion,totalRemaining:m.totalTime??null,paused:false,answered:false,lifeline:true,skipUsed:0,coinsEarned:0,xpEarned:0};
  }
  function startMode(modeId,category=null){
    resetDailyIfNeeded();updateStreak();buildSession(modeId,category);currentPage='quiz';setActiveNav('');document.querySelector('#bottomNav').style.display='none';Y.gameplayStart();startTick();renderQuestion();queueSave();syncHeader();
  }
  function currentQuestion(){return session?.questions?.[session.index]}
  function difficultyLabel(d){return d==='hard'?'Сложный':d==='medium'?'Средний':'Лёгкий'}
  function renderQuestion(){
    if(!session)return;if(session.index>=session.questions.length){finishSession();return}const q=currentQuestion();if(!q){finishSession();return}
    session.answered=false;session.questionStarted=Date.now();if(session.mode.perQuestion)session.remaining=session.mode.perQuestion;
    const total=session.mode.totalTime?session.mode.totalTime:session.mode.perQuestion;const progress=session.modeId==='blitz'?session.remaining/total:session.index/session.questions.length;
    screen.innerHTML=`<div class="quiz-layout"><div class="quiz-top"><div class="round-meta"><small>${esc(session.mode.name)}</small><b>${session.modeId==='blitz'?`${session.correct} ✓`: `${session.index+1}/${session.questions.length}`}</b></div><div class="timer-wrap"><div id="timerBar" class="timer-bar" style="width:${session.modeId==='blitz'?100:100}%"></div></div><div class="round-meta" style="text-align:right"><small>Счёт</small><b id="quizScore">${fmt(session.score)}</b></div></div>
      <article class="quiz-card"><div class="question-badges"><span class="pill">${CATEGORIES[q.category]?.emoji||'❓'} ${esc(CATEGORIES[q.category]?.name||q.category)}</span><span class="pill diff-${q.difficulty}">${difficultyLabel(q.difficulty)}</span>${session.hearts!==null?`<span class="pill">${'♥'.repeat(Math.max(0,session.hearts))}${'♡'.repeat(Math.max(0,(session.mode.hearts||0)-session.hearts))}</span>`:''}</div><h1 class="question">${esc(q.q)}</h1>
      <div class="answers">${q.answers.map((a,i)=>`<button class="answer" data-answer="${i}"><span class="answer-key">${keys[i]||i+1}</span><span>${esc(a)}</span></button>`).join('')}</div>
      <div class="quiz-footer"><div class="lifelines"><button class="life-btn" data-life ${session.lifeline?'':'disabled'}>½ 50/50</button><button class="life-btn" data-skip ${state.coins>=50?'':'disabled'}>↻ Пропуск · 50</button>${session.modeId==='survival'&&session.hearts<3?'<button class="life-btn" data-life-ad>♥ Жизнь · реклама</button>':''}</div><div class="combo">${session.combo>=2?`🔥 Серия ×${session.combo}`:''}</div></div></article></div>`;
    updateTimerUI();
  }
  function startTick(){stopTick();tickTimer=setInterval(()=>{
    if(!session||session.paused||session.answered)return;
    session.remaining=Math.max(0,session.remaining-.1);updateTimerUI();
    if(session.remaining<=0){if(session.modeId==='blitz')finishSession();else timeOut()}
  },100)}
  function stopTick(){clearInterval(tickTimer);tickTimer=null}
  function updateTimerUI(){
    const bar=$('#timerBar');if(!bar||!session)return;const total=session.modeId==='blitz'?session.mode.totalTime:session.mode.perQuestion;if(!total)return;const pct=clamp(session.remaining/total*100,0,100);bar.style.width=pct+'%';bar.classList.toggle('danger',pct<28)
  }
  function answerQuestion(idx){
    if(!session||session.answered)return;const q=currentQuestion();session.answered=true;const buttons=[...document.querySelectorAll('.answer')];buttons.forEach(b=>b.disabled=true);const ok=idx===q.correct;
    buttons[q.correct]?.classList.add('correct');if(!ok)buttons[idx]?.classList.add('wrong');
    const timeBonus=Math.floor((session.remaining||0)*8);const diff=q.difficulty==='hard'?1.5:q.difficulty==='medium'?1.2:1;const base=Math.floor((100+timeBonus)*diff*(session.mode.multiplier||1));
    state.totalAnswers++;state.dailyProgress.answers++;
    const cat=state.categoryStats[q.category]||(state.categoryStats[q.category]={answers:0,correct:0});cat.answers++;
    if(!state.dailyProgress.categories.includes(q.category))state.dailyProgress.categories.push(q.category);
    if(ok){session.correct++;session.combo++;session.maxCombo=Math.max(session.maxCombo,session.combo);state.correctAnswers++;state.dailyProgress.correct++;cat.correct++;const comboBonus=Math.min(200,(session.combo-1)*15);session.score+=base+comboBonus;showToast(session.combo>=3?`Верно! Серия ×${session.combo}`:'Верно!')}
    else{session.wrong++;session.combo=0;if(session.hearts!==null){session.hearts--;if(session.hearts<=0){showToast('Жизни закончились');setTimeout(finishSession,700);return}}showToast('Неверно')}
    state.dailyProgress.bestCombo=Math.max(state.dailyProgress.bestCombo,session.maxCombo);$('#quizScore').textContent=fmt(session.score);queueSave();
    setTimeout(()=>{if(!session)return;session.index++;if(session.modeId==='blitz'&&session.remaining<=0)finishSession();else renderQuestion()},720);
  }
  function timeOut(){
    if(!session||session.answered)return;session.answered=true;const q=currentQuestion();state.totalAnswers++;state.dailyProgress.answers++;state.categoryStats[q.category]??={answers:0,correct:0};state.categoryStats[q.category].answers++;if(!state.dailyProgress.categories.includes(q.category))state.dailyProgress.categories.push(q.category);session.wrong++;session.combo=0;if(session.hearts!==null)session.hearts--;document.querySelectorAll('.answer').forEach((b,i)=>{b.disabled=true;if(i===q.correct)b.classList.add('correct')});showToast('Время вышло');
    if(session.hearts!==null&&session.hearts<=0){setTimeout(finishSession,700);return}setTimeout(()=>{if(session){session.index++;renderQuestion()}},720)
  }
  function useLifeLine(){
    if(!session||!session.lifeline||session.answered)return;const q=currentQuestion();const wrong=[...document.querySelectorAll('.answer')].filter((_,i)=>i!==q.correct);shuffle(wrong,Math.random).slice(0,Math.max(1,wrong.length-1)).forEach(b=>{b.disabled=true;b.style.visibility='hidden'});session.lifeline=false;const btn=$('[data-life]');if(btn)btn.disabled=true;showToast('Убраны лишние варианты')
  }
  function useSkip(){
    if(!session||session.answered||state.coins<50)return;state.coins-=50;session.skipUsed++;session.index++;queueSave();syncHeader();showToast('Вопрос пропущен');renderQuestion()
  }
  async function reviveByAd(){
    if(!session||session.modeId!=='survival'||session.hearts>=3)return;session.paused=true;const ok=await Y.rewardedAd();if(!session)return;session.paused=false;Y.gameplayStart();if(ok){session.hearts++;showToast('+1 жизнь');renderQuestion()}else showToast('Награда не получена')
  }

  async function finishSession(){
    if(!session)return;stopTick();Y.gameplayStop();const s=session;session=null;document.querySelector('#bottomNav').style.display='grid';
    const answered=s.correct+s.wrong;const accuracy=answered?Math.round(s.correct/answered*100):0;const xp=Math.floor(s.score*.18+s.correct*18);const coins=Math.max(10,Math.floor(s.score/70));
    state.gamesPlayed++;state.xp+=xp;state.coins+=coins;state.ratingPoints+=s.score;state.bestScore=Math.max(state.bestScore,s.score);state.dailyProgress.games++;state.dailyProgress.score+=s.score;state.dailyProgress.bestCombo=Math.max(state.dailyProgress.bestCombo,s.maxCombo);
    const ms=state.modeStats[s.modeId]||(state.modeStats[s.modeId]={games:0,best:0,bestCombo:0});ms.games++;ms.best=Math.max(ms.best,s.score);ms.bestCombo=Math.max(ms.bestCombo,s.maxCombo);
    if(s.mode.daily)state.dailyPlayed=dayKey();checkAchievements();queueSave(true);syncHeader();Y.setScore(ratingScore());
    state.adCounter=(state.adCounter||0)+1;queueSave();
    renderResult(s,{answered,accuracy,xp,coins});
    if(state.adCounter%3===0)setTimeout(()=>Y.fullscreenAd(),250);
  }
  function renderResult(s,r){
    currentPage='result';setActiveNav('');screen.innerHTML=`<div class="content-card result-card"><div class="result-orb">${r.accuracy>=90?'🏆':r.accuracy>=70?'🌟':'🧠'}</div><div class="eyebrow">${esc(s.mode.name)} завершён</div><h1>${fmt(s.score)} очков</h1><p>${resultPhrase(r.accuracy)}</p><div class="result-stats"><div class="metric"><small>Верно</small><b>${s.correct}</b></div><div class="metric"><small>Точность</small><b>${r.accuracy}%</b></div><div class="metric"><small>Серия</small><b>×${s.maxCombo}</b></div><div class="metric"><small>Награда</small><b>+${r.coins} ●</b></div></div><p>Получено <b>+${r.xp} XP</b>. Рейтинг-счёт: <b>${fmt(ratingScore())}</b>.</p><div class="action-row"><button class="primary" data-replay="${s.modeId}" data-cat="${s.category||''}">Ещё раз</button><button class="secondary" data-double="${r.coins}">×2 монеты · реклама</button><button class="ghost" data-action="home">На главную</button></div></div>`;
  }
  function resultPhrase(a){if(a===100)return 'Идеальный раунд. Ни одной ошибки!';if(a>=90)return 'Очень сильный результат.';if(a>=75)return 'Отличный кругозор — ещё немного до идеала.';if(a>=55)return 'Хорошая база. Следующий раунд будет ещё лучше.';return 'Каждый вопрос прокачивает знания. Попробуй другой режим.'}
  async function doubleReward(amount,button){
    if(button.disabled)return;button.disabled=true;const ok=await Y.rewardedAd();if(ok){state.coins+=Number(amount)||0;queueSave(true);syncHeader();showToast(`Ещё +${amount} монет`);button.textContent='Награда получена'}else{button.disabled=false;showToast('Видео не завершено')}
  }

  async function authorizeAndRefresh(){
    const ok=await Y.authorize();if(ok){const cloud=await Y.loadSave();if(cloud){state=mergeState(state,cloud);saveLocal()}syncHeader();showToast('Вход выполнен');if(currentPage==='rating')renderRating();else renderProfile()}else showToast('Не удалось войти')
  }

  document.addEventListener('click',e=>{
    const t=e.target.closest('button');if(!t)return;
    if(t.dataset.answer!==undefined){answerQuestion(Number(t.dataset.answer));return}
    if(t.dataset.mode){startMode(t.dataset.mode);return}
    if(t.dataset.category){startMode('classic',t.dataset.category);return}
    if(t.dataset.life!==undefined){useLifeLine();return}
    if(t.dataset.skip!==undefined){useSkip();return}
    if(t.dataset.lifeAd!==undefined){reviveByAd();return}
    if(t.dataset.claim){claimQuest(t.dataset.claim);return}
    if(t.dataset.chest!==undefined){claimDailyChest();return}
    if(t.dataset.auth!==undefined){authorizeAndRefresh();return}
    if(t.dataset.replay){startMode(t.dataset.replay,t.dataset.cat||null);return}
    if(t.dataset.double){doubleReward(t.dataset.double,t);return}
    if(t.dataset.modal==='close'){closeModal();return}
    const a=t.dataset.action;if(!a)return;
    ({home:renderHome,modes:renderModes,daily:renderDaily,rating:renderRating,profile:renderProfile,categories:renderCategories,shop:renderShop}[a]||renderHome)();
  });

  document.addEventListener('keydown',e=>{
    if(!session||session.answered)return;const idx=['1','2','3','4','a','b','c','d','A','B','C','D'].indexOf(e.key);if(idx>=0)answerQuestion(idx%4)
  });
  document.addEventListener('visibilitychange',()=>{if(!session)return;session.paused=document.hidden;if(document.hidden)Y.gameplayStop();else Y.gameplayStart()});
  Y.on('pause',()=>{if(session)session.paused=true});Y.on('resume',()=>{if(session){session.paused=false;Y.gameplayStart()}});
  window.addEventListener('pagehide',()=>{saveLocal();Y.save(state);Y.gameplayStop()});

  async function boot(){
    loadLocal();await Y.init();const cloud=await Y.loadSave();if(cloud)state=mergeState(state,cloud);resetDailyIfNeeded();updateStreak();checkAchievements();queueSave();syncHeader();renderHome();Y.gameReady();
    if(Y.local)showToast('Локальный режим: SDK Яндекс Игр не найден');
  }
  boot();
})();
