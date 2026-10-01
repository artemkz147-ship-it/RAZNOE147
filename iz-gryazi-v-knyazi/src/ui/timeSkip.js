import {backgrounds} from '../assets/manifest.js';
export function renderSkipTimer(s){
  let root=document.getElementById('skip-progress');const p=s.activeSkip;
  document.body.classList.toggle('skipping',!!p);
  if(!p){root?.remove();return;}
  if(!root){root=document.createElement('section');root.id='skip-progress';root.className='skip-progress';root.setAttribute('role','dialog');root.setAttribute('aria-label','Перемотка времени');root.innerHTML='<div class="skip-sky"></div><div class="skip-clock"><i class="clock-hour"></i><i class="clock-minute"></i><b></b><span class="clock12">12</span><span class="clock3">3</span><span class="clock6">6</span><span class="clock9">9</span></div><div class="skip-caption"><span>ВРЕМЯ ИДЁТ</span><h2></h2><p></p><progress></progress><button data-cancel-skip>ОСТАНОВИТЬ</button></div>';document.body.append(root);}
  const waiting=!!(s.pending||s.recentIncident||s.outcome||s.romance?.conflict);root.hidden=waiting;root.classList.toggle('waiting',waiting);
  root.style.setProperty('--skip-background',`url('${backgrounds[s.district]}')`);
  root.querySelector('h2').textContent=p.name;root.querySelector('p').textContent=`День ${s.day} · прошло ${p.worked} из ${p.planned} дней`;
  const bar=root.querySelector('progress');bar.max=p.planned;bar.value=p.worked;
}
