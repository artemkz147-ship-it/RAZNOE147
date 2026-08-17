export const I18N = {
  ru: {
    loadingTitle:'ПРОБУЖДЕНИЕ ДОЛИНЫ', subtitle:'Прорвись через туман, усиливай рыцаря и уничтожь Владыку Долины.',
    best:'Лучшее время', bestKills:'Рекорд убийств', souls:'Осколки душ', start:'НАЧАТЬ ЗАБЕГ', altar:'АЛТАРЬ СИЛЫ', settings:'НАСТРОЙКИ', controls:'УПРАВЛЕНИЕ', review:'ОЦЕНИТЬ ИГРУ',
    menuNote:'WASD / стрелки · Space — рывок · на телефоне — виртуальный джойстик', time:'Время', level:'Ур.', kills:'Враги', pause:'ПАУЗА', resume:'ПРОДОЛЖИТЬ', quit:'В ГЛАВНОЕ МЕНЮ',
    choosePower:'ВЫБЕРИ СИЛУ', levelUp:'НОВЫЙ УРОВЕНЬ', reroll:'Обновить выбор', fallen:'ГЕРОЙ ПАЛ', victory:'ВЛАДЫКА ПОВЕРЖЕН', revive:'ВОЗРОДИТЬСЯ', again:'НОВЫЙ ЗАБЕГ', toMenu:'ГЛАВНОЕ МЕНЮ', doubleSouls:'УДВОИТЬ ДУШИ',
    controlsText:'Двигайся WASD или стрелками. Рыцарь автоматически атакует ближайших врагов. Space — быстрый рывок с короткой неуязвимостью. На телефоне используй джойстик слева и кнопку рывка справа.',
    dash:'РЫВОК', revived:'ВОЗРОЖДЕНИЕ', noAd:'РЕКЛАМА СЕЙЧАС НЕДОСТУПНА', boss:'ВЛАДЫКА ТУМАНА', elite:'ЭЛИТНЫЙ ВРАГ', loadError:'Не удалось загрузить игровые ассеты.',
    bossIncoming:'ВЛАДЫКА ТУМАНА ВХОДИТ В ДОЛИНУ', bossEnrage:'ТУМАН СГУЩАЕТСЯ', reward:'Награда', soulsEarned:'Души за забег',
    altarTitle:'АЛТАРЬ СИЛЫ', altarHint:'Постоянные улучшения сохраняются между забегами.', buy:'УЛУЧШИТЬ', max:'МАКС.', needSouls:'Не хватает осколков душ', upgraded:'СИЛА УЛУЧШЕНА',
    music:'Музыка', sfx:'Эффекты', vibration:'Вибрация', quality:'Качество', qualityAuto:'Авто', qualityHigh:'Высокое', qualityLow:'Экономное', back:'НАЗАД',
    firstMove:'Двигайся — рыцарь атакует сам', firstDash:'Рывок помогает пройти сквозь опасность', firstLevel:'Собирай кристаллы и выбирай усиления',
    rank:'Ранг', runComplete:'ЗАБЕГ ЗАВЕРШЁН', runEnded:'ЗАБЕГ ОКОНЧЕН', rewardedChoice:'Выбери: возрождение или двойная награда', reviewThanks:'Спасибо за оценку!',
    fullscreen:'НА ВЕСЬ ЭКРАН'
  },
  en: {
    loadingTitle:'THE VALE AWAKENS', subtitle:'Cut through the mist, empower the knight and destroy the Lord of the Vale.',
    best:'Best time', bestKills:'Kill record', souls:'Soul shards', start:'START RUN', altar:'ALTAR OF POWER', settings:'SETTINGS', controls:'CONTROLS', review:'RATE GAME',
    menuNote:'WASD / arrows · Space dash · virtual joystick on mobile', time:'Time', level:'Lvl', kills:'Kills', pause:'PAUSE', resume:'RESUME', quit:'MAIN MENU',
    choosePower:'CHOOSE POWER', levelUp:'LEVEL UP', reroll:'Reroll choices', fallen:'THE HERO FELL', victory:'THE LORD IS BROKEN', revive:'REVIVE', again:'NEW RUN', toMenu:'MAIN MENU', doubleSouls:'DOUBLE SOULS',
    controlsText:'Move with WASD or arrow keys. The knight automatically attacks nearby enemies. Space performs a fast dash with brief invulnerability. On mobile use the left joystick and the dash button on the right.',
    dash:'DASH', revived:'REVIVED', noAd:'AD IS NOT AVAILABLE RIGHT NOW', boss:'LORD OF MIST', elite:'ELITE ENEMY', loadError:'Failed to load game assets.',
    bossIncoming:'THE LORD OF MIST ENTERS THE VALE', bossEnrage:'THE MIST THICKENS', reward:'Reward', soulsEarned:'Run souls',
    altarTitle:'ALTAR OF POWER', altarHint:'Permanent upgrades persist between runs.', buy:'UPGRADE', max:'MAX', needSouls:'Not enough soul shards', upgraded:'POWER UPGRADED',
    music:'Music', sfx:'Effects', vibration:'Vibration', quality:'Quality', qualityAuto:'Auto', qualityHigh:'High', qualityLow:'Battery saver', back:'BACK',
    firstMove:'Move — the knight attacks automatically', firstDash:'Dash through danger', firstLevel:'Collect crystals and choose upgrades',
    rank:'Rank', runComplete:'RUN COMPLETE', runEnded:'RUN ENDED', rewardedChoice:'Choose: revival or double reward', reviewThanks:'Thanks for rating!',
    fullscreen:'FULLSCREEN'
  }
};

