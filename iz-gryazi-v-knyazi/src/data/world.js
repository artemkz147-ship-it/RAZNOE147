export const districts = [
  { id:'yard', name:'Район Пятиэтажек', short:'Пятиэтажки', subtitle:'Где каждый подъезд знает твои долги', tier:0, fare:0, required:{}, palette:['#71777a','#2e3438','#bb8056'], weather:'Грязный снег', places:['Подъезд № 7','Ларёк «24 часа»','Гаражный ряд'], jobs:['scrap','courier','cleaner'], npcs:['valera','tamara'] },
  { id:'market', name:'Вокзальный рынок', short:'Рынок', subtitle:'Здесь продают даже чужую уверенность', tier:1, fare:65, required:{ respect:8, story:2 }, palette:['#8a7460','#35383a','#d69353'], weather:'Слякоть', places:['Рынок','Шиномонтаж','Маршрутная стоянка'], jobs:['loader','driver','reseller'], npcs:['azamat','valera'] },
  { id:'industrial', name:'Промзона «Надежда»', short:'Промзона', subtitle:'Надежда здесь — название забора', tier:2, fare:120, required:{ respect:24, story:4 }, palette:['#696f72','#292e33','#dc7e4d'], weather:'Кислый дождь', places:['Склад № 4','Автосервис','Заводоуправление'], jobs:['warehouse','mechanic','contract'], npcs:['lida','azamat'] },
  { id:'center', name:'Старый центр', short:'Центр', subtitle:'Кофе дорогой. Совесть — по акции', tier:3, fare:220, required:{ respect:48, story:6 }, palette:['#777c82','#252c35','#daa66b'], weather:'Холодный туман', places:['Кафе «Сделка»','Мэрия','Салон связи'], jobs:['barista','sales','consultant'], npcs:['vera','lida'] },
  { id:'glass', name:'Стеклянный квартал', short:'Квартал', subtitle:'Вид на город включён в стоимость кредита', tier:4, fare:450, required:{ respect:82, story:8 }, palette:['#617780','#19232d','#e3bc88'], weather:'Дорогой дождь', places:['Бизнес-центр','Клуб «Высота»','Галерея'], jobs:['manager','pitch'], npcs:['artur','vera'] },
  { id:'heights', name:'Верхний берег', short:'Берег', subtitle:'Отсюда лужи похожи на архитектуру', tier:5, fare:900, required:{ respect:135, story:10 }, palette:['#697e8a','#18212b','#e8c596'], weather:'Золотой час', places:['Резиденция','Казино «Зеро»','Панорамная башня'], jobs:['board','summit'], npcs:['artur','minister'] }
];

