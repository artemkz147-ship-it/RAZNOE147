import {moveHome} from './housing.js';
import { people } from '../data/people.js';
import { romancePeople } from '../data/romance.js';
import { addLog,adjust } from './state.js';

export const startingAges={sergey:34,valera:48,tamara:64,azamat:39,lida:43,vera:35,artur:55,minister:60,rosa:57,pasha:49,zoya:52,marina:30,nina:32,alisa:20,viktoria:25,irina:36};
const surnames={male:['Орлов','Соколов','Миронов','Ким','Ахметов','Белов','Лазарев','Волков'],female:['Орлова','Соколова','Миронова','Ким','Ахметова','Белова','Лазарева','Волкова']};
const residents=[
  {names:['Данил','Кирилл','Рустам'],role:'курьер новой волны',district:'yard',portraitId:'azamat',accent:'#b28762',age:24,threshold:0,favor:'Доставить посылку',cost:90,effect:{contacts:1,energy:-3},relation:2,lines:['«Вчера адреса здесь не было. Сегодня дом уже есть».','«Маршрут меняется, а подъезд без лифта остаётся».']},
  {names:['Оксана','Айгуль','Полина'],female:true,role:'хозяйка маленького кафе',district:'market',portraitId:'rosa',accent:'#c29a78',age:37,threshold:8,romance:true,dateCost:900,giftCost:1100,commit:42,description:'Открыла кафе своими силами. Ценит надёжность и не любит пустых обещаний.',favor:'Накормить смену',cost:680,effect:{energy:9,mood:3,contacts:1},relation:3,lines:['«Сегодня суп. Завтра посмотрим, кого приведёт город».','«Постоянный клиент узнаётся не по кошельку».']},
  {names:['Савелий','Марат','Глеб'],role:'мастер из нового цеха',district:'industrial',portraitId:'pasha',accent:'#9b9079',age:41,threshold:24,favor:'Помочь с инструментом',cost:550,effect:{health:2,respect:2},relation:2,lines:['«Станки стареют быстрее людей, если их не слушать».','«Работа найдётся. Вопрос, сколько она заберёт сил».']},
  {names:['Елена','Диана','Милена'],female:true,role:'фельдшер районной клиники',district:'center',portraitId:'lida',accent:'#b0a2a7',age:29,threshold:48,romance:true,dateCost:1200,giftCost:1700,commit:55,description:'Привыкла заботиться о других, но хочет видеть заботу и к себе.',favor:'Записать к врачу',cost:900,effect:{health:8,stress:-2},relation:3,lines:['«Травмы не любят, когда их называют пустяком».','«Сначала здоровье, потом великие планы».']},
  {names:['Тимур','Максим','Егор'],role:'аналитик молодой фирмы',district:'glass',portraitId:'artur',accent:'#afa38d',age:32,threshold:82,favor:'Проверить расчёты',cost:3500,effect:{business:3,contacts:1},relation:3,lines:['«Цифры честны ровно до первого совещания».','«Я здесь недавно. Город пока не привык к моим вопросам».']},
  {names:['Людмила','Раиса','Анна'],female:true,role:'организатор нового фонда',district:'heights',portraitId:'zoya',accent:'#aaa6b5',age:50,threshold:135,favor:'Представить волонтёрам',cost:4200,effect:{contacts:3,respect:2},relation:3,lines:['«Благотворительность начинается с тех, кого не пригласили».','«У денег длинная память. У людей иногда короче».']}
];

export const populationState=()=>({residents:[],departed:{},nextArrivalDay:181});
export const personAge=(state,person)=>{
  const base=person.ageAtArrival??startingAges[person.id]??35;
  const birthday=person.birthdayOffset??[...person.id].reduce((sum,ch)=>(sum*31+ch.charCodeAt(0))%360,0);
  return base+Math.floor((state.day-(person.arrivedDay||1)+birthday)/360);
};
export const isPresent=(state,id)=>!state.population?.departed?.[id];
export const livingPeople=state=>[...people,...(state.population?.residents||[]).filter(p=>!p.romance)].filter(p=>isPresent(state,p.id));
export const livingRomancePeople=state=>[...romancePeople,...(state.population?.residents||[]).filter(p=>p.romance)].filter(p=>isPresent(state,p.id));
export const knownDepartures=state=>Object.entries(state.population?.departed||{}).map(([id,record])=>({id,...record,person:[...people,...romancePeople,...(state.population?.residents||[])].find(p=>p.id===id)})).filter(x=>x.person);

