import type { Point } from "./state";

export type WorldId =
  | "middle-earth"
  | "wizarding-britain"
  | "marvel-mcu"
  | "the-matrix"
  | "star-wars"
  | "jurassic-park"
  | "game-of-thrones"
  | "the-witcher"
  | "naruto"
  | "cyberpunk-2077";

export type MapLandmark = { label: string; x: number; y: number };
export type World = {
  id: WorldId;
  title: string;
  subtitle: string;
  mapTitle: string;
  mapType: "land" | "city" | "island" | "galaxy";
  mapShape: string;
  mapScale: string;
  accent: string;
  landmarks: MapLandmark[];
};

export type Scenario = {
  id: string;
  universeId: WorldId;
  locationName: string;
  panoramaPath: string;
  point: Point;
};

export const WORLDS: World[] = [
  {
    id: "middle-earth", title: "Средиземье", subtitle: "Хоббит · Властелин колец", mapTitle: "Западные земли",
    mapType: "land", mapScale: "ШИР · ЭРИАДОР · ГОНДОР", accent: "#bba36d",
    mapShape: "M67 9 75 14 74 22 84 28 80 35 87 42 81 49 85 57 75 61 77 70 69 76 64 90 56 94 50 87 41 90 35 83 25 84 19 75 12 71 15 62 9 55 14 46 10 38 18 30 16 22 24 16 31 11 40 13 48 7 57 12",
    landmarks: [
      { label: "Шир", x: 0.27, y: 0.34 }, { label: "Бри", x: 0.38, y: 0.43 },
      { label: "Ривенделл", x: 0.48, y: 0.28 }, { label: "Мория", x: 0.54, y: 0.46 },
      { label: "Изенгард", x: 0.49, y: 0.63 }, { label: "Минас-Тирит", x: 0.68, y: 0.7 },
      { label: "Мордор", x: 0.78, y: 0.72 },
    ],
  },
  {
    id: "wizarding-britain", title: "Волшебная Британия", subtitle: "Гарри Поттер", mapTitle: "Британия волшебников",
    mapType: "land", mapScale: "ЛОНДОН · ХОГСМИД · ХОГВАРТС", accent: "#b8a0ce",
    mapShape: "M49 8 58 13 56 20 64 26 60 35 68 42 62 48 68 54 61 62 63 71 55 78 51 91 44 87 41 79 34 74 37 66 30 60 35 52 30 45 36 39 32 32 39 27 37 19 45 15",
    landmarks: [
      { label: "Хогвартс", x: 0.45, y: 0.2 }, { label: "Хогсмид", x: 0.48, y: 0.26 },
      { label: "Лондон", x: 0.63, y: 0.78 }, { label: "Диагон-аллея", x: 0.66, y: 0.8 },
      { label: "Годрикова впадина", x: 0.48, y: 0.61 },
    ],
  },
  {
    id: "marvel-mcu", title: "Marvel: Киновселенная", subtitle: "Мстители · Человек-паук", mapTitle: "Земля — ключевые точки",
    mapType: "land", mapScale: "НЬЮ-ЙОРК · ВАКАНДА · СОКОВИЯ", accent: "#d77868",
    mapShape: "M8 24 17 17 26 20 32 14 41 18 48 11 58 15 64 12 71 18 82 20 90 29 86 37 91 44 84 51 80 61 72 62 66 72 56 70 49 79 39 74 31 77 25 67 17 65 19 56 11 50 15 41 8 34",
    landmarks: [
      { label: "Нью-Йорк", x: 0.27, y: 0.37 }, { label: "Ваканда", x: 0.55, y: 0.55 },
      { label: "Соковия", x: 0.49, y: 0.3 }, { label: "Сан-Франциско", x: 0.18, y: 0.42 },
    ],
  },
  {
    id: "the-matrix", title: "Матрица", subtitle: "Город симуляции", mapTitle: "Мегаполис",
    mapType: "city", mapScale: "ЦЕНТР · ТРАССЫ · ЖИЛЫЕ КВАРТАЛЫ", accent: "#8bb58c",
    mapShape: "M12 16 87 16 87 84 12 84Z",
    landmarks: [
      { label: "Деловой квартал", x: 0.55, y: 0.32 }, { label: "Станция метро", x: 0.34, y: 0.61 },
      { label: "Склад", x: 0.72, y: 0.69 }, { label: "Крыши центра", x: 0.48, y: 0.47 },
    ],
  },
  {
    id: "star-wars", title: "Звёздные войны", subtitle: "Галактика далеко-далеко", mapTitle: "Карта галактики",
    mapType: "galaxy", mapScale: "ВНЕШНЕЕ КОЛЬЦО · ЯДРО · ВНУТРЕННИЕ МИРЫ", accent: "#e1c77b",
    mapShape: "M14 26Q49 8 87 27M12 42Q49 22 88 43M12 60Q49 80 88 59M25 14Q40 47 26 84M48 10Q59 49 49 90M70 15Q61 47 76 84",
    landmarks: [
      { label: "Татуин", x: 0.19, y: 0.68 }, { label: "Корусант", x: 0.56, y: 0.4 },
      { label: "Хот", x: 0.75, y: 0.19 }, { label: "Эндор", x: 0.7, y: 0.72 },
      { label: "Набу", x: 0.47, y: 0.59 },
    ],
  },
  {
    id: "jurassic-park", title: "Парк юрского периода", subtitle: "Остров Нублар", mapTitle: "Исла-Нублар",
    mapType: "island", mapScale: "ПОРТ · ПАРК · ВОЛЬЕРЫ", accent: "#a5b974",
    mapShape: "M49 8 59 16 63 26 72 30 67 40 75 49 69 57 71 68 61 74 57 86 47 91 42 83 33 81 28 73 31 63 23 56 30 47 25 39 34 31 35 20 43 17",
    landmarks: [
      { label: "Посетительский центр", x: 0.49, y: 0.5 }, { label: "Вольер рапторов", x: 0.63, y: 0.34 },
      { label: "Пирс", x: 0.38, y: 0.78 }, { label: "Восточная равнина", x: 0.57, y: 0.68 },
    ],
  },
  {
    id: "game-of-thrones", title: "Игра престолов", subtitle: "Песнь льда и пламени", mapTitle: "Вестерос и Эссос",
    mapType: "land", mapScale: "СЕВЕР · РЕЧНЫЕ ЗЕМЛИ · ЮГ", accent: "#b69c81",
    mapShape: "M28 7 41 13 46 22 44 31 52 38 48 47 54 55 47 65 50 75 42 86 34 91 30 82 25 75 27 65 21 58 25 48 20 38 25 30 21 19",
    landmarks: [
      { label: "Винтерфелл", x: 0.37, y: 0.24 }, { label: "Королевская Гавань", x: 0.45, y: 0.63 },
      { label: "Орлиное Гнездо", x: 0.56, y: 0.48 }, { label: "Драконий Камень", x: 0.51, y: 0.72 },
    ],
  },
  {
    id: "the-witcher", title: "Ведьмак", subtitle: "Северные королевства", mapTitle: "Северные королевства",
    mapType: "land", mapScale: "КАЭР МОРХЕН · РЕДАНИЯ · ТЕМЕРИЯ", accent: "#c5a66e",
    mapShape: "M50 8 61 14 58 23 69 30 64 38 74 45 67 53 70 62 61 67 60 77 52 82 46 93 39 83 31 78 34 67 26 61 31 50 24 43 33 34 28 25 39 19 41 11",
    landmarks: [
      { label: "Каэр Морхен", x: 0.49, y: 0.22 }, { label: "Оксенфурт", x: 0.62, y: 0.51 },
      { label: "Новиград", x: 0.53, y: 0.58 }, { label: "Вызима", x: 0.38, y: 0.67 },
    ],
  },
  {
    id: "naruto", title: "Наруто", subtitle: "Мир шиноби", mapTitle: "Страны шиноби",
    mapType: "land", mapScale: "СТРАНА ОГНЯ · ВОДЫ · ВЕТРА", accent: "#cf9973",
    mapShape: "M9 29 19 20 31 23 38 13 50 18 57 10 69 17 82 16 91 28 85 38 89 48 80 54 81 65 72 72 65 86 53 81 44 89 34 78 25 78 18 67 12 59 17 48 8 40",
    landmarks: [
      { label: "Коноха", x: 0.49, y: 0.49 }, { label: "Суна", x: 0.25, y: 0.59 },
      { label: "Кири", x: 0.79, y: 0.55 }, { label: "Долина Завершения", x: 0.54, y: 0.7 },
    ],
  },
  {
    id: "cyberpunk-2077", title: "Cyberpunk 2077", subtitle: "Найт-Сити", mapTitle: "Найт-Сити",
    mapType: "city", mapScale: "УОТСОН · ЦЕНТР · ПАСИФИКА", accent: "#e08fce",
    mapShape: "M13 19 83 12 90 26 85 42 91 56 78 66 81 82 63 87 49 81 34 88 20 76 11 60 17 43 9 31Z",
    landmarks: [
      { label: "Корпоративный центр", x: 0.51, y: 0.42 }, { label: "Уотсон", x: 0.31, y: 0.29 },
      { label: "Хейвуд", x: 0.43, y: 0.62 }, { label: "Пасифика", x: 0.24, y: 0.76 },
      { label: "Бэдлендс", x: 0.8, y: 0.5 },
    ],
  },
];

