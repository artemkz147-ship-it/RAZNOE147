export const HEROES = [
  {id:'donut_knight',name:'Рыцарь Пончик',asset:'hero01',weapon:'donut',cost:0,desc:'Надёжный стрелок в тяжёлой броне.',bonus:{armor:.12,hp:.08}},
  {id:'dew_fairy',name:'Фея Росинка',asset:'hero02',weapon:'firefly',cost:0,desc:'Быстро растёт по уровню и собирает опыт.',bonus:{xp:.16,pickup:.18}},
  {id:'hamster_pirate',name:'Пират Хомяк',asset:'hero03',weapon:'pirate_bomb',cost:0,desc:'Сильные взрывы и повышенная удача.',bonus:{luck:.20,splash:.16}},
  {id:'candy_witch',name:'Конфетная Ведьма',asset:'hero04',weapon:'rainbow_fan',cost:900,desc:'Широкие залпы и высокий базовый урон.',bonus:{damage:.12,projectiles:1}},
  {id:'mushroom_druid',name:'Грибной Друид',asset:'hero05',weapon:'flower_burst',cost:1250,desc:'Большая площадь атак и контроль толпы.',bonus:{area:.18,slow:.08}},
  {id:'cat_mage',name:'Кот-Маг',asset:'hero06',weapon:'amber_comet',cost:1600,desc:'Часто атакует и пробивает строй.',bonus:{cooldown:.12,pierce:1}},
  {id:'toy_robot',name:'Игрушечный Робот',asset:'hero07',weapon:'blast_bot',cost:1950,desc:'Боты преследуют цели и взрываются.',bonus:{damageReduction:.10,homing:.18}},
  {id:'baker_alchemist',name:'Пекарь Алхимик',asset:'hero08',weapon:'pancake',cost:2300,desc:'Возвратные снаряды бьют врагов дважды.',bonus:{duration:.20,regen:.18}},
  {id:'fox_archer',name:'Лисёнок Лучник',asset:'hero09',weapon:'fox_bow',cost:2650,desc:'Быстрые критические стрелы.',bonus:{speed:.12,crit:.08}},
  {id:'cloud_princess',name:'Облачная Принцесса',asset:'hero10',weapon:'cloud_orb',cost:3000,desc:'Самонаводящиеся облачные заряды.',bonus:{projectileSpeed:.16,dodge:.08}},
  {id:'watermelon_captain',name:'Капитан Арбуз',asset:'hero11',weapon:'watermelon',cost:3350,desc:'Тяжёлые снаряды с крупным взрывом.',bonus:{knockback:.22,splash:.18}},
  {id:'music_gnome',name:'Музыкальный Гном',asset:'hero12',weapon:'music_wave',cost:3700,desc:'Волновые залпы пробивают большие группы.',bonus:{area:.12,cooldown:.08}},
  {id:'lightning_bee',name:'Пчёлка Молния',asset:'hero13',weapon:'smart_bee',cost:4050,desc:'Очень быстрые самонаводящиеся атаки.',bonus:{speed:.15,cooldown:.08}},
  {id:'snow_penguin',name:'Снежный Пингвин',asset:'hero14',weapon:'snowball',cost:4400,desc:'Замедляет врагов и постепенно лечится.',bonus:{regen:.35,slow:.14}},
  {id:'pumpkin_jester',name:'Тыквенный Шут',asset:'hero15',weapon:'pumpkin',cost:4750,desc:'Дальние тыквенные мины и цепные взрывы.',bonus:{luck:.12,splash:.12}}
];

const levelText = (name, evolution) => [
  `Усилить ${name}: +22% урона.`,
  'Перезарядка быстрее на 12%.',
  'Дополнительный снаряд или заряд.',
  'Увеличение размера атаки на 16%.',
  'Ещё одно пробивание или новая цель.',
  'Скорость снаряда +18%.',
  'Сильный дополнительный эффект.',
  `ЭВОЛЮЦИЯ: ${evolution}.`
];