export const jobs = [
  {id:'scrap',name:'Сдать металл',district:'yard',hours:3,energy:18,pay:310,skill:'grit',game:'timing',risk:0.02,description:'Магнит ищет железо, дед ищет справедливость.'},
  {id:'courier',name:'Развозить заказы',district:'yard',hours:4,energy:26,pay:520,skill:'grit',game:'route',risk:0.03,description:'Клиент пишет «я у подъезда». Это ложь.'},
  {id:'cleaner',name:'Убрать подъезд',district:'yard',hours:4,energy:29,pay:580,skill:'charm',game:'sort',risk:0.01,description:'Найди то, что было полом до ремонта.'},
  {id:'loader',name:'Грузить коробки',district:'market',hours:4,energy:32,pay:900,skill:'grit',game:'timing',risk:0.04,description:'Внутри хрупкое. Снаружи — ты.'},
  {id:'driver',name:'Подменить водителя',district:'market',hours:5,energy:27,pay:1250,skill:'grit',game:'route',risk:0.04,description:'Маршрут — это теория, пробка — практика.'},
  {id:'reseller',name:'Перепродать партию',district:'market',hours:3,energy:16,pay:1050,skill:'charm',game:'bargain',risk:0.02,description:'Новая этикетка — новая жизнь.'},
  {id:'warehouse',name:'Сортировать склад',district:'industrial',hours:5,energy:34,pay:1750,skill:'focus',game:'sort',risk:0.03,description:'Система учёта на бумаге в клетку.'},
  {id:'mechanic',name:'Починить движок',district:'industrial',hours:5,energy:30,pay:2200,skill:'focus',game:'timing',risk:0.05,description:'Шум пропал. Возможно, машина умерла.'},
  {id:'contract',name:'Сверить накладные',district:'industrial',hours:4,energy:24,pay:2500,skill:'focus',game:'audit',risk:0.01,description:'Если цифры сходятся, ищи ошибку.'},
  {id:'barista',name:'Выйти в смену',district:'center',hours:5,energy:26,pay:3400,skill:'charm',game:'sort',risk:0.01,description:'Стакан подороже, кофе тот же.'},
  {id:'sales',name:'Закрыть продажу',district:'center',hours:4,energy:23,pay:4600,skill:'charm',game:'bargain',risk:0.02,description:'Бонус выплачивается оптимизмом.'},
  {id:'consultant',name:'Проверить проект',district:'center',hours:5,energy:27,pay:5300,skill:'focus',game:'audit',risk:0.01,description:'Презентация уже согласована с реальностью.'},
  {id:'manager',name:'Управлять сменой',district:'glass',hours:5,energy:32,pay:8000,skill:'focus',game:'route',risk:0.02,description:'Решения твои. Последствия отдела.'},
  {id:'pitch',name:'Продать идею',district:'glass',hours:3,energy:25,pay:10500,skill:'charm',game:'bargain',risk:0.02,description:'Монетизируй воздух до конкурентов.'},
  {id:'board',name:'Заседание совета',district:'heights',hours:4,energy:29,pay:17000,skill:'charm',game:'audit',risk:0.01,description:'Говори «синергия», пока не дадут слово.'},
  {id:'summit',name:'Заключить партнёрство',district:'heights',hours:5,energy:35,pay:23500,skill:'charm',game:'bargain',risk:0.02,description:'Рукопожатие дороже договора.'},
  {id:'bottles',name:'Сдать стеклотару',district:'yard',hours:2,energy:12,pay:240,skill:'grit',game:'timing',risk:.01,description:'Каждая бутылка — чужой вечер и твой завтрак.'},
  {id:'dogwalk',name:'Выгулять чужих собак',district:'yard',hours:3,energy:21,pay:690,skill:'charm',game:'route',risk:.02,description:'Собаки слушаются лучше хозяев. Иногда.'},
  {id:'packing',name:'Собрать заказы',district:'market',hours:4,energy:25,pay:1100,skill:'focus',game:'sort',risk:.02,description:'Кому-то опять нужен чайник к восьми утра.'},
  {id:'stocktake',name:'Пересчитать остатки',district:'market',hours:5,energy:22,pay:1450,skill:'focus',game:'audit',risk:.01,description:'Остатки считают тебя в ответ.'},
  {id:'dispatcher',name:'Дежурить диспетчером',district:'industrial',hours:5,energy:24,pay:2900,skill:'focus',game:'route',risk:.02,description:'Телефон звонит даже когда выключен.'},
  {id:'safety',name:'Провести инструктаж',district:'industrial',hours:4,energy:22,pay:3100,skill:'charm',game:'sort',risk:.01,description:'Каска обязательна. Здравый смысл по желанию.'},
  {id:'showing',name:'Показать квартиру',district:'center',hours:4,energy:21,pay:5100,skill:'charm',game:'bargain',risk:.01,description:'Вид во двор называют видом на историю.'},
  {id:'eventsetup',name:'Собрать мероприятие',district:'center',hours:5,energy:33,pay:5900,skill:'grit',game:'timing',risk:.04,description:'Главный спикер опоздал, баннер — нет.'},
  {id:'prcrisis',name:'Погасить скандал',district:'glass',hours:4,energy:27,pay:12500,skill:'charm',game:'bargain',risk:.02,description:'Слово «недоразумение» оплачивается отдельно.'},
  {id:'datacheck',name:'Проверить аналитику',district:'glass',hours:5,energy:24,pay:14200,skill:'focus',game:'audit',risk:.01,description:'График растёт, если повернуть экран.'},
  {id:'gala',name:'Вести приём',district:'heights',hours:5,energy:31,pay:28000,skill:'charm',game:'sort',risk:.02,description:'Запомни имена тех, кто помнит твою фамилию.'},
  {id:'foundation',name:'Сверить фонд',district:'heights',hours:5,energy:27,pay:32000,skill:'focus',game:'audit',risk:.01,description:'Благотворительность начинается с бухгалтерии.'},
  {id:'snow',name:'Расчистить двор',district:'yard',hours:3,energy:24,pay:430,skill:'grit',game:'timing',risk:.03,description:'Снег убран. Машины заняли место быстрее.'},
  {id:'notice',name:'Разнести квитанции',district:'yard',hours:2,energy:14,pay:370,skill:'focus',game:'route',risk:.01,description:'Каждая дверь уверена, что платёж придумал ты.'},
  {id:'repairphone',name:'Починить телефоны',district:'market',hours:4,energy:20,pay:1650,skill:'focus',game:'sort',risk:.02,description:'Экран разбит, но надежда клиента целая.'},
  {id:'bake',name:'Ночная выпечка',district:'market',hours:5,energy:30,pay:1780,skill:'grit',game:'timing',risk:.03,description:'Самая свежая булка досталась начальнику.'},
  {id:'quality',name:'Проверить партию',district:'industrial',hours:5,energy:25,pay:3650,skill:'focus',game:'audit',risk:.02,description:'Брак считается допустимым, пока не твой.'},
  {id:'welding',name:'Сварить каркас',district:'industrial',hours:5,energy:34,pay:4100,skill:'grit',game:'timing',risk:.06,description:'Красиво получилось там, где не видно.'},
  {id:'archive',name:'Разобрать архив',district:'center',hours:4,energy:22,pay:6200,skill:'focus',game:'sort',risk:.01,description:'Пыль знает историю мэрии лучше учебника.'},
  {id:'host',name:'Встретить делегацию',district:'center',hours:4,energy:21,pay:7100,skill:'charm',game:'route',risk:.01,description:'Улыбка держится дольше согласованной речи.'},
  {id:'fundraise',name:'Найти спонсора',district:'glass',hours:4,energy:24,pay:17500,skill:'charm',game:'bargain',risk:.02,description:'Они хотят добра, желательно с логотипом.'},
  {id:'securityaudit',name:'Аудит доступа',district:'glass',hours:5,energy:27,pay:19200,skill:'focus',game:'audit',risk:.02,description:'Пароль сменили. Он всё ещё написан на стикере.'},
  {id:'artdeal',name:'Продать коллекцию',district:'heights',hours:4,energy:25,pay:36000,skill:'charm',game:'bargain',risk:.01,description:'Ценник зависит от того, кто на него смотрит.'},
  {id:'arbitration',name:'Уладить спор',district:'heights',hours:5,energy:32,pay:41000,skill:'charm',game:'sort',risk:.02,description:'Обе стороны победили в пресс-релизе.'}
];

