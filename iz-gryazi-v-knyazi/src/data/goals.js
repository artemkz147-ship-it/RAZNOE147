import { netWorth } from '../systems/economy.js';
import { homes,vehicles } from './world.js';

export const routesToSuccess=[
  {id:'builder',name:'Предприниматель',text:'Построить дела, которым город доверяет.',art:'industrial',need:{wealth:30000000,businesses:4,respect:100}},
  {id:'civic',name:'Человек города',text:'Иметь капитал, связи и уважение жителей.',art:'center',need:{wealth:20000000,contacts:35,respect:150}},
  {id:'shadow',name:'Теневой хозяин',text:'Собрать большое состояние опасным путём.',art:'glass',need:{wealth:50000000,crime:45,businesses:2}}
];

export const milestones=[
  {id:'first-pay',name:'Первые деньги',text:'Получить оплату за любую смену.',art:'yard',need:{jobs:1}},
  {id:'market',name:'Открыть рынок',text:'Заработать уважение, деньги или связи для входа.',art:'market',need:{district:'market'}},
  {id:'own-room',name:'Своя дверь',text:'Переехать с чужого дивана.',art:'yard',need:{homeRank:1}},
  {id:'first-vehicle',name:'Свои колёса',text:'Купить первый транспорт.',art:'market',need:{vehicleRank:1}},
  {id:'first-business',name:'Первое дело',text:'Купить и запустить бизнес.',art:'industrial',need:{businesses:1}},
  {id:'connections',name:'Тебя знают',text:'Завести 15 полезных связей.',art:'center',need:{contacts:15}},
  {id:'million',name:'Первый миллион',text:'Довести состояние до миллиона рублей.',art:'glass',need:{wealth:1000000}},
  {id:'team',name:'Сеть дел',text:'Владеть четырьмя бизнесами.',art:'industrial',need:{businesses:4}},
  {id:'penthouse',name:'Ключ от высоты',text:'Купить пентхаус или усадьбу.',art:'heights',need:{homeRank:6}},
  {id:'ten-million',name:'Большие деньги',text:'Довести состояние до десяти миллионов рублей.',art:'glass',need:{wealth:10000000}}
];

export function goalsSnapshot(state){
  return {wealth:netWorth(state),businesses:Object.keys(state.businesses).length,respect:state.stats.respect,contacts:state.stats.contacts,crime:state.stats.crime,jobs:state.jobsDone,homeRank:Math.max(...state.ownedHomes.map(id=>Math.min(7,homes.findIndex(x=>x.id===id)))),vehicleRank:Math.max(...state.ownedVehicles.map(id=>vehicles.findIndex(x=>x.id===id))),districts:state.visitedDistricts||[state.district]};
}
export function goalReached(state,goal){
  const data=goalsSnapshot(state);
  return Object.entries(goal.need).every(([key,target])=>key==='district'?data.districts.includes(target):data[key]>=target);
}
export function successRoute(state){return routesToSuccess.find(route=>goalReached(state,route))||null;}
export function successRoutes(state){return routesToSuccess.filter(route=>goalReached(state,route));}