export const WEAPONS = [
  {id:'donut',name:'Бублики-Спутники',evolution:'Планетарная Пекарня',model:'weapon01',projectile:'food_donut',type:'shot',damage:18,cooldown:.72,speed:18,count:2,spread:.13,pierce:1,range:27,scale:.32,passive:'area'},
  {id:'firefly',name:'Цепь Светлячков',evolution:'Рой Зарниц',model:'weapon02',projectile:'gem',type:'chain',damage:14,cooldown:.68,speed:22,count:1,chain:3,chainRadius:6,range:28,scale:.21,passive:'cooldown'},
  {id:'pirate_bomb',name:'Пиратская Бомба',evolution:'Пороховой Шторм',model:'weapon03',projectile:'bomb',type:'bomb',damage:30,cooldown:1.26,speed:13,count:1,splash:3.6,range:26,scale:.34,passive:'area'},
  {id:'rainbow_fan',name:'Радужный Веер',evolution:'Семицветная Буря',model:'weapon04',projectile:'gem',type:'fan',damage:12,cooldown:.78,speed:20,count:5,spread:.18,pierce:1,range:24,scale:.18,passive:'projectiles'},
  {id:'flower_burst',name:'Цветочная Карусель',evolution:'Цветочная Корона',model:'weapon05',projectile:'flower',type:'fan',damage:13,cooldown:.74,speed:17,count:4,spread:.24,slow:.16,range:22,scale:.28,passive:'area'},
  {id:'amber_comet',name:'Янтарная Комета',evolution:'Янтарная Сверхновая',model:'weapon06',projectile:'gem',type:'shot',damage:24,cooldown:.82,speed:25,count:1,pierce:3,range:31,scale:.28,passive:'power'},
  {id:'blast_bot',name:'Взрывной Бот',evolution:'Механический Карнавал',model:'weapon07',projectile:'mini_robot',type:'homing',damage:24,cooldown:1.08,speed:12,count:1,homing:6,splash:2.5,range:30,scale:.32,passive:'cooldown'},
  {id:'pancake',name:'Возвратный Блин',evolution:'Бесконечный Блин',model:'weapon08',projectile:'food_pancake',type:'boomerang',damage:18,cooldown:1.02,speed:18,count:1,pierce:5,range:18,scale:.34,passive:'duration'},
  {id:'fox_bow',name:'Лисий Лук',evolution:'Ливень Лисьих Стрел',model:'weapon09',projectile:'arrow',type:'shot',damage:20,cooldown:.68,speed:29,count:1,pierce:1,crit:.12,range:34,scale:.30,passive:'crit'},
  {id:'cloud_orb',name:'Облачный Спутник',evolution:'Грозовой Спутник',model:'weapon10',projectile:'cloud',type:'homing',damage:16,cooldown:.64,speed:15,count:2,homing:8,pierce:1,range:28,scale:.30,passive:'speed'},
  {id:'watermelon',name:'Взрывной Арбуз',evolution:'Гигантский Арбуз',model:'weapon11',projectile:'food_watermelon',type:'bomb',damage:34,cooldown:1.34,speed:12,count:1,splash:4.2,knockback:2.4,range:25,scale:.38,passive:'area'},
  {id:'music_wave',name:'Буря Нот',evolution:'Громовой Концерт',model:'weapon12',projectile:'star',type:'radial',damage:12,cooldown:1.12,speed:17,count:8,pierce:2,range:20,scale:.18,passive:'projectiles'},
  {id:'smart_bee',name:'Умная Пчела',evolution:'Королевский Рой',model:'weapon13',projectile:'bee',type:'homing',damage:14,cooldown:.48,speed:18,count:2,homing:10,range:27,scale:.26,passive:'cooldown'},
  {id:'snowball',name:'Снежная Дорожка',evolution:'Полярная Буря',model:'weapon14',projectile:'snow',type:'fan',damage:14,cooldown:.76,speed:16,count:3,spread:.16,slow:.30,range:23,scale:.26,passive:'duration'},
  {id:'pumpkin',name:'Тыквенная Засада',evolution:'Тыквенный Лабиринт',model:'weapon15',projectile:'food_pumpkin',type:'mine',damage:28,cooldown:1.16,speed:12,count:1,splash:3.1,range:20,scale:.34,passive:'area'}
].map(w=>({...w,levels:levelText(w.name,w.evolution)}));