export const homes = [
  {id:'sofa',name:'Диван у знакомого',price:0,daily:0,restore:28,prestige:0,description:'Валера пустил бесплатно. Еду и прочий быт ты оплачиваешь сам.'},
  {id:'hostel',name:'Койка без соседского дивана',price:2500,daily:155,restore:32,prestige:1,description:'Шторка — первая собственная граница.'},
  {id:'room',name:'Комната с ковром',price:7500,daily:190,restore:38,prestige:3,description:'Ковёр глушит соседей. Иногда.'},
  {id:'flat',name:'Однушка у кольца',price:90000,daily:520,restore:51,prestige:11,description:'Свой чайник. Чужой ипотечный страх.'},
  {id:'loft',name:'Лофт с кирпичом',price:680000,daily:2200,restore:65,prestige:26,description:'Кирпич старый, цена новая.'},
  {id:'duplex',name:'Двухуровневая квартира',price:3100000,daily:5900,restore:72,prestige:41,description:'Лестница внутри жилья звучит как повышение.'},
  {id:'penthouse',name:'Пентхаус с видом',price:8900000,daily:11500,restore:78,prestige:58,description:'Видно даже район, где всё начиналось.'},
  {id:'estate',name:'Усадьба на берегу',price:42000000,daily:38000,restore:90,prestige:100,description:'Свои ворота, чужие проблемы.'}
];

