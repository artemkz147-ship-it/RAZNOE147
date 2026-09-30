import { districts,jobs } from './data/world.js';
import { crimes } from './data/crime.js';
import { freshState } from './systems/state.js';
import { byId } from './systems/economy.js';
import { loadGame,saveGame,exportGame,importGame } from './systems/save.js';
import { GameEngine } from './systems/engine.js';
import { artScene } from './scene/artscene.js';
import { shell,modal,menuModal } from './ui/views.js';
import {renderSkipTimer} from './ui/timeSkip.js';
import { MiniGame } from './ui/minigames.js';
import { socialGroup } from './systems/social.js';

if(window.AndroidGame)document.documentElement.classList.add('android-host');
const app=document.getElementById('app');
const loaded=loadGame();
const game=new GameEngine(loaded.state);
let tab='city',shopType='home',earningMode='legal',peopleMode='contacts',careMode='food',mini=null,menuOpen=false,dialogueId=null,dialogueResult=null,travelId=null,citizenId=null,citizenPane='life';
const railPositions=new Map();
let focusCurrentDistrict=false;

function addCarouselControls() {
  if(window.innerWidth>760)return;
  document.querySelectorAll('.district-grid,.job-grid,.asset-grid,.business-grid,.people-grid,.romance-grid,.invest-grid,.two-column,.story-list,.activity-grid,.casino-grid').forEach((rail,i)=>{
    if(rail.children.length<2)return;
    rail.id=`scroll-rail-${i}`;
    const controls=document.createElement('div');controls.className='carousel-controls';
    controls.innerHTML=`<span>ЛИСТАЙ КАРТОЧКИ · ${rail.children.length}</span><div><button type="button" data-carousel="-1" data-rail="${rail.id}" aria-label="Предыдущая карточка">‹</button><button type="button" data-carousel="1" data-rail="${rail.id}" aria-label="Следующая карточка">›</button></div>`;
    rail.before(controls);
    if(!focusCurrentDistrict&&railPositions.has(`${tab}:${i}`))rail.scrollLeft=railPositions.get(`${tab}:${i}`);
    else if(rail.classList.contains('district-grid')){
      const current=rail.querySelector('.here');
      if(current)rail.scrollLeft=current.offsetLeft-rail.offsetLeft;
    }
    else rail.scrollLeft=0;
    rail.addEventListener('scroll',()=>railPositions.set(`${tab}:${i}`,rail.scrollLeft),{passive:true});
  });
}

function render() {
  document.body.className=`tab-${tab}`;
  app.innerHTML=shell(game.state,tab,shopType,mini,earningMode,dialogueId,dialogueResult,travelId,peopleMode,careMode);
  const district=byId(districts,game.state.district);
  document.getElementById('scene').innerHTML=artScene(district,game.state);
  addCarouselControls();
  focusCurrentDistrict=false;
  if(menuOpen||citizenId)renderModal();
  renderSkipTimer(game.state);
}
function renderModal() {
  const root=document.getElementById('modal-root');
  if(root)root.innerHTML=menuOpen?menuModal(game.state):modal(game.state,mini,dialogueId,dialogueResult,travelId,citizenId,citizenPane);
}
function toast(message,tone='good') {
  if(!message)return;
  const zone=document.getElementById('toast-zone');if(!zone)return;
  const node=document.createElement('div');node.className=`toast ${tone}`;node.textContent=message;
  zone.append(node);setTimeout(()=>node.remove(),6500);
  const scene=document.querySelector('.scene-wrap');
  if(scene&&tone!=='neutral'){
    scene.classList.add(tone==='bad'?'scene-shake':'scene-glow');
    setTimeout(()=>scene.classList.remove('scene-shake','scene-glow'),520);
  }
}
game.subscribe((_,result)=>{if(result?.skipPreference)return;if(result?.timeSkipFrame){if(game.state.pending||game.state.recentIncident||game.state.romance?.conflict)render();else renderSkipTimer(game.state);return;}render();if(result&&!result.dialogueReply)toast(result.message,result.tone);});
render();
if(loaded.offlineDays)toast(`Пока тебя не было, прошло ${loaded.offlineDays} дн. Доходы и расходы учтены.`,'story');