export const SCENARIOS: Scenario[] = [
  {
    id: "hobbiton",
    universeId: "middle-earth",
    locationName: "Хоббитон, Шир",
    panoramaPath: "/panoramas/middle-earth-hobbiton.webp",
    point: { x: 0.27, y: 0.34 },
  },
  {
    id: "great-hall", universeId: "wizarding-britain", locationName: "Большой зал Хогвартса",
    panoramaPath: "/panoramas/wizarding-britain-hogwarts-great-hall.webp", point: { x: 0.45, y: 0.2 },
  },
  {
    id: "sanctum", universeId: "marvel-mcu", locationName: "Санктум в Гринвич-Виллидж",
    panoramaPath: "/panoramas/marvel-mcu-sanctum.webp", point: { x: 0.35, y: 0.45 },
  },
  {
    id: "simulation-street", universeId: "the-matrix", locationName: "Перекрёсток в симуляции",
    panoramaPath: "/panoramas/the-matrix-city-intersection.webp", point: { x: 0.53, y: 0.47 },
  },
  {
    id: "mos-eisley", universeId: "star-wars", locationName: "Мос-Эйсли, Татуин",
    panoramaPath: "/panoramas/star-wars-mos-eisley.webp", point: { x: 0.19, y: 0.68 },
  },
  {
    id: "nublar-visitor-center", universeId: "jurassic-park", locationName: "Центр посетителей Исла-Нублар",
    panoramaPath: "/panoramas/jurassic-park-nublar-visitor-center.webp", point: { x: 0.49, y: 0.5 },
  },
  {
    id: "red-keep-throne-room", universeId: "game-of-thrones", locationName: "Тронный зал Красного замка",
    panoramaPath: "/panoramas/game-of-thrones-red-keep.webp", point: { x: 0.45, y: 0.63 },
  },
  {
    id: "kaer-morhen", universeId: "the-witcher", locationName: "Двор Каэр Морхена",
    panoramaPath: "/panoramas/the-witcher-kaer-morhen.webp", point: { x: 0.49, y: 0.22 },
  },
  {
    id: "konoha", universeId: "naruto", locationName: "Коноха",
    panoramaPath: "/panoramas/naruto-konoha.webp", point: { x: 0.49, y: 0.49 },
  },
  {
    id: "night-city", universeId: "cyberpunk-2077", locationName: "Найт-Сити, Уотсон",
    panoramaPath: "/panoramas/cyberpunk-2077-night-city.webp", point: { x: 0.51, y: 0.42 },
  },
];

export const WORLD_BY_ID = Object.fromEntries(WORLDS.map((world) => [world.id, world])) as Record<WorldId, World>;