export const PASSIVES = [
  {id:'power',name:'Сила',icon:'✦',max:5,desc:'+10% ко всему урону.',apply:p=>p.damageMul*=1.10},
  {id:'cooldown',name:'Темп атак',icon:'⌁',max:5,desc:'Оружие стреляет на 8% чаще.',apply:p=>p.cooldownMul*=.92},
  {id:'speed',name:'Скорость',icon:'➤',max:5,desc:'+8% к скорости движения.',apply:p=>p.speedMul*=1.08},
  {id:'health',name:'Здоровье',icon:'♥',max:5,desc:'+18 к максимуму здоровья.',apply:p=>{p.maxHp+=18;p.hp+=18;}},
  {id:'armor',name:'Броня',icon:'⬙',max:5,desc:'Входящий урон -7%.',apply:p=>p.damageTaken*=.93},
  {id:'area',name:'Размер атак',icon:'◎',max:5,desc:'+10% к размеру взрывов и снарядов.',apply:p=>p.areaMul*=1.10},
  {id:'projectiles',name:'Доп. снаряды',icon:'✣',max:3,desc:'+1 снаряд у многозарядного оружия.',apply:p=>p.extraProjectiles++},
  {id:'crit',name:'Критический шанс',icon:'◆',max:5,desc:'+6% шанс критического попадания.',apply:p=>p.crit+=.06},
  {id:'pickup',name:'Магнит',icon:'◉',max:5,desc:'+18% к радиусу подбора.',apply:p=>p.pickup*=1.18},
  {id:'regen',name:'Регенерация',icon:'✚',max:5,desc:'+0.25 здоровья в секунду.',apply:p=>p.regen+=.25},
  {id:'pierce',name:'Пробивание',icon:'➹',max:3,desc:'+1 пробивание для прямых снарядов.',apply:p=>p.extraPierce++},
  {id:'duration',name:'Длительность',icon:'◴',max:5,desc:'+12% к времени жизни атак.',apply:p=>p.duration*=1.12}
];

const maps = [
  ['sunny_meadow','Солнечный луг','Тёплая зелёная поляна','forest','#72b35a','#d7f3b6','#92c972','tree'],
  ['magic_forest','Волшебный лес','Сказочный лес со светящимися кристаллами','forest','#315b4d','#6fc9a4','#4b8468','tree'],
  ['candy_park','Карамельный парк','Яркий парк с десертными декорациями','park','#ef9dc4','#fff0bd','#f5b4cf','food_cookie'],
  ['pumpkin_village','Тыквенная деревня','Тёплая осенняя деревушка','village','#ad6942','#ffd394','#c8874b','deadTree'],
  ['snow_festival','Снежный праздник','Светлая зимняя долина','snow','#dce8ef','#f8fbff','#b8d5e6','rock'],
  ['toy_castle','Игрушечный замок','Цветной замковый двор','castle','#7da0c6','#f5e4aa','#9fbde0','column'],
  ['bubble_beach','Пузырьковый пляж','Песчаная бухта с морской границей','beach','#d9bd79','#c7f5ff','#e9d590','rock'],
  ['cheese_moon','Сырная луна','Кратеры, камни и звёздный горизонт','moon','#cdbf78','#1d2442','#b5aa6c','rock'],
  ['clockwork_garden','Часовой сад','Сад механизмов и колонн','clock','#7f9279','#d7c27c','#9aa58a','column'],
  ['rainbow_canyon','Радужный каньон','Яркие скалы и узкие проходы','canyon','#b16d55','#f5bcd7','#c9856d','rock'],
  ['jelly_caves','Желейные пещеры','Светящиеся цветные пещеры','cave','#795c91','#dc9cff','#8c6aa5','gem'],
  ['cloud_city','Облачный город','Светлая небесная площадь','sky','#a9d9e9','#eafaff','#c5e8f3','arch'],
  ['cookie_desert','Печеньковая пустыня','Тёплая пустыня и сладкие руины','desert','#c79662','#f6cf8f','#d9aa74','food_cookie'],
  ['firefly_swamp','Болото светлячков','Влажное болото с огнями','swamp','#496d53','#9fcf83','#557b5d','tree'],
  ['music_fair','Музыкальная ярмарка','Праздничная площадь и сцена','fair','#bd7d72','#ffe1a2','#d29588','arch'],
  ['crystal_hills','Хрустальные холмы','Кристаллические гряды','crystal','#6e8fa9','#cdeaff','#809fb6','gem'],
  ['starry_rooftops','Звёздные крыши','Ночная городская арена','rooftop','#364962','#111a31','#4a617e','column'],
  ['festival_finale','Финал фестиваля','Самая яркая праздничная карта','festival','#d2788d','#ffe6a9','#e294a6','arch']
];