function startJob(id) {
  if(game.state.jailDays){toast('Сначала выйди на свободу.','bad');return;}
  if(game.state.pending){toast('Сначала прими решение в истории.','bad');return;}
  const ready=game.jobReady(id);if(!ready.ok){toast(ready.message,ready.tone);return;}
  const job=byId(jobs,id);
  mini=new MiniGame(job,score=>{mini=null;if(score===null){render();toast('Смена прервана. Деньги за попытку не платят.','bad');return;}game.completeJob(id,score);});
  renderModal();mini.start(renderModal);
}
function startCrime(id) {
  const ready=game.crimeReady(id);if(!ready.ok){toast(ready.message,ready.tone);return;}
  const gig=byId(crimes,id);
  mini=new MiniGame(gig,score=>{mini=null;if(score===null){render();toast('Заказ сорвался. Пока без последствий.','bad');return;}game.completeCrime(id,score);});
  renderModal();mini.start(renderModal);
}
function perform(action,id,months) {
  if(action==='open-citizen'){citizenId=id;citizenPane='life';renderModal();return;}
  if(action==='close-citizen'){citizenId=null;renderModal();return;}
  if(action==='menu'){menuOpen=true;renderModal();return;}
  if(action==='close-menu'){menuOpen=false;renderModal();return;}
  if(action==='tab-story'){tab='story';render();window.scrollTo(0,0);return;}
  if(action==='tab-work'){tab='work';render();window.scrollTo(0,0);return;}
  if(action==='tab-business'){tab='business';render();window.scrollTo(0,0);return;}
  if(action==='choose-travel'){travelId=id;renderModal();return;}
  if(action==='close-travel'){travelId=null;renderModal();return;}
  if(action==='travel'){travelId=null;focusCurrentDistrict=true;game.travel(id);return;}
  if(action==='job'){startJob(id);return;}
  if(action==='crime'){startCrime(id);return;}
  if(action==='casino'){game.startCasino(id,document.getElementById('casino-stake')?.value);return;}
  if(action==='hire'){game.hire(id);return;}
  if(action==='leave'){game.requestLeave();return;}
  if(action==='quit-career'){game.quitCareer();return;}
  if(action==='career'){game.workCareer(id,months);return;}
  if(action==='diet'){game.setDiet(id);return;}
  if(action==='restart-after-death'){menuOpen=false;citizenId=null;tab='city';game.replaceState(freshState());return;}
  if(action==='serve'){game.serveSentence();return;}
  if(action==='activity'){game.activity(id);return;}
  if(action.startsWith('buy-')){game.buy(action.slice(4),id);return;}
  if(action.startsWith('equip-')){game.equip(action.slice(6),id);return;}
  if(action.startsWith('manage-')){game.manageBusiness(id,action.slice(7));return;}
  if(action==='talk'){dialogueId=id;dialogueResult=null;renderModal();return;}
  if(action==='close-dialogue'){dialogueId=null;dialogueResult=null;renderModal();return;}
  if(action==='favor'){game.person(id,action);return;}
  if(action==='invest'){const amount=document.getElementById(`invest-${id}`)?.value;game.invest(id,amount);return;}
  if(action==='pay-debt'){game.payDebt(document.getElementById('debt-amount')?.value);return;}
  if(action==='save'){saveGame(game.state);menuOpen=false;renderModal();toast('Прогресс сохранён.');return;}
  if(action==='export'){
    if(window.AndroidGame?.saveJson){
      window.AndroidGame.saveJson(JSON.stringify({...game.state,lastSaved:Date.now()},null,2),`iz-gryazi-v-knyazi-day-${game.state.day}.json`);
      toast('Выберите папку для сохранения.');return;
    }
    const url=URL.createObjectURL(exportGame(game.state));const link=document.createElement('a');
    link.href=url;link.download=`iz-gryazi-v-knyazi-day-${game.state.day}.json`;link.click();setTimeout(()=>URL.revokeObjectURL(url),30000);
    toast('Файл сохранения подготовлен.');return;
  }
  if(action==='import'){document.getElementById('import-file')?.click();return;}
  if(action==='reset'){
    if(!window.confirm('Начать новую игру? Текущий прогресс в браузере будет перезаписан.'))return;
    menuOpen=false;citizenId=null;dialogueId=null;dialogueResult=null;tab='city';game.replaceState(freshState());return;
  }
}
document.addEventListener('click',event=>{
  if(event.target.closest('[data-cancel-skip]')){game.finishTimeSkip();return;}
  const citizenTab=event.target.closest('[data-citizen-pane]');
  if(citizenTab){citizenPane=citizenTab.dataset.citizenPane;renderModal();return;}
  const citizenAction=event.target.closest('[data-citizen-action]');
  if(citizenAction){game.citizenAction(citizenAction.dataset.id,citizenAction.dataset.citizenAction);return;}
  const romanceChoice=event.target.closest('[data-romance-choice]');
  if(romanceChoice){game.resolveRomanceConflict(Number(romanceChoice.dataset.romanceChoice));return;}
  const romanceAction=event.target.closest('[data-romance-action]');
  if(romanceAction){const id=romanceAction.dataset.id,action=romanceAction.dataset.romanceAction,result=game.romanceAction(id,action);if(result.ok&&(action==='commit'||action==='separate')){peopleMode=action==='commit'?'romance':socialGroup(game.state,id);railPositions.delete('people:0');render();}return;}
  const socialAction=event.target.closest('[data-social-action]');
  if(socialAction){const result=game.socialAction(socialAction.dataset.id,socialAction.dataset.socialAction);if(result.ok){peopleMode=socialGroup(game.state,socialAction.dataset.id);railPositions.delete('people:0');render();}return;}
  const peopleTab=event.target.closest('[data-people-mode]');
  if(peopleTab){peopleMode=peopleTab.dataset.peopleMode;railPositions.delete('people:0');render();return;}
  const casinoAction=event.target.closest('[data-casino-action]');
  if(casinoAction){if(casinoAction.dataset.casinoAction==='close')game.closeCasino();else game.casinoAct(casinoAction.dataset.casinoAction,casinoAction.dataset.casinoValue);return;}
  const carousel=event.target.closest('[data-carousel]');
  if(carousel){const rail=document.getElementById(carousel.dataset.rail);if(rail)rail.scrollBy({left:Number(carousel.dataset.carousel)*(rail.firstElementChild?.getBoundingClientRect().width||rail.clientWidth)+Number(carousel.dataset.carousel)*10,behavior:'smooth'});return;}
  const travelMode=event.target.closest('[data-travel-mode]');
  if(travelMode){const id=travelMode.dataset.id,mode=travelMode.dataset.travelMode;travelId=null;focusCurrentDistrict=true;game.travel(id,mode);return;}
  const incidentChoice=event.target.closest('[data-incident-choice]');
  if(incidentChoice){game.resolveIncident(Number(incidentChoice.dataset.incidentChoice));return;}
  const dialogueChoice=event.target.closest('[data-dialogue-choice]');
  if(dialogueChoice&&dialogueId){const result=game.person(dialogueId,'talk',Number(dialogueChoice.dataset.dialogueChoice));if(result.ok){dialogueResult=result;renderModal();}return;}
  const choice=event.target.closest('[data-choice]');
  if(choice){game.resolveChoice(Number(choice.dataset.choice));return;}
  const miniButton=event.target.closest('[data-mini]');
  if(miniButton){mini?.input(miniButton.dataset.mini,miniButton.dataset.value);return;}
  const tabButton=event.target.closest('[data-tab]');
  if(tabButton){tab=tabButton.dataset.tab;render();if(tabButton.classList.contains('scene-map'))document.getElementById('view')?.scrollIntoView({behavior:'smooth',block:'start'});else window.scrollTo(0,0);return;}
  const shop=event.target.closest('[data-shop]');
  if(shop){shopType=shop.dataset.shop;render();return;}
  const care=event.target.closest('[data-care-mode]');
  if(care){careMode=care.dataset.careMode;tab='life';render();return;}
  const earn=event.target.closest('[data-earn]');
  if(earn){earningMode=earn.dataset.earn;render();return;}
  const chip=event.target.closest('[data-bet]');
  if(chip){const field=document.getElementById('casino-stake');if(field)field.value=chip.dataset.bet;return;}
  const button=event.target.closest('[data-action]');
  if(button?.dataset.action==='skip'){
    const panel=button.closest('.skip-controls');
    game.startTimeSkip(button.dataset.kind,button.dataset.id,Number(panel.querySelector('.skip-months').value),panel.querySelector('.skip-diet').value);
    return;
  }
  if(button)perform(button.dataset.action,button.dataset.id,button.dataset.months);
  else if(event.target.classList.contains('dismissable')){menuOpen=false;renderModal();}
});
document.addEventListener('input',event=>{
  if(event.target.matches('[data-mini-input="offer"]'))mini?.setOffer(event.target.value);
});
document.addEventListener('change',async event=>{
  if(event.target.matches('.skip-diet')){game.chooseSkipDiet(event.target.value);return;}
  if(event.target.id!=='import-file'||!event.target.files?.length)return;
  try {const state=await importGame(event.target.files[0]);menuOpen=false;dialogueId=null;dialogueResult=null;tab='city';game.replaceState(state);}
  catch(error){toast(`Не удалось загрузить: ${error.message}`,'bad');}
});
document.addEventListener('keydown',event=>{
  if(event.code==='Space'&&mini?.job.game==='timing'){event.preventDefault();mini.input('timing');}
  if(event.key==='Escape'){
    if(menuOpen){menuOpen=false;renderModal();}
    else if(mini)mini.input('exit');
      else if(citizenId){citizenId=null;renderModal();}
      else if(travelId){travelId=null;renderModal();}
    else if(dialogueId){dialogueId=null;dialogueResult=null;renderModal();}
  }
});
window.addEventListener('beforeunload',()=>saveGame(game.state));
setInterval(()=>saveGame(game.state),30000);
setInterval(()=>{if(game.state.activeSkip)game.advanceTimeSkip();},1000/7);
