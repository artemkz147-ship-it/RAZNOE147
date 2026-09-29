const suits=['♠','♥','♦','♣'];
const reds=new Set([1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36]);
export const isRed=n=>reds.has(n);
const label=card=>`${{11:'В',12:'Д',13:'К',14:'Т'}[card.rank]||card.rank}${card.suit}`;
export const cardLabel=label;
function deck(){return suits.flatMap(suit=>Array.from({length:13},(_,i)=>({rank:i+2,suit})));}
function draw(cards,rng){return cards.splice(Math.min(cards.length-1,Math.floor(rng()*cards.length)),1)[0];}
export function blackjackValue(cards){let sum=cards.reduce((n,c)=>n+(c.rank===14?11:Math.min(10,c.rank)),0),aces=cards.filter(c=>c.rank===14).length;while(sum>21&&aces-- >0)sum-=10;return sum;}
export function pokerScore(cards){
  const ranks=cards.map(c=>c.rank).sort((a,b)=>b-a),counts=new Map();ranks.forEach(n=>counts.set(n,(counts.get(n)||0)+1));
  const groups=[...counts].sort((a,b)=>b[1]-a[1]||b[0]-a[0]);
  const flush=cards.every(c=>c.suit===cards[0].suit),unique=[...new Set(ranks)],wheel=unique.join(',')==='14,5,4,3,2';
  const straight=unique.length===5&&(wheel||unique[0]-unique[4]===4),highStraight=wheel?5:unique[0];
  if(straight&&flush)return [8,highStraight];
  if(groups[0][1]===4)return [7,groups[0][0],groups[1][0]];
  if(groups[0][1]===3&&groups[1][1]===2)return [6,groups[0][0],groups[1][0]];
  if(flush)return [5,...ranks];
  if(straight)return [4,highStraight];
  if(groups[0][1]===3)return [3,groups[0][0],...groups.slice(1).map(x=>x[0])];
  if(groups[0][1]===2&&groups[1][1]===2)return [2,Math.max(groups[0][0],groups[1][0]),Math.min(groups[0][0],groups[1][0]),groups[2][0]];
  if(groups[0][1]===2)return [1,groups[0][0],...groups.slice(1).map(x=>x[0])];
  return [0,...ranks];
}
const scoreNames=['Старшая карта','Пара','Две пары','Тройка','Стрит','Флеш','Фул-хаус','Каре','Стрит-флеш'];
function compare(a,b){for(let i=0;i<Math.max(a.length,b.length);i++){if((a[i]||0)!==(b[i]||0))return (a[i]||0)-(b[i]||0)}return 0;}
function rivalDraw(table,rng){
  const score=pokerScore(table.rival),keep=score[0]>=1?table.rival.filter(c=>table.rival.filter(x=>x.rank===c.rank).length>1):table.rival.filter(c=>c.rank>=11);
  const replace=table.rival.filter(c=>!keep.includes(c)).slice(0,3);for(const c of replace)table.rival[table.rival.indexOf(c)]=draw(table.deck,rng);
}
export function createCasinoTable(id,stake,rng){
  const base={id,stake,wager:stake,phase:'play',result:null,returned:0,rivalName:id==='poker'?'Артур «Тихий»':'Крупье Нина'};
  if(id==='roulette')return {...base,selection:null,pocket:null};
  const cards=deck();
  if(id==='blackjack')return {...base,deck:cards,player:[draw(cards,rng),draw(cards,rng)],rival:[draw(cards,rng),draw(cards,rng)]};
  if(id==='poker')return {...base,deck:cards,player:Array.from({length:5},()=>draw(cards,rng)),rival:Array.from({length:5},()=>draw(cards,rng)),selected:[],drawn:false,rivalName:'Артур «Тихий»'};
  return null;
}
export function actCasinoTable(table,action,value,rng){
  if(!table||table.phase==='done')return {error:'Партия уже завершена.'};
  if(table.id==='roulette'){
    if(action==='select'){const valid=['red','black','even','odd'].includes(value)||/^n(?:[0-9]|[12][0-9]|3[0-6])$/.test(value);if(!valid)return {error:'Выбери сектор или число.'};table.selection=value;return {message:'Ставка выбрана.'};}
    if(action!=='spin'||!table.selection)return {error:'Сначала выбери ставку.'};
    table.pocket=Math.floor(rng()*37);const n=table.pocket,hit=table.selection==='red'?isRed(n):table.selection==='black'?n>0&&!isRed(n):table.selection==='even'?n>0&&n%2===0:table.selection==='odd'?n%2===1:Number(table.selection.slice(1))===n;
    table.returned=hit?table.stake*(table.selection[0]==='n'?36:2):0;table.result=`Шарик: ${n}${n===0?' · зеро':isRed(n)?' · красное':' · чёрное'}. ${hit?'Ставка сыграла.':'Ставка проиграла.'}`;table.phase='done';return {done:true};
  }
  if(table.id==='blackjack'){
    if(action==='hit'){table.player.push(draw(table.deck,rng));if(blackjackValue(table.player)>21){table.phase='done';table.result='Перебор. Крупье забирает ставку.';return {done:true}}return {message:'Взята ещё карта.'};}
    if(action!=='stand'&&action!=='double')return {error:'Выбери карту или остановись.'};
    if(action==='double'){table.wager+=table.stake;table.player.push(draw(table.deck,rng));if(blackjackValue(table.player)>21){table.phase='done';table.result='После удвоения перебор.';return {done:true,extraStake:table.stake}}}
    while(blackjackValue(table.rival)<17)table.rival.push(draw(table.deck,rng));
    const p=blackjackValue(table.player),d=blackjackValue(table.rival),natural=p===21&&table.player.length===2;
    table.returned=d>21||p>d?Math.round(table.wager*(natural?2.5:2)):p===d?table.wager:0;
    table.result=`У тебя ${p}, у крупье ${d}. ${table.returned>table.wager?'Победа!':table.returned?'Ничья, ставка вернулась.':'Крупье выиграл.'}`;table.phase='done';return {done:true,extraStake:action==='double'?table.stake:0};
  }
  if(table.id==='poker'){
    if(action==='toggle'&&!table.drawn){const index=Number(value);if(!Number.isInteger(index)||index<0||index>4)return {error:'Карта не найдена.'};table.selected=table.selected.includes(index)?table.selected.filter(x=>x!==index):table.selected.length<3?[...table.selected,index]:table.selected;return {message:'Карты отмечены.'};}
    if(action==='draw'&&!table.drawn){for(const index of table.selected)table.player[index]=draw(table.deck,rng);table.drawn=true;table.selected=[];rivalDraw(table,rng);return {message:'Карты обменяны. Реши, продолжать ли торг.'};}
    if(!table.drawn)return {error:'Сначала обменяй карты или оставь свои.'};
    if(action==='fold'){table.phase='done';table.result='Ты сбросил карты. Ставка осталась в банке.';return {done:true};}
    if(!['showdown','raise'].includes(action))return {error:'Выбери действие.'};
    const player=pokerScore(table.player),rival=pokerScore(table.rival),extra=action==='raise'?table.stake:0,call=action!=='raise'||rival[0]>=1||rng()<.22;
    if(!call){table.returned=table.stake*3;table.result='Артур сбросил карты после повышения. Банк твой.';}
    else{const win=compare(player,rival),pot=table.stake*(action==='raise'?4:2);table.returned=win>0?pot:win===0?pot/2:0;table.result=`У тебя ${scoreNames[player[0]]}, у Артура ${scoreNames[rival[0]]}. ${win>0?'Ты выиграл банк.':win===0?'Разделили банк.':'Артур забрал банк.'}`;}
    table.wager+=extra;table.phase='done';return {done:true,extraStake:extra};
  }
  return {error:'Стол не найден.'};
}
