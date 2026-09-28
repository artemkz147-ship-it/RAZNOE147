import { districts,jobs } from './data/world.js';
import { crimes } from './data/crime.js';
import { freshState } from './systems/state.js';
import { byId } from './systems/economy.js';
import { loadGame,saveGame,exportGame,importGame } from './systems/save.js';
import { GameEngine } from './systems/engine.js';
import { artScene } from './scene/artscene.js';
import { shell,modal,menuModal } from './ui/views.js';
import { MiniGame } from './ui/minigames.js';

const app=document.getElementById('app');
const loaded=loadGame();
const game=new GameEngine(loaded.state);
let tab='city',shopType='home',earningMode='legal',mini=null,menuOpen=false,dialogueId=null,dialogueResult=null;

function render() {
  document.body.className=`tab-${tab}`;
  app.innerHTML=shell(game.state,tab,shopType,mini,earningMode,dialogueId,dialogueResult);
  const district=byId(districts,game.state.district);
  document.getElementById('scene').innerHTML=artScene(district,game.state);
  if(menuOpen)document.getElementById('modal-root').innerHTML=menuModal(game.state);
}
function renderModal() {
  const root=document.getElementById('modal-root');
  if(root)root.innerHTML=menuOpen?menuModal(game.state):modal(game.state,mini,dialogueId,dialogueResult);
}
function toast(message,tone='good') {
  if(!message)return;
  const zone=document.getElementById('toast-zone');if(!zone)return;
  const node=document.createElement('div');node.className=`toast ${tone}`;node.textContent=message;
  zone.append(node);setTimeout(()=>node.remove(),4400);
  const scene=document.querySelector('.scene-wrap');
  if(scene&&tone!=='neutral'){
    scene.classList.add(tone==='bad'?'scene-shake':'scene-glow');
    setTimeout(()=>scene.classList.remove('scene-shake','scene-glow'),520);
  }
}
game.subscribe((_,result)=>{render();if(result&&!result.dialogueReply)toast(result.message,result.tone);});
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
function perform(action,id) {
  if(action==='menu'){menuOpen=true;renderModal();return;}
  if(action==='close-menu'){menuOpen=false;renderModal();return;}
  if(action==='tab-story'){tab='story';render();return;}
  if(action==='tab-work'){tab='work';render();return;}
  if(action==='tab-business'){tab='business';render();return;}
  if(action==='travel'){game.travel(id);return;}
  if(action==='job'){startJob(id);return;}
  if(action==='crime'){startCrime(id);return;}
  if(action==='casino'){game.playCasino(id,document.getElementById('casino-stake')?.value);return;}
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
    menuOpen=false;dialogueId=null;dialogueResult=null;tab='city';game.replaceState(freshState());return;
  }
}
document.addEventListener('click',event=>{
  const dialogueChoice=event.target.closest('[data-dialogue-choice]');
  if(dialogueChoice&&dialogueId){const result=game.person(dialogueId,'talk',Number(dialogueChoice.dataset.dialogueChoice));if(result.ok){dialogueResult=result;renderModal();}return;}
  const choice=event.target.closest('[data-choice]');
  if(choice){game.resolveChoice(Number(choice.dataset.choice));return;}
  const miniButton=event.target.closest('[data-mini]');
  if(miniButton){mini?.input(miniButton.dataset.mini,miniButton.dataset.value);return;}
  const tabButton=event.target.closest('[data-tab]');
  if(tabButton){tab=tabButton.dataset.tab;render();if(tabButton.classList.contains('scene-map'))document.getElementById('view')?.scrollIntoView({behavior:'smooth',block:'start'});return;}
  const shop=event.target.closest('[data-shop]');
  if(shop){shopType=shop.dataset.shop;render();return;}
  const earn=event.target.closest('[data-earn]');
  if(earn){earningMode=earn.dataset.earn;render();return;}
  const chip=event.target.closest('[data-bet]');
  if(chip){const field=document.getElementById('casino-stake');if(field)field.value=chip.dataset.bet;return;}
  const button=event.target.closest('[data-action]');
  if(button)perform(button.dataset.action,button.dataset.id);
  else if(event.target.classList.contains('dismissable')){menuOpen=false;renderModal();}
});
document.addEventListener('input',event=>{
  if(event.target.matches('[data-mini-input="offer"]'))mini?.setOffer(event.target.value);
});
document.addEventListener('change',async event=>{
  if(event.target.id!=='import-file'||!event.target.files?.length)return;
  try {const state=await importGame(event.target.files[0]);menuOpen=false;dialogueId=null;dialogueResult=null;tab='city';game.replaceState(state);}
  catch(error){toast(`Не удалось загрузить: ${error.message}`,'bad');}
});
document.addEventListener('keydown',event=>{
  if(event.code==='Space'&&mini?.job.game==='timing'){event.preventDefault();mini.input('timing');}
  if(event.key==='Escape'){
    if(menuOpen){menuOpen=false;renderModal();}
    else if(mini)mini.input('exit');
    else if(dialogueId){dialogueId=null;dialogueResult=null;renderModal();}
  }
});
window.addEventListener('beforeunload',()=>saveGame(game.state));
setInterval(()=>saveGame(game.state),30000);