const layouts = {
  forest:[['tree',-24,-18,5],['tree',-12,-25,4.5],['tree',4,-27,5.2],['tree',18,-23,4.7],['tree',25,-9,5.4],['tree',23,11,4.8],['tree',12,24,5.1],['tree',-6,26,4.6],['tree',-22,18,5.3],['rock',8,8,1.8],['bush',-9,7,1.3]],
  park:[['food_cookie',-20,-18,2.2],['food_cookie',18,-16,2.5],['food_donut',-16,17,2.4],['food_donut',17,19,2.0],['arch',0,-25,4.0],['column',-8,0,3.0],['column',8,0,3.0],['bush',-18,0,1.2],['bush',18,0,1.2]],
  village:[['deadTree',-23,-17,4.5],['deadTree',21,-18,4.8],['arch',0,-24,4.0],['column',-15,7,3.2],['column',15,7,3.2],['food_pumpkin',-9,17,1.5],['food_pumpkin',10,18,1.8],['rock',0,8,1.8]],
  snow:[['tree',-22,-20,5.0],['tree',22,-19,5.2],['tree',-18,18,4.6],['tree',20,19,4.8],['rock',-8,-4,2.0],['rock',9,5,1.8],['gem',0,20,1.5]],
  castle:[['arch',0,-26,4.8],['arch',0,26,4.8],['column',-24,-15,4],['column',24,-15,4],['column',-24,15,4],['column',24,15,4],['chest',-10,0,1.5],['chest',10,0,1.5]],
  beach:[['rock',-25,-16,2],['rock',25,-16,2],['tree',-22,17,5],['tree',22,18,5],['food_watermelon',-10,10,1.4],['food_watermelon',12,8,1.5]],
  moon:[['rock',-22,-18,2.5],['rock',17,-20,2.2],['rock',24,12,2.4],['rock',-20,16,2.0],['gem',0,-19,2],['gem',10,15,1.8],['gem',-10,10,1.5]],
  clock:[['column',-24,-18,4],['column',24,-18,4],['column',-24,18,4],['column',24,18,4],['arch',0,-25,4.5],['arch',0,25,4.5],['gem',0,0,1.5]],
  canyon:[['rock',-24,-21,3.0],['rock',-15,-16,2.5],['rock',15,-16,2.8],['rock',24,-21,3.0],['rock',-20,17,2.8],['rock',20,17,2.8],['deadTree',0,22,4.2]],
  cave:[['rock',-22,-19,3],['rock',22,-19,3],['rock',-24,15,2.5],['rock',24,15,2.5],['gem',-12,-6,2],['gem',12,6,2],['gem',0,21,2.3]],
  sky:[['arch',-20,-18,4],['arch',20,-18,4],['arch',-20,18,4],['arch',20,18,4],['column',0,-22,4],['column',0,22,4],['gem',0,0,1.4]],
  desert:[['deadTree',-22,-18,4],['deadTree',22,18,4],['rock',18,-16,2.4],['rock',-18,15,2.2],['food_cookie',0,-20,2.2],['food_cookie',0,20,2.5]],
  swamp:[['tree',-23,-18,5],['tree',21,-19,4.7],['tree',-21,18,4.8],['tree',22,16,5],['torch',-8,0,2],['torch',8,0,2],['bush',0,19,1.2]],
  fair:[['arch',0,-25,5],['column',-20,-14,4],['column',20,-14,4],['column',-20,14,4],['column',20,14,4],['food_donut',-8,12,1.8],['food_cookie',9,12,1.8],['torch',0,20,2]],
  crystal:[['gem',-22,-18,2.5],['gem',22,-18,2.2],['gem',-22,18,2.0],['gem',22,18,2.7],['rock',0,-20,2],['rock',0,20,2],['gem',0,0,1.7]],
  rooftop:[['column',-24,-18,4],['column',24,-18,4],['column',-24,18,4],['column',24,18,4],['arch',0,-25,4],['arch',0,25,4],['torch',-12,0,2],['torch',12,0,2]],
  festival:[['arch',0,-27,5],['arch',0,27,5],['column',-24,-14,4],['column',24,-14,4],['column',-24,14,4],['column',24,14,4],['food_donut',-10,16,2],['food_cookie',10,16,2],['gem',0,0,1.8],['torch',0,-18,2]]
};