export const vehicles = [
  {id:'feet',name:'Два ботинка',price:0,upkeep:0,fareFactor:1,timeFactor:1,prestige:0,description:'Один ещё почти не течёт.'},
  {id:'bike',name:'Велосипед «Аист»',price:6500,upkeep:35,fareFactor:.72,timeFactor:.82,prestige:2,description:'Тормозит мыслью о последствиях.'},
  {id:'moped',name:'Мопед после брата',price:29000,upkeep:120,fareFactor:.58,timeFactor:.65,prestige:5,description:'Заводится со второй легенды.'},
  {id:'lada',name:'Лада «не битая»',price:145000,upkeep:460,fareFactor:.45,timeFactor:.55,prestige:11,description:'По документам почти европейская.'},
  {id:'sedan',name:'Седан в кредит',price:820000,upkeep:1750,fareFactor:.32,timeFactor:.45,prestige:24,description:'Кожаный салон пахнет процентами.'},
  {id:'suv',name:'Чёрный внедорожник',price:4200000,upkeep:7300,fareFactor:.22,timeFactor:.40,prestige:44,description:'Паркуется как аргумент.'},
  {id:'limousine',name:'Представительский седан',price:17500000,upkeep:22000,fareFactor:.15,timeFactor:.32,prestige:75,description:'Водитель делает вид, что не слышит.'}
];

export const businesses = [
  {id:'stall',name:'Ларёк у остановки',district:'yard',price:18000,base:630,upkeep:210,risk:.12,upgrade:11500,staff:120,description:'Чай, скотч и товары с характером.'},
  {id:'tire',name:'Шиномонтаж «Поворот»',district:'market',price:125000,base:2800,upkeep:940,risk:.17,upgrade:72000,staff:650,description:'Сезон начинается неожиданно каждый год.'},
  {id:'delivery',name:'Курьерская артель',district:'market',price:340000,base:5700,upkeep:2100,risk:.16,upgrade:195000,staff:1450,description:'Все обещают доставить вчера.'},
  {id:'garage',name:'Автосервис «Честно»',district:'industrial',price:880000,base:11200,upkeep:4300,risk:.2,upgrade:510000,staff:3800,description:'Название — рекламный эксперимент.'},
  {id:'cafe',name:'Кафе «Сделка»',district:'center',price:2450000,base:27800,upkeep:11500,risk:.19,upgrade:1450000,staff:8400,description:'Кофе подают с презентацией.'},
  {id:'agency',name:'Агентство впечатлений',district:'glass',price:9400000,base:93000,upkeep:39500,risk:.22,upgrade:5700000,staff:27000,description:'Создаёт спрос на собственные услуги.'},
  {id:'holding',name:'Городской холдинг',district:'heights',price:48000000,base:380000,upkeep:175000,risk:.28,upgrade:31000000,staff:115000,description:'Схема сложнее городской карты.'},
  {id:'laundry',name:'Прачечная «Чисто условно»',district:'yard',price:65000,base:1800,upkeep:660,risk:.1,upgrade:42000,staff:350,description:'Пятна уходят. Платежи остаются.'},
  {id:'pickup',name:'Пункт выдачи «Завтра»',district:'market',price:210000,base:3900,upkeep:1380,risk:.14,upgrade:130000,staff:900,description:'Клиенты приходят за коробкой и спором.'},
  {id:'packshop',name:'Цех упаковки',district:'industrial',price:1350000,base:17800,upkeep:7100,risk:.18,upgrade:800000,staff:5700,description:'Коробка красивая. Внутри пока экономика.'},
  {id:'rental',name:'Агентство аренды',district:'center',price:3900000,base:41500,upkeep:18400,risk:.16,upgrade:2300000,staff:13000,description:'Каждой квартире приписан «характер».'},
  {id:'prhouse',name:'Бюро репутации',district:'glass',price:15500000,base:149000,upkeep:67000,risk:.24,upgrade:8900000,staff:46000,description:'Исправляют новости быстрее, чем ошибки.'},
  {id:'trust',name:'Управляющая компания',district:'heights',price:76000000,base:620000,upkeep:294000,risk:.3,upgrade:49000000,staff:180000,description:'Обслуживает дома и чужие амбиции.'},
  {id:'repairshop',name:'Мастерская у дома',district:'yard',price:112000,base:2600,upkeep:970,risk:.13,upgrade:71000,staff:560,description:'Чинит мелочи и отношения с гарантией до выхода.'},
  {id:'canteen',name:'Столовая «Второе»',district:'market',price:520000,base:8900,upkeep:3600,risk:.18,upgrade:310000,staff:2300,description:'Компот стабилен даже при кризисе.'},
  {id:'metalworks',name:'Малый металлоцех',district:'industrial',price:2400000,base:30500,upkeep:13000,risk:.23,upgrade:1400000,staff:9500,description:'Станки гремят громче производственного плана.'},
  {id:'gallery',name:'Галерея нужных вкусов',district:'center',price:6500000,base:72000,upkeep:31800,risk:.2,upgrade:3900000,staff:21000,description:'Картины меняются реже спонсоров.'},
  {id:'techlab',name:'Лаборатория идей',district:'glass',price:24200000,base:215000,upkeep:101000,risk:.27,upgrade:14000000,staff:65000,description:'Продаёт будущее подпиской на квартал.'},
  {id:'casino',name:'Казино «Зеро»',district:'heights',price:98000000,base:830000,upkeep:410000,risk:.34,upgrade:61000000,staff:250000,description:'Здесь даже люстры знают цену удачи.'}
];