export const UPGRADE_POOL = [
  {id:'damage',icon:'⚔',name:{ru:'Закалённая сталь',en:'Hardened Steel'},desc:{ru:'+24% к урону меча',en:'+24% sword damage'},max:8,apply:g=>g.player.damage*=1.24},
  {id:'speed',icon:'➤',name:{ru:'Шаг охотника',en:"Hunter's Step"},desc:{ru:'+11% к скорости движения',en:'+11% move speed'},max:6,apply:g=>g.player.speed*=1.11},
  {id:'haste',icon:'⌁',name:{ru:'Боевой ритм',en:'Battle Rhythm'},desc:{ru:'Атаки на 14% чаще',en:'Attack 14% faster'},max:7,apply:g=>g.player.attackDelay*=.86},
  {id:'vitality',icon:'♥',name:{ru:'Живучесть',en:'Vitality'},desc:{ru:'+25 к максимуму здоровья и лечение',en:'+25 max HP and heal'},max:6,apply:g=>{g.player.maxHp+=25;g.player.hp=Math.min(g.player.maxHp,g.player.hp+40);}},
  {id:'reach',icon:'◈',name:{ru:'Длинная дуга',en:'Long Arc'},desc:{ru:'+18% к дальности удара',en:'+18% melee reach'},max:5,apply:g=>g.player.attackRange*=1.18},
  {id:'cleave',icon:'✦',name:{ru:'Рассекающий удар',en:'Cleave'},desc:{ru:'+1 цель за одну атаку',en:'+1 target per attack'},max:4,apply:g=>g.player.cleave+=1},
  {id:'armor',icon:'⬙',name:{ru:'Костяная броня',en:'Bone Armor'},desc:{ru:'Получаемый урон -11%',en:'Take 11% less damage'},max:5,apply:g=>g.player.damageTaken*=.89},
  {id:'dash',icon:'↯',name:{ru:'Теневой рывок',en:'Shadow Dash'},desc:{ru:'Рывок перезаряжается на 17% быстрее',en:'Dash recharges 17% faster'},max:5,apply:g=>g.player.dashCooldown*=.83},
  {id:'crit',icon:'◆',name:{ru:'Глаз хищника',en:'Predator Eye'},desc:{ru:'+9% шанс двойного урона',en:'+9% double-damage chance'},max:6,apply:g=>g.player.crit=Math.min(.65,g.player.crit+.09)},
  {id:'magnet',icon:'◎',name:{ru:'Зов кристаллов',en:'Crystal Call'},desc:{ru:'+28% к радиусу притяжения опыта',en:'+28% pickup radius'},max:5,apply:g=>g.player.pickupRadius*=1.28},
  {id:'regen',icon:'✚',name:{ru:'Кровь долины',en:'Blood of the Vale'},desc:{ru:'Восстановление здоровья со временем',en:'Regenerate health over time'},max:5,apply:g=>g.player.regen+=.45},
  {id:'bolt',icon:'✧',name:{ru:'Осколок души',en:'Soul Shard'},desc:{ru:'Автоматический дальний выстрел кристаллом',en:'Automatic ranged crystal shot'},max:5,apply:g=>{g.player.boltRank++;g.player.boltDamage*=1.22;g.player.boltDelay=Math.max(.55,g.player.boltDelay*.88);}},
  {id:'orbit',icon:'⟲',name:{ru:'Клинки хранителя',en:'Guardian Blades'},desc:{ru:'Добавляет вращающийся вокруг героя меч',en:'Adds an orbiting sword'},max:4,apply:g=>{g.player.orbitRank++;g.syncOrbitBlades();}},
  {id:'fortune',icon:'◇',name:{ru:'Жадность тумана',en:'Mist Fortune'},desc:{ru:'+18% осколков душ за этот забег',en:'+18% soul shards this run'},max:5,apply:g=>g.player.soulGain*=1.18}
];