export const MAPS = maps.map((m,i)=>({
  id:m[0],name:m[1],desc:m[2],layout:m[3],ground:m[4],sky:m[5],accent:m[6],barrier:m[7],
  width:112+(i%3)*8,height:92+(i%4)*7,unlockWins:i===0?0:i,enemyTier:i,bossIndex:i,
  landmarks:layouts[m[3]]||layouts.forest,
  hazard: i===6?{type:'water',axis:'z',at:38,width:8}:i===9?{type:'lava',axis:'x',at:45,width:7}:i===13?{type:'water',axis:'x',at:-44,width:8}:i===17?{type:'lava',axis:'z',at:-40,width:7}:null
}));

const behaviors=['chase','zigzag','dash','ranged','split','charger','sniper','swarm','shield','healer','bomber','orbit'];
export const ENEMIES = Array.from({length:24},(_,i)=>({
  id:`enemy_${String(i+1).padStart(2,'0')}`,name:['Прыгучий','Шустрый','Липкий','Шумный','Заводной','Сияющий'][i%6]+' '+['Слайм','Гриб','Жук','Кекс'][Math.floor(i/6)],
  asset:`monster${String((i%12)+1).padStart(2,'0')}`,behavior:behaviors[i%behaviors.length],hp:42+i*7,speed:2.0+(i%6)*.32,damage:7+Math.floor(i/4),xp:1+Math.floor(i/8),scale:.88+(i%4)*.08,
  color:[0xffffff,0xffe2a8,0xb9f4d0,0xcfc1ff][i%4]
}));

export const BOSSES = [
  'Гигантский Пончик','Старый Пень-Шутник','Королева Карамели','Тыквенный Мэр','Снежный Дедуля','Король Игрушек','Капитан Пузырь','Сырная Луна','Великий Будильник','Радужный Дракон','Император Желе','Грозовая Туча','Печеньковый Сфинкс','Хозяин Светлячков','Маэстро Гром','Кристальный Мамонт','Звёздный Енот-Мех','Король Фестиваля'
].map((name,i)=>({id:`boss_${i+1}`,name,asset:`monster${String((i%12)+1).padStart(2,'0')}`,pattern:['charge','rings','summon','dash','spiral','meteors'][i%6],hp:1800+i*260,damage:18+i,speed:1.65+(i%4)*.18,scale:2.2+(i%3)*.18}));

export const MODES = [
  {id:'quick',name:'Быстрый забег',seconds:180,difficulty:.88,reward:1,unlockWins:0},
  {id:'classic',name:'Классический забег',seconds:900,difficulty:1,reward:2.2,unlockWins:0},
  {id:'marathon',name:'Марафон',seconds:1800,difficulty:1.16,reward:4.5,unlockWins:3},
  {id:'endless',name:'Бесконечный режим',seconds:999999,difficulty:1.30,reward:1.35,unlockWins:8}
];

