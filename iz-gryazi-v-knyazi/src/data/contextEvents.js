import { businesses,homes } from './world.js';
import { homeTerms } from '../systems/housing.js';
const jobs={
 janitor:[['Телефон под скамейкой','Во время уборки нашёл телефон. На экране — пропущенный звонок.','Вернуть владельцу',{respect:3,money:300},'Продать находку',{money:2800,crime:3},'Владелец поблагодарил тебя.','Телефон продан. Хозяин может обратиться в полицию.'],['Битое стекло','Среди мусора разбитые бутылки. Перчатки порвались.','Попросить новые перчатки',{energy:-3},'Продолжить голыми руками',{health:-7},'Работа закончена без порезов.','Осколок порезал ладонь.'],['Злая собака','Собака не даёт убрать возле контейнера.','Позвать хозяина',{energy:-4},'Прогнать самому',{health:-5,stress:4},'Хозяин увёл собаку.','Собака цапнула за руку.']],
 shop:[['Подозрительная купюра','Клиент протянул купюру, которую не принимает детектор.','Проверить с управляющим',{respect:2},'Принять без проверки',{money:-700,stress:3},'Подделка не попала в кассу.','После пересчёта обнаружилась недостача.'],['Хамство у кассы','Покупатель кричит из-за цены, которую ты не устанавливаешь.','Позвать администратора',{stress:2},'Ответить грубо',{respect:-3},'Ситуацию удалось закончить спокойно.','Покупатель написал жалобу.'],['Коллега просит подменить','У коллеги заболел ребёнок. Он просит остаться на два часа.','Остаться',{energy:-10,money:350,contacts:1},'Отказаться спокойно',{mood:1},'За подмену доплатили.','Коллега нашёл другую помощь.']],
 mechanic:[['Масло на полу','В цехе пролилось масло возле подъёмника.','Убрать перед работой',{energy:-5},'Обойти и продолжить',{health:-6},'Теперь проход безопасен.','На скользком полу подвернул ногу.'],['Скрытая неисправность','У машины обнаружилась неисправность, которой нет в заказе.','Сообщить клиенту',{respect:3,contacts:1},'Сделать вид, что не заметил',{stress:4,respect:-3},'Клиент согласовал дополнительный ремонт.','Проблема осталась. Клиент может вернуться с претензией.'],['Новая диагностика','Старший механик предлагает показать новый прибор после смены.','Остаться учиться',{energy:-7,business:2},'Пойти домой',{energy:3},'Ты разобрался с диагностикой.','Сегодня выбрал отдых.']],
 clerk:[['Ошибка в отчёте','В таблице расходится итог. Отчёт ещё не отправлен.','Исправить и предупредить',{energy:-5,respect:2},'Отправить как есть',{business:-3,stress:5},'Ошибку устранили до отправки.','Ошибка дошла до руководителя.'],['Чужие документы','В принтере остались документы с зарплатами коллег.','Передать ответственному',{respect:2},'Разослать в общий чат',{respect:-8,crime:2},'Документы убрали из общего доступа.','Руководство начало разбирательство.'],['Коллега просит совет','Коллега застрял на задаче, которую ты уже решал.','Объяснить',{energy:-4,contacts:1},'Сказать, что занят',{mood:1},'Коллега запомнил помощь.','Ты закончил свою работу.']],
 manager:[['Срыв поставки','Поставщик не привёз товар к сроку.','Найти замену',{energy:-8,business:2},'Скрывать до последнего',{business:-4,stress:5},'Удалось уменьшить простой.','Команда узнала слишком поздно.'],['Выгорание сотрудника','Сотрудник просит убрать сверхурочные.','Перестроить график',{energy:-5,respect:3},'Надавить',{respect:-5,stress:4},'Команда сохранила темп без лишних смен.','Сотрудник начал искать другую работу.'],['Премия за проект','Проект сдан вовремя. Команда ждёт решения о премии.','Разделить заслуги',{contacts:2,money:900},'Присвоить результат',{money:1600,respect:-5},'Тебя ценят как руководителя.','Премия получена, но доверие потеряно.']],
 director:[['Спорный контракт','Юрист отметил риск в договоре филиала.','Пересмотреть условия',{energy:-6,business:3},'Подписать не читая',{business:-5,stress:6},'Спорные условия исправлены.','Риск остался на твоей ответственности.'],['Утечка бюджета','В смете филиала найден повторный платёж.','Провести сверку',{energy:-7,respect:3},'Закрыть глаза',{crime:3,business:-4},'Повторный платёж остановлен.','Ошибка стала расходом филиала.'],['Совет директоров','Тебя просят защитить результаты команды.','Подготовить цифры',{energy:-8,business:3},'Обойтись красивой речью',{stress:4,business:-2},'Цифры убедили руководство.','Вопросов оказалось больше обещаний.']]
};
export const contextEvents=[];
for(const [career,rows] of Object.entries(jobs))rows.forEach((r,i)=>contextEvents.push({id:`work-${career}-${i}`,minDay:1,scope:'work',career,art:career==='janitor'?1:career==='mechanic'?4:10,test:s=>s.employment?.id===career&&s.employment?.lastWorkedDay===s.day, title:r[0],text:r[1],choices:[{text:r[2],effect:r[3],log:r[6]},{text:r[4],effect:r[5],log:r[7],misconduct:i===1&&career==='clerk',complaint:i===1&&career==='shop'}]}));
for(const firm of businesses){
 const sector=['stall','shop','shawarma','cafe'].includes(firm.id)?'товар':firm.district==='industrial'?'оборудование':'заказ';
 const cost=Math.max(300,firm.upkeep*2);
 const rows=[
  ['Повреждение после закрытия',`У дела «${firm.name}» повреждено ${sector==='оборудование'?'оборудование':'помещение'}. Утром работу придётся ограничить.`,'Оплатить ремонт',cost,'Закрыться до ремонта',0,'Ремонт выполнен. Дело снова работает.','Работа приостановлена до ремонта.','repair','pause'],
  ['Поставщик меняет условия',`Для «${firm.name}» поставщик поднял цены. Нужно решить, продолжать ли закупки.`,'Принять новые условия',0,'Найти другого поставщика',cost,'Постоянные расходы выросли на 8%.','Удалось сохранить прежние расходы.','expense','supplier'],
  ['Налёт вымогателей',`В «${firm.name}» пришли вымогатели. Требуют деньги и обещают вернуться.`,'Обратиться за защитой',Math.round(cost*.7),'Заплатить',cost,'Ты подал заявление и усилил защиту.','Они ушли с деньгами. Это не гарантирует, что не вернутся.','security','extortion'],
  ['Крупный заказ',`Делу «${firm.name}» предложили заказ больше обычного. Понадобятся материалы и время.`,'Вложиться в выполнение',cost,'Отказаться от заказа',0,'Материалы закуплены. Выручка временно выросла.','Ты сохранил обычный график.','order','none']
 ];
 rows.forEach((r,i)=>contextEvents.push({id:`firm-${firm.id}-${i}`,minDay:1,scope:'business',firmId:firm.id,art:11,test:s=>!!s.businesses[firm.id]&&!s.businesses[firm.id].paused&&(i!==2||!s.businesses[firm.id].security),title:r[0],text:r[1],choices:[{text:r[2],cost:r[3],firmAction:r[8],log:r[6]},{text:r[4],cost:r[5],firmAction:r[9],log:r[7]}]}));
 contextEvents.push({id:`staff-${firm.id}`,minDay:1,scope:'business',firmId:firm.id,art:11,test:s=>!!s.businesses[firm.id]?.staff&&!s.businesses[firm.id]?.audited,title:'Недостача у сотрудника',text:`При сверке денег «${firm.name}» обнаружилась недостача. Нужно проверить смену.`,choices:[{text:'Проверить кассу и установить учёт',cost,firmAction:'audit',log:'Ты усилил контроль и выяснил причину недостачи.'},{text:'Поверить на слово',effect:{money:-cost},log:'Недостачу пришлось закрыть собственными деньгами.'}]});
}
contextEvents.push(
 {id:'work-street-warning',scope:'work',minDay:1,art:10,test:s=>homeTerms(s.home).kind==='street'&&s.employment?.lastWorkedDay===s.day&&['clerk','manager','director','shop'].includes(s.employment.id),title:'Рабочий день после вокзала',text:'После ночёвок без своей комнаты ты пришёл на смену невыспавшимся. Руководитель заметил состояние и жалобы клиентов.',choices:[{text:'Привести себя в порядок и обсудить график',cost:250,effect:{energy:-8,stress:3},log:'Ты оплатил душ и объяснил ситуацию. Сегодня договор удалось сохранить.'},{text:'Нагрубить в ответ',misconduct:true,effect:{respect:-5},log:'Конфликт закончился увольнением. Ночлег на вокзале усугубил проблему.'}]},
 {id:'street-mugging',scope:'housing',minDay:1,art:3,test:s=>homeTerms(s.home).kind==='street',title:'Ночлег оказался чужим',text:'Ночью тебя разбудили люди, считающие это место своим.',choices:[{text:'Уйти и искать другое место',effect:{energy:-12,stress:5},log:'Ты ушёл без драки, но почти не спал.'},{text:'Спорить',effect:{health:-14,stress:8},log:'Словами закончить не удалось. Ты получил травмы.'}]},
 {id:'street-cold',scope:'housing',minDay:1,art:3,test:s=>homeTerms(s.home).kind==='street',title:'Холодная ночь',text:'Укрытие промокло. К утру начался кашель.',choices:[{text:'Зайти в тёплую столовую',cost:120,effect:{health:3,energy:4},log:'Ты согрелся, но нужен нормальный ночлег.'},{text:'Остаться',effect:{health:-6,energy:-8},log:'Холод ухудшил самочувствие.'}]},
 {id:'hostel-theft',scope:'housing',minDay:2,art:3,test:s=>s.home==='hostel',title:'Открытый шкафчик',text:'После ночёвки обнаружил, что шкафчик не заперт.',choices:[{text:'Проверить вещи и поставить замок',cost:250,effect:{stress:-2},log:'Вещи на месте. Замок куплен.'},{text:'Махнуть рукой',effect:{money:-100,stress:3},log:'Не досчитался небольшой суммы.'}]}
);
export function eventEligible(s,e){
 if(s.day<e.minDay||e.test&&!e.test(s))return false;
 if(e.needBusiness&&!Object.keys(s.businesses).length||e.needVehicle&&s.vehicle==='feet')return false;
 const kind=homeTerms(s.home).kind;
 if(['rent','renovation'].includes(e.id)&&kind!=='rent')return false;
 if(['leak','heating'].includes(e.id)&&!['rent','owned'].includes(kind))return false;
 if(['corporate-party'].includes(e.id)&&!s.employment)return false;
 if(e.id==='date'&&(!['lada','sedan','suv','limousine'].includes(s.vehicle)||!s.social?.sergey?.met||s.population?.departed?.sergey))return false;
 if(e.id==='employee'&&!Object.values(s.businesses).some(x=>x.staff))return false;
 if(e.id==='oldfriend'&&(!s.social?.valera?.met||s.population?.departed?.valera))return false;
 if(e.id==='article'&&!s.social?.vera?.met)return false;
 if(['paparazzi','luxury-shoot','charity'].includes(e.id)&&s.stats.fame<15)return false;
 if(e.id==='dating-app'&&(!s.upgrades.includes('phone')||s.home!=='sofa'))return false;
 if(e.id==='pothole'&&(!['lada','sedan','suv','limousine'].includes(s.vehicle)||s.lastTrip?.mode!=='own'||s.lastTrip.day<s.day-1))return false;
 return true;
}
export function applyEventAction(s,e,choice){
 if(e.id==='rent'){s.housing.multiplier*=choice.rentFactor||1;return;}
 if(choice.misconduct){s.careerMisconduct=true;}
 if(choice.complaint&&s.employment)s.employment.absences=(s.employment.absences||0)+2;
 const f=s.businesses[e.firmId];if(!f)return;
 const action=choice.firmAction;
 if(action==='repair'){f.condition=100;f.paused=false;}
 if(action==='pause'){f.paused=true;f.condition=Math.min(f.condition,60);}
 if(action==='expense')f.expenseFactor=(f.expenseFactor||1)*1.08;
 if(action==='security')f.security=true;
 if(action==='order'){f.orderUntil=s.day+7;}
 if(action==='audit')f.audited=true;
}