export const META_UPGRADES = [
  {id:'might',icon:'⚔',name:{ru:'Сила',en:'Might'},desc:{ru:'+5% стартового урона за ранг',en:'+5% starting damage per rank'},max:10,base:45,growth:1.42},
  {id:'vitality',icon:'♥',name:{ru:'Стойкость',en:'Vitality'},desc:{ru:'+6% стартового здоровья за ранг',en:'+6% starting HP per rank'},max:10,base:45,growth:1.42},
  {id:'agility',icon:'➤',name:{ru:'Ловкость',en:'Agility'},desc:{ru:'+3% скорости за ранг',en:'+3% speed per rank'},max:10,base:55,growth:1.44},
  {id:'ward',icon:'⬙',name:{ru:'Оберег',en:'Ward'},desc:{ru:'-2% входящего урона за ранг',en:'-2% incoming damage per rank'},max:10,base:65,growth:1.46},
  {id:'fortune',icon:'◇',name:{ru:'Удача',en:'Fortune'},desc:{ru:'+6% осколков душ за ранг',en:'+6% soul shards per rank'},max:10,base:50,growth:1.43}
];

export const ENEMY_DEFS = {
  skeleton:{asset:'skeleton',height:1.72,hp:58,speed:2.85,damage:8,xp:1,souls:1,contact:1.12},
  slime:{asset:'slime',height:1.18,hp:125,speed:1.7,damage:13,xp:2,souls:2,contact:1.18},
  bat:{asset:'bat',height:.82,hp:34,speed:4.2,damage:6,xp:1,souls:1,contact:.92,float:.75},
  dragon:{asset:'dragon',height:2.15,hp:185,speed:2.15,damage:12,xp:4,souls:4,contact:1.55,ranged:true},
  boss:{asset:'dragon',height:4.15,hp:1900,speed:2.0,damage:20,xp:45,souls:75,contact:2.0,ranged:true,boss:true}
};

export const ASSET_URLS = {
  hero:'/assets/hero.glb', sword:'/assets/sword.glb', skeleton:'/assets/skeleton.glb', slime:'/assets/slime.glb', bat:'/assets/bat.glb', dragon:'/assets/dragon.glb',
  tree:'/assets/tree.glb', deadTree:'/assets/dead-tree.glb', bush:'/assets/bush.glb', rock:'/assets/rock.glb', gem:'/assets/gem.glb',
  floor:'/assets/floor.glb', arch:'/assets/arch.glb', column:'/assets/column.glb', chest:'/assets/chest.glb', torch:'/assets/torch.glb', fire:'/assets/fire.glb'
};

export const metaCost = (def, rank) => Math.floor(def.base * Math.pow(def.growth, rank));
