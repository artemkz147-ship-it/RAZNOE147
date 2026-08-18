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

async function heroArtQA(){
  const page=await browser.newPage();
  await page.setViewport({width:1365,height:768,deviceScaleFactor:1});
  page.on('pageerror',e=>console.error('PAGE ERROR:',e.message));
  await page.goto('http://127.0.0.1:4173/',{waitUntil:'networkidle0',timeout:30000});
  await page.waitForFunction(()=>document.body?.dataset?.gameReady==='true',{timeout:30000});
  await page.click('#hero-btn');
  await page.waitForSelector('#hero-panel.screen--visible',{timeout:5000});
  await page.waitForFunction(()=>{
    const imgs=[...document.querySelectorAll('#hero-grid .hero-art img')];
    return imgs.length===15&&imgs.every(i=>i.complete&&i.naturalWidth>32&&i.naturalHeight>32);
  },{timeout:15000});
  const art=await page.evaluate(()=>({cards:document.querySelectorAll('#hero-grid .hero-card').length,images:document.querySelectorAll('#hero-grid .hero-art img').length,broken:[...document.querySelectorAll('#hero-grid .hero-art img')].filter(i=>!i.complete||!i.naturalWidth).length}));
  if(art.cards!==15||art.images!==15||art.broken) throw new Error(`Hero art QA failed ${JSON.stringify(art)}`);
  await sleep(500);
  await page.screenshot({path:'qa-heroes.png'});
  await page.close();
  console.log('hero-art',JSON.stringify(art));
}

const combat=await openPage('http://127.0.0.1:4173/?autostart=1&debug-fast=1&map=sunny_meadow&hero=donut_knight',1280,720,null,6000);
for(const key of ['qaShots','qaHits','qaKills']){
  if(!(Number(combat[key])>0)) throw new Error(`Real combat QA failed: ${key}=${combat[key]??'missing'}`);
}
if(!['playing','upgrade','gameover','victory','chest'].includes(combat.gameState)) throw new Error(`Unexpected game state ${combat.gameState}`);

await heroArtQA();

const cases=[
 ['sunny_meadow','donut_knight','qa-sunny-meadow.png'],
 ['candy_park','candy_witch','qa-candy-park.png'],
 ['snow_festival','snow_penguin','qa-snow-festival.png'],
 ['rainbow_canyon','fox_archer','qa-rainbow-canyon.png'],
 ['firefly_swamp','lightning_bee','qa-firefly-swamp.png']
];
for(const [map,hero,file] of cases) await openPage(`http://127.0.0.1:4173/?autostart=1&map=${map}&hero=${hero}`,1280,720,file,4400);
await openPage('http://127.0.0.1:4173/?autostart=1&map=festival_finale&hero=pumpkin_jester',412,915,'qa-mobile-festival.png',4400);

// Capture every hero in live gameplay. Besides firing, each hero must land a
// real hit so a broken muzzle direction / weapon aim can never pass CI again.
const heroIds=['donut_knight','dew_fairy','hamster_pirate','candy_witch','mushroom_druid','cat_mage','toy_robot','baker_alchemist','fox_archer','cloud_princess','watermelon_captain','music_gnome','lightning_bee','snow_penguin','pumpkin_jester'];
for(let i=0;i<heroIds.length;i++){
  const hero=heroIds[i];
  const data=await openPage(`http://127.0.0.1:4173/?autostart=1&debug-fast=1&map=sunny_meadow&hero=${hero}`,960,540,`qa-hero-${String(i+1).padStart(2,'0')}.png`,3200);
  if(!(Number(data.qaShots)>0)) throw new Error(`Hero weapon did not fire: ${hero}`);
  if(!(Number(data.qaHits)>0)) throw new Error(`Hero weapon fired but never hit an enemy: ${hero}`);
}

await browser.close();
console.log('Real-time WebGL combat, all-hero hit/weapon, portrait and visual QA passed.');
