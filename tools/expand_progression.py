from pathlib import Path

cp=Path('src/content.js')
s=cp.read_text(encoding='utf-8')
start=s.index('export const DAILY_QUESTS = [')
end=s.index('export const ASSET_URLS',start)
expanded=r'''export const DAILY_QUESTS = [
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

'''
s=s[:start]+expanded+s[end:]
cp.write_text(s,encoding='utf-8')

mp=Path('src/main.js')
m=mp.read_text(encoding='utf-8')
old="for(const id of p.weapons){const r=p.weaponRanks[id]||1;if(r<8)c.push({kind:'weapon',id,rarity:r>=7?4:2});}"
new="for(const id of p.weapons){const r=p.weaponRanks[id]||1,w=weaponById(id),recipeReady=(p.passiveRanks[w.passive]||0)>0;if(r<7||(r===7&&recipeReady))c.push({kind:'weapon',id,rarity:r>=7?4:2});}"
if old in m:m=m.replace(old,new)
mp.write_text(m,encoding='utf-8')
print('expanded 9 daily quests, 24 career milestones, and recipe-gated evolutions')