function arrive(state,arrivalDay=state.day){
  const population=state.population,number=population.residents.length,template=residents[number%residents.length];
  const cycle=Math.floor(number/residents.length),name=`${template.names[cycle%template.names.length]} ${surnames[template.female?'female':'male'][Math.floor(cycle/template.names.length)%8]}`;
  const person={...template,id:`resident-${number+1}`,name,ageAtArrival:template.age+cycle%5,arrivedDay:arrivalDay,lines:[...template.lines]};
  delete person.names;delete person.age;delete person.female;
  population.residents.push(person);
  population.nextArrivalDay=arrivalDay+180;
  if(arrivalDay===state.day)addLog(state,`В ${person.district==='yard'?'Пятиэтажках':person.district==='market'?'рынке':person.district==='industrial'?'промзоне':person.district==='center'?'центре':person.district==='glass'?'Стеклянном квартале':'Верхнем береге'} появился новый человек: ${name}.`,'story');
}

function leave(state,person){
  const age=personAge(state,person),partner=(state.romance?.partners||[]).includes(person.id)||state.romance?.partner===person.id;
  const score=state.social?.[person.id]?.score||0;
  state.population.departed[person.id]={day:state.day,age,cause:'Ушёл из жизни'};
  if(partner){state.romance.partners=(state.romance.partners||[]).filter(id=>id!==person.id);state.romance.partner=state.romance.partners.at(-1)||null;state.romance.profiles[person.id].married=false;adjust(state,{mood:-16,stress:14});}
  else if(score>=30)adjust(state,{mood:-8,stress:5});
  if(state.romance?.conflict?.partnerId===person.id)state.romance.conflict=null;
  if(person.id==='sergey'&&state.home==='sofa'){
    moveHome(state,'station');if(!state.ownedHomes.includes('station'))state.ownedHomes.push('station');
    addLog(state,'После ухода Серёги диван больше недоступен. Тебе пришлось искать бесплатный ночлег на вокзале.','bad');
  }
  addLog(state,`${person.name} ушёл из жизни в ${age} лет.${partner?' Вы были вместе.':''}`,'bad');
  if(!state.recentIncident&&!state.pending)state.recentIncident={title:`Память о ${person.name}`,text:`${person.name} ушёл из жизни в ${age} лет. Город продолжает шуметь, а вашей истории больше не будет нового разговора.`,portrait:person.portrait,image:person.portrait===undefined?`person:${person.id}`:undefined,art:0,choices:[{text:'Вспомнить хорошие моменты',effect:{mood:2,stress:-3},reply:'Ты сохранил тёплые воспоминания.'},{text:'Побыть одному',effect:{energy:3,stress:2},reply:'Ты дал себе время пережить новость.'}]};
}

export function populationDay(state,rng=Math.random,quiet=false){
  state.population ||= populationState();
  let arrived=0;
  while(state.day>=state.population.nextArrivalDay){arrive(state,state.population.nextArrivalDay);arrived++;}
  if(arrived>1)addLog(state,`За прошедшие годы в городе появились новые жители: ${arrived}.`,'story');
  if(!quiet&&!state.recentIncident&&!state.pending&&!state.jailDays){
    const birthday=[...people,...romancePeople,...state.population.residents].find(person=>isPresent(state,person.id)&&(state.social?.[person.id]?.met||state.dialogueProgress?.[person.id]||state.romance?.profiles?.[person.id]?.met)&&personAge(state,person)>personAge({...state,day:state.day-1},person));
    if(birthday){
      const age=personAge(state,birthday),romance=birthday.romance||romancePeople.some(p=>p.id===birthday.id),gift=Math.round(birthday.giftCost||600);
      const bond=romance?{rapport:{id:birthday.id,delta:5}}:{socialDelta:5};
      state.recentIncident={title:`День рождения ${birthday.name}`,text:`${birthday.name} исполнилось ${age} лет. Можно уделить человеку время или выбрать подарок.`,portrait:birthday.portrait,image:birthday.portrait===undefined?`person:${birthday.id}`:undefined,art:0,socialId:birthday.id,choices:[{text:'Поздравить лично',effect:{energy:-3,mood:2},...bond,reply:'Вы провели немного времени вместе.'},{text:`Подарить полезную вещь · ${gift} ₽`,cost:gift,effect:{mood:3,contacts:1},...(romance?{rapport:{id:birthday.id,delta:9}}:{socialDelta:9}),reply:'Подарок пришёлся кстати.'}]};
      addLog(state,`${birthday.name}: день рождения, ${age} лет.`,'story');
    }
  }
  if(state.day%30!==0)return;
  for(const person of [...people,...romancePeople,...state.population.residents]){
    if(!isPresent(state,person.id))continue;
    const age=personAge(state,person);
    if(age<67)continue;
    const mortality=Math.min(.35,Math.pow(age-66,1.7)*.00075);
    if(rng()<mortality){leave(state,person);break;}
  }
}
