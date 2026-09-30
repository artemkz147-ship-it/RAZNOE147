const machine=[['supply','Питание','В',11,13,'Проверить питание и заменить повреждённый провод','Слабое питание не давало узлу запуститься.'],['bearing','Подшипник','дБ',15,25,'Заменить изношенный подшипник','Шум ушёл после замены подшипника.'],['cooling','Охлаждение','°C',30,45,'Очистить забитое охлаждение','Температура вернулась к рабочей.'],['contact','Контакт','Ом',0,2,'Очистить и закрепить разъём','Высокое сопротивление было вызвано плохим контактом.']];
const phone=[['battery','Аккумулятор','В',3.6,4.2,'Заменить неисправный аккумулятор','Питание стало стабильным.'],['port','Разъём зарядки','Ом',0,2,'Очистить и заменить повреждённый разъём','Контакт восстановился после ремонта разъёма.'],['display','Подсветка','мА',20,35,'Заменить повреждённый модуль экрана','Экран снова включается без сбоев.'],['sensor','Датчик касания','мс',5,15,'Переподключить шлейф датчика','Касания перестали пропадать.']];
export function diagnosticRound(job,round,rng=Math.random){
 const profile=job.id==='repairphone'?phone:machine,index=Math.min(3,Math.floor(rng()*4)),fault=profile[(index+round)%4][0];
 const readings=profile.map(([id,name,unit,min,max],i)=>({id,name,unit,min,max,value:id===fault?+(max+(max-min)*.7+1).toFixed(1):+((min+max)/2).toFixed(1)}));
 const repairs=profile.map(([id,,,min,max,action,reply])=>({id:'fix-'+id,fault:id,name:action,reply}));
 const offset=Math.floor(rng()*4);repairs.push(...repairs.splice(0,offset));
 return {fault,readings,repairs,reply:profile.find(x=>x[0]===fault)[6]};
}
