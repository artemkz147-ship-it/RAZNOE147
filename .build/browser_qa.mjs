import puppeteer from 'puppeteer-core';

const chrome=process.env.CHROME;
if(!chrome) throw new Error('CHROME executable not provided');
const browser=await puppeteer.launch({headless:true,executablePath:chrome,args:['--no-sandbox','--disable-dev-shm-usage','--enable-webgl','--ignore-gpu-blocklist','--enable-unsafe-swiftshader','--use-angle=swiftshader','--autoplay-policy=no-user-gesture-required','--disable-background-timer-throttling','--disable-renderer-backgrounding','--disable-backgrounding-occluded-windows']});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));

async function openPage(url,width,height,file,wait=4500){
  const page=await browser.newPage();
  await page.setViewport({width,height,deviceScaleFactor:1});
  page.on('pageerror',e=>console.error('PAGE ERROR:',e.message));
  page.on('console',m=>{if(m.type()==='error')console.error('CONSOLE:',m.text());});
  await page.goto(url,{waitUntil:'networkidle0',timeout:30000});
  await page.waitForFunction(()=>document.body?.dataset?.gameReady==='true',{timeout:30000});
  await sleep(wait);
  const data=await page.evaluate(()=>({...document.body.dataset}));
  console.log(file||'combat',JSON.stringify(data));
  if(file) await page.screenshot({path:file});
  await page.close();
  return data;
}

const combat=await openPage('http://127.0.0.1:4173/?autostart=1&debug-fast=1&map=sunny_meadow&hero=donut_knight',1280,720,null,5500);
for(const key of ['qaShots','qaHits','qaKills']){
  if(!(Number(combat[key])>0)) throw new Error(`Real combat QA failed: ${key}=${combat[key]??'missing'}`);
}
if(!['playing','upgrade','gameover','victory','chest'].includes(combat.gameState)) throw new Error(`Unexpected game state ${combat.gameState}`);

const cases=[
 ['sunny_meadow','donut_knight','qa-sunny-meadow.png'],
 ['candy_park','candy_witch','qa-candy-park.png'],
 ['snow_festival','snow_penguin','qa-snow-festival.png'],
 ['rainbow_canyon','fox_archer','qa-rainbow-canyon.png'],
 ['firefly_swamp','lightning_bee','qa-firefly-swamp.png']
];
for(const [map,hero,file] of cases) await openPage(`http://127.0.0.1:4173/?autostart=1&map=${map}&hero=${hero}`,1280,720,file,3600);
await openPage('http://127.0.0.1:4173/?autostart=1&map=festival_finale&hero=pumpkin_jester',412,915,'qa-mobile-festival.png',3600);
await browser.close();
console.log('Real-time WebGL combat and visual QA passed.');