export const META_UPGRADES = [
  {id:'power',name:'Сила',desc:'+4% ко всему урону',value:.04,cost:180,max:10},
  {id:'health',name:'Здоровье',desc:'+10 здоровья',value:10,cost:160,max:10},
  {id:'speed',name:'Скорость',desc:'+2.5% скорости',value:.025,cost:150,max:10},
  {id:'armor',name:'Броня',desc:'-2.5% входящего урона',value:.025,cost:170,max:10},
  {id:'cooldown',name:'Темп атак',desc:'+2.5% скорости атаки',value:.025,cost:210,max:10},
  {id:'area',name:'Размер атак',desc:'+4% площади атак',value:.04,cost:200,max:10},
  {id:'xp',name:'Опыт',desc:'+5% опыта',value:.05,cost:190,max:10},
  {id:'pickup',name:'Радиус подбора',desc:'+8% радиуса',value:.08,cost:140,max:10},
  {id:'luck',name:'Удача',desc:'+4% шанс редких вариантов',value:.04,cost:220,max:10},
  {id:'crit',name:'Критический шанс',desc:'+2% крит. шанса',value:.02,cost:230,max:10},
  {id:'regen',name:'Регенерация',desc:'+0.08 HP/сек',value:.08,cost:200,max:10},
  {id:'reroll',name:'Перебросы',desc:'+1 бесплатный переброс',value:1,cost:500,max:5}
];

export const DAILY_QUESTS = [
  {id:'daily_kills_1',name:'Разминка',text:'Уничтожить 120 врагов',stat:'kills',target:120,reward:160},
  {id:'daily_kills_2',name:'Натиск дня',text:'Уничтожить 300 врагов',stat:'kills',target:300,reward:300},
  {id:'daily_kills_3',name:'Не остановить',text:'Уничтожить 600 врагов',stat:'kills',target:600,reward:520},
  {id:'daily_levels_1',name:'Быстрый рост',text:'Получить 6 уровней',stat:'levels',target:6,reward:140},
  {id:'daily_levels_2',name:'Сила растёт',text:'Получить 12 уровней',stat:'levels',target:12,reward:260},
  {id:'daily_levels_3',name:'Мастер усилений',text:'Получить 20 уровней',stat:'levels',target:20,reward:430},
  {id:'daily_survive_1',name:'Продержись',text:'Выжить 3 минуты суммарно',stat:'survive',target:180,reward:150},
  {id:'daily_survive_2',name:'Не сдавайся',text:'Выжить 8 минут суммарно',stat:'survive',target:480,reward:300},
  {id:'daily_survive_3',name:'Долгая смена',text:'Выжить 15 минут суммарно',stat:'survive',target:900,reward:500}
];

