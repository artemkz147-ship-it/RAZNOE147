import { netWorth } from '../systems/economy.js';
import { homes } from './world.js';

export const romancePeople=[
  {id:'marina',name:'Марина',role:'знакомая со старого двора',district:'yard',portrait:0,dateCost:160,giftCost:320,commit:18,description:'С ней легко заговорить. Планы на завтра у неё меняются чаще погоды.',lines:['«Пойдём гулять? Деньги подождут».','«Ты снова весь в делах? Я думала, будет веселей».','«Иногда хочется начать заново. Без обещаний».']},
  {id:'nina',name:'Нина',role:'бухгалтер и волонтёр',district:'industrial',portrait:1,dateCost:420,giftCost:650,commit:62,description:'Ценит честность и постоянство. Быстрые подарки её не убеждают.',lines:['«Расскажи, как прошла смена. Я слушаю».','«Мне важнее, что ты делаешь каждый день».','«Дом строится не по красивой речи».']},
  {id:'alisa',name:'Алиса',role:'яркая знакомая с рынка',district:'market',portrait:2,dateCost:650,giftCost:1800,commit:34,description:'Любит шумные планы и чужое внимание. Быт ей пока кажется скучной темой.',lines:['«Давай снимем что-нибудь красивое».','«Ты опять считаешь расходы? Ну скучно же».','«Сегодня хочу всё поменять».']},
  {id:'viktoria',name:'Виктория',role:'гостья дорогих вечеров',district:'glass',portrait:3,dateCost:18000,giftCost:42000,commit:52,description:'Встречи с ней стоят дорого. Внимание, статус и личная свобода для неё важны.',lines:['«В этом городе любят победителей».','«Обещания звучат лучше с хорошим видом».','«Наши планы зависят от обстоятельств».']},
  {id:'irina',name:'Ирина',role:'навязчивая новая знакомая',district:null,portrait:4,dateCost:1200,giftCost:2200,commit:28,description:'Появляется, когда тебя начинают замечать. Очень быстро узнаёт о тебе слишком много.',lines:['«Я случайно оказалась рядом».','«Почему ты не ответил сразу?»','«У нас ведь нет секретов?»']}
];

export function romanceAccess(state,person){
  if(person.id==='nina'){
    if(state.jobsDone<5)return 'Нужны пять рабочих смен';
    if(homes.findIndex(x=>x.id===state.home)<1)return 'Нужно отдельное жильё, хотя бы койка';
    if(state.stats.stress>=75)return 'Сначала справься со стрессом';
  }
  if(person.id==='viktoria'&&netWorth(state)<2000000)return 'Нужен капитал от 2 млн ₽';
  if(person.id==='irina'&&netWorth(state)<100000)return 'Появится, когда заработаешь 100 тыс. ₽';
  if(person.district&&!state.visitedDistricts?.includes(person.district))return `Сначала побывай в районе «${person.district==='industrial'?'Промзона':person.district==='glass'?'Стеклянный квартал':person.district==='market'?'Рынок':'Пятиэтажки'}»`;
  return '';
}

export const romancePortraitAtlas=new URL('../assets/people/romance-atlas.jpg',import.meta.url).href;
