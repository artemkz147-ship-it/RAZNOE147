export const WORLD_THEMES = [
  {
    id: 'bamboo', name: 'Лес Безмолвия', subtitle: 'Бамбуковые тропы под кровавой луной',
    sky: 0x11151d, haze: 0x253346, moon: 0xffd9c4, accent: 0xd9485f, far: 0x17202c, mid: 0x101821,
    weather: 'leaves', enemyPool: ['swordsman','archer','scout'], boss: 'oni'
  },
  {
    id: 'cliffs', name: 'Горы Ворона', subtitle: 'Храмы над пропастью',
    sky: 0x0e1622, haze: 0x334761, moon: 0xd9efff, accent: 0x5fb5d8, far: 0x19283a, mid: 0x101c2a,
    weather: 'rain', enemyPool: ['swordsman','archer','brute'], boss: 'shogun'
  },
  {
    id: 'fire', name: 'Пылающий Монастырь', subtitle: 'Пепел помнит имена павших',
    sky: 0x1b0d0b, haze: 0x5a2117, moon: 0xffb36a, accent: 0xff6a36, far: 0x31130f, mid: 0x1c0d0b,
    weather: 'embers', enemyPool: ['brute','archer','mage'], boss: 'monk'
  },
  {
    id: 'swamp', name: 'Топи Духов', subtitle: 'Руины, где вода шепчет',
    sky: 0x071716, haze: 0x264d42, moon: 0xb7efd6, accent: 0x62d8a5, far: 0x102a26, mid: 0x0b1d1a,
    weather: 'mist', enemyPool: ['scout','mage','assassin'], boss: 'kunoichi'
  },
  {
    id: 'citadel', name: 'Цитадель Тени', subtitle: 'Последняя дорога ронина',
    sky: 0x090711, haze: 0x372453, moon: 0xe5c6ff, accent: 0xa565e8, far: 0x1d132d, mid: 0x120c1c,
    weather: 'ash', enemyPool: ['assassin','mage','brute','archer'], boss: 'shadowLord'
  }
];

export const BOSSES = {
  oni: { name:'Они Железного Леса', hp:18, speed:105, damage:2, tint:0xd84b46, behavior:'charge' },
  shogun: { name:'Сёгун Ворона', hp:24, speed:125, damage:2, tint:0x5f9fd8, behavior:'blade' },
  monk: { name:'Монах Пепла', hp:28, speed:95, damage:2, tint:0xf06b32, behavior:'fire' },
  kunoichi: { name:'Куноити Топей', hp:26, speed:155, damage:2, tint:0x58c69a, behavior:'teleport' },
  shadowLord: { name:'Владыка Тени', hp:36, speed:140, damage:3, tint:0x9a5ad9, behavior:'shadow' }
};

const PATTERNS = [
  ['flat','gap','stairsUp','flat','pit','roof','flat','gap','stairsDown','flat'],
  ['flat','tower','gap','zigzag','pit','flat','roof','gap','tower','flat'],
  ['stairsUp','flat','gap','roof','gap','stairsDown','pit','flat','zigzag','flat'],
  ['flat','pit','moving','gap','tower','flat','moving','roof','gap','flat'],
  ['tower','gap','stairsUp','pit','flat','zigzag','roof','moving','gap','flat'],
  ['flat','roof','pit','gap','moving','tower','zigzag','pit','stairsDown','flat']
];

const TITLES = [
  ['Тихая тропа','Лезвия в бамбуке','Красная луна','Мост без имени','Дыхание леса','Сердце Они'],
  ['Первый уступ','Тропа ворона','Колокола ветра','Через облака','Крыши монастыря','Зал Сёгуна'],
  ['Врата пепла','Горящие балки','Храм без молитв','Колодец огня','Последний факел','Печь Монаха'],
  ['Чёрная вода','Корни под луной','Затонувший двор','Шёпот фонарей','Тропа духов','Зеркало Куноити'],
  ['Нижние врата','Башни тени','Сад клинков','Тёмный архив','Тронный путь','Последняя тень']
];

export const LEVELS = Array.from({length:30}, (_,index)=>{
  const world=Math.floor(index/6);
  const stage=index%6;
  const boss=stage===5;
  const difficulty=1+world*.28+stage*.11;
  return {
    id:index+1,
    world,
    stage,
    title:TITLES[world][stage],
    boss,
    bossType: boss ? WORLD_THEMES[world].boss : null,
    width: boss ? 6500+world*320 : 5200+stage*330+world*240,
    patterns:PATTERNS[(stage+world*2)%PATTERNS.length],
    difficulty,
    enemyCount: boss ? 10+world*2 : 8+stage*2+world,
    coinTarget: boss ? 28+world*3 : 20+stage*3+world*2,
    moving: stage>=2,
    hazards: 2+stage+world,
    secretXRatio: .32 + ((stage*17+world*11)%41)/100,
  };
});

export const STORY = [
  'Рэйдзин вернулся из изгнания и нашёл родную деревню пустой. На воротах остался знак Пяти Теней.',
  'След ведёт в горы. Вороны кружат над крепостью человека, который когда-то называл Рэйдзина братом.',
  'Огонь уничтожает следы, но не вину. В монастыре хранится третий осколок печати.',
  'Духи болот знают имя предателя. Чтобы услышать его, Рэйдзину придётся пройти через собственные воспоминания.',
  'Пять осколков собраны. За стенами Цитадели ждёт тот, кто превратил клан в оружие.'
];

export const UPGRADES = {
  vitality:{name:'Стойкость',desc:'+1 максимум здоровья',max:5,base:90},
  blade:{name:'Клинок',desc:'+1 урон мечом',max:5,base:110},
  shadow:{name:'Шаг тени',desc:'быстрее рывок и восстановление',max:5,base:100},
  focus:{name:'Фокус',desc:'+20 энергии и сильнее сюрикены',max:5,base:95}
};

export function upgradeCost(key, level){
  const u=UPGRADES[key];
  return Math.round(u.base*Math.pow(1.62,level));
}