export const CAREER_QUESTS = [
  {id:'career_kills_1',name:'Первая сотня',text:'Уничтожить 500 врагов',stat:'kills',target:500,reward:450},
  {id:'career_kills_2',name:'Гроза толпы',text:'Уничтожить 1 500 врагов',stat:'kills',target:1500,reward:800},
  {id:'career_kills_3',name:'Легенда натиска',text:'Уничтожить 5 000 врагов',stat:'kills',target:5000,reward:1600},
  {id:'career_kills_4',name:'Никого не осталось',text:'Уничтожить 12 000 врагов',stat:'kills',target:12000,reward:3000},
  {id:'career_wins_1',name:'Первая победа',text:'Победить 1 раз',stat:'wins',target:1,reward:350},
  {id:'career_wins_2',name:'Победная серия',text:'Победить 5 раз',stat:'wins',target:5,reward:900},
  {id:'career_wins_3',name:'Герой фестиваля',text:'Победить 15 раз',stat:'wins',target:15,reward:1900},
  {id:'career_wins_4',name:'Чемпион миров',text:'Победить 30 раз',stat:'wins',target:30,reward:3500},
  {id:'career_bosses_1',name:'Первый гигант',text:'Победить 1 босса',stat:'bosses',target:1,reward:400},
  {id:'career_bosses_2',name:'Охотник на боссов',text:'Победить 5 боссов',stat:'bosses',target:5,reward:1000},
  {id:'career_bosses_3',name:'Укротитель гигантов',text:'Победить 12 боссов',stat:'bosses',target:12,reward:2100},
  {id:'career_bosses_4',name:'Боссов больше нет',text:'Победить 30 боссов',stat:'bosses',target:30,reward:3800},
  {id:'career_levels_1',name:'Учёба идёт',text:'Получить 25 уровней',stat:'levels',target:25,reward:500},
  {id:'career_levels_2',name:'Опытный герой',text:'Получить 100 уровней',stat:'levels',target:100,reward:1100},
  {id:'career_levels_3',name:'Ветеран усилений',text:'Получить 250 уровней',stat:'levels',target:250,reward:2200},
  {id:'career_levels_4',name:'Живая легенда',text:'Получить 600 уровней',stat:'levels',target:600,reward:4000},
  {id:'career_survive_1',name:'Пятнадцать минут',text:'Выжить 15 минут суммарно',stat:'survive',target:900,reward:500},
  {id:'career_survive_2',name:'Час в бою',text:'Выжить 60 минут суммарно',stat:'survive',target:3600,reward:1300},
  {id:'career_survive_3',name:'Несокрушимый',text:'Выжить 3 часа суммарно',stat:'survive',target:10800,reward:2600},
  {id:'career_survive_4',name:'Бесконечный натиск',text:'Выжить 7 часов суммарно',stat:'survive',target:25200,reward:4500},
  {id:'career_maps_1',name:'Первые путешествия',text:'Победить на 3 разных картах',stat:'mapWins',target:3,reward:700},
  {id:'career_maps_2',name:'Путешественник',text:'Победить на 8 разных картах',stat:'mapWins',target:8,reward:1500},
  {id:'career_maps_3',name:'Исследователь миров',text:'Победить на 12 разных картах',stat:'mapWins',target:12,reward:2600},
  {id:'career_maps_4',name:'Все 18 миров',text:'Победить на всех картах',stat:'mapWins',target:18,reward:5000}
];

export const ASSET_URLS = (()=>{
  const a={floor:'/assets/floor.glb',tree:'/assets/tree.glb',deadTree:'/assets/dead-tree.glb',bush:'/assets/bush.glb',rock:'/assets/rock.glb',gem:'/assets/gem.glb',arch:'/assets/arch.glb',column:'/assets/column.glb',chest:'/assets/chest.glb',torch:'/assets/torch.glb',fire:'/assets/fire.glb',flower:'/assets/flower.glb',bomb:'/assets/bomb.glb',arrow:'/assets/arrow.glb',star:'/assets/star.glb',cloud:'/assets/cloud.glb',snow:'/assets/snow.glb',bee:'/assets/bee.glb',mini_robot:'/assets/mini-robot.glb',food_donut:'/assets/food-donut.glb',food_watermelon:'/assets/food-watermelon.glb',food_pancake:'/assets/food-pancake.glb',food_pumpkin:'/assets/food-pumpkin.glb',food_cookie:'/assets/food-cookie.glb'};
  for(let i=1;i<=15;i++)a[`hero${String(i).padStart(2,'0')}`]=`/assets/hero-${String(i).padStart(2,'0')}.glb`;
  for(let i=1;i<=15;i++)a[`weapon${String(i).padStart(2,'0')}`]=`/assets/weapon-${String(i).padStart(2,'0')}.glb`;
  for(let i=1;i<=12;i++)a[`monster${String(i).padStart(2,'0')}`]=`/assets/monster-${String(i).padStart(2,'0')}.glb`;
  return a;
})();

export const metaCost=(def,rank)=>Math.floor(def.cost*Math.pow(1.38,rank));
export const weaponById=id=>WEAPONS.find(w=>w.id===id);
export const passiveById=id=>PASSIVES.find(p=>p.id===id);