export const upgrades = [
  {id:'boots',name:'Нормальные ботинки',price:1900,stat:'grit',amount:1,description:'Сухие носки как инвестиция.'},
  {id:'phone',name:'Телефон с экраном',price:8500,stat:'focus',amount:1,description:'Пять приложений обещают доход.'},
  {id:'jacket',name:'Пиджак с плечами',price:23000,stat:'charm',amount:1,description:'Собеседник смотрит выше кармана.'},
  {id:'course',name:'Курс «Финансы»',price:49000,stat:'focus',amount:2,description:'Без вебинара о вебинарах.'},
  {id:'trainer',name:'Тренер по переговорам',price:160000,stat:'charm',amount:2,description:'Учит молчать за отдельную плату.'},
  {id:'assistant',name:'Личный помощник',price:720000,stat:'grit',amount:2,description:'Помнит встречи и прежние обещания.'},
  {id:'clean-shirt',name:'Чистая рубашка',price:1300,stat:'charm',amount:1,description:'Теперь на собеседовании пахнет только надеждой.'},
  {id:'work-jacket',name:'Куртка без дыр',price:4800,stat:'grit',amount:1,description:'Карманы целые. В них пока нечего положить.'},
  {id:'leather-jacket',name:'Кожанка с рынка',price:18000,stat:'charm',amount:1,description:'Скидку дали за уверенный взгляд.'},
  {id:'office-suit',name:'Первый костюм',price:85000,stat:'charm',amount:2,description:'Галстук пока живёт отдельной жизнью.'},
  {id:'tailored-suit',name:'Костюм по фигуре',price:790000,stat:'charm',amount:2,description:'Портной знает о тебе больше банка.'},
  {id:'cashmere-coat',name:'Пальто с подкладкой',price:4900000,stat:'charm',amount:3,description:'В нём даже очередь выглядит как встреча.'}
];

export const marketLabels = ['Затишье','Обычный день','Суета','Бум'];

