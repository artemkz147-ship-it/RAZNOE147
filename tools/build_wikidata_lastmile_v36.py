#!/usr/bin/env python3
import hashlib,json,random,re,time
from collections import Counter
from pathlib import Path
import requests
OUT=Path('quality-bank-v36-lastmile');OUT.mkdir(exist_ok=True)
S=requests.Session();S.headers.update({'User-Agent':'YandexMegaQuiz/3.6 last-mile-builder (GitHub artemkz147-ship-it)'})
SP='https://query.wikidata.org/sparql';LANGS=('ru','en','kk');TARGET=245

def norm(x):return re.sub(r'\s+',' ',str(x or '').casefold().replace('ё','е')).strip()
def val(b,k):return (b.get(k) or {}).get('value','').strip()
def get(q,tries=4,to=70):
 e=None
 for i in range(tries):
  try:
   r=S.get(SP,params={'query':q,'format':'json'},timeout=to)
   if r.status_code==429:time.sleep(5+i*4);continue
   r.raise_for_status();return r.json()
  except Exception as z:e=z;time.sleep(2+i*2)
 raise e or RuntimeError('query failed')
def labels(b,p):
 return {'en':val(b,p+'En'),'ru':val(b,p+'Ru') or val(b,p+'En'),'kk':val(b,p+'Kk') or val(b,p+'Ru') or val(b,p+'En')}
def select_labels(subject,answer,where,limit=500):
 q=f'''SELECT DISTINCT ?item ?answer ?itemEn ?itemRu ?itemKk ?ansEn ?ansRu ?ansKk WHERE {{
 {where}
 ?item wikibase:sitelinks ?n. FILTER(?n>=5)
 ?item rdfs:label ?itemEn.FILTER(LANG(?itemEn)="en")
 ?answer rdfs:label ?ansEn.FILTER(LANG(?ansEn)="en")
 OPTIONAL{{?item rdfs:label ?itemRu.FILTER(LANG(?itemRu)="ru")}} OPTIONAL{{?item rdfs:label ?itemKk.FILTER(LANG(?itemKk)="kk")}}
 OPTIONAL{{?answer rdfs:label ?ansRu.FILTER(LANG(?ansRu)="ru")}} OPTIONAL{{?answer rdfs:label ?ansKk.FILTER(LANG(?ansKk)="kk")}}
 }} LIMIT {limit}'''
 d=get(q);out=[]
 for b in d['results']['bindings']:
  out.append((val(b,'item').rsplit('/',1)[-1],val(b,'answer').rsplit('/',1)[-1],labels(b,'item'),labels(b,'ans')))
 return out
def make_relation(cat,key,rows,templates):
 out={l:[] for l in LANGS};idx={'ru':0,'en':1,'kk':2}
 for l in LANGS:
  pool=[];seen=set()
  for _,_,_,a in rows:
   z=a[l];nz=norm(z)
   if z and nz not in seen:seen.add(nz);pool.append(z)
  si=set()
  for item,a,il,al in rows:
   if len(out[l])>=TARGET:break
   x=il[l];c=al[l];nx=norm(x)
   if not x or not c or nx in si or len(x)>115 or len(c)>95:continue
   w=[z for z in pool if norm(z)!=norm(c)]
   if len(w)<3:continue
   rng=random.Random(int(hashlib.sha256((key+'|'+item+'|'+l).encode()).hexdigest()[:16],16));rng.shuffle(w);opts=[c]+w[:3];rng.shuffle(opts)
   fam='wdl-'+hashlib.sha1((key+'|'+item+'|'+a).encode()).hexdigest()[:16]
   out[l].append({'id':fam+'-'+l,'family':fam,'rootFamily':fam,'category':cat,'q':templates[idx[l]].format(x=x),'answers':opts,'correct':opts.index(c),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'});si.add(nx)
 return out
def make_year(cat,key,where,templates):
 q=f'''SELECT DISTINCT ?item ?date ?itemEn ?itemRu ?itemKk WHERE {{ {where} ?item wikibase:sitelinks ?n.FILTER(?n>=4) ?item rdfs:label ?itemEn.FILTER(LANG(?itemEn)="en") OPTIONAL{{?item rdfs:label ?itemRu.FILTER(LANG(?itemRu)="ru")}} OPTIONAL{{?item rdfs:label ?itemKk.FILTER(LANG(?itemKk)="kk")}} }} LIMIT 600'''
 d=get(q);raw=[]
 for b in d['results']['bindings']:
  m=re.match(r'([+-]?\d{1,4})-',val(b,'date'))
  if not m:continue
  y=int(m.group(1))
  if y<1 or y>2024:continue
  raw.append((val(b,'item').rsplit('/',1)[-1],y,labels(b,'item')))
 years=sorted(set(y for _,y,_ in raw));out={l:[] for l in LANGS};idx={'ru':0,'en':1,'kk':2}
 for l in LANGS:
  seen=set()
  for item,y,il in raw:
   if len(out[l])>=TARGET:break
   x=il[l];nx=norm(x)
   if not x or nx in seen:continue
   rng=random.Random(int(hashlib.sha256((key+'|'+item+'|'+l).encode()).hexdigest()[:16],16));near=sorted((z for z in years if z!=y),key=lambda z:abs(z-y))[:40];rng.shuffle(near);opts=[str(y)]+[str(z) for z in near[:3]]
   if len(opts)<4:continue
   rng.shuffle(opts);fam='wdly-'+hashlib.sha1((key+'|'+item+'|'+str(y)).encode()).hexdigest()[:16]
   out[l].append({'id':fam+'-'+l,'family':fam,'rootFamily':fam,'category':cat,'q':templates[idx[l]].format(x=x),'answers':opts,'correct':opts.index(str(y)),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'});seen.add(nx)
 return out
def add(bank,m,label):
 print(label,{l:len(m[l]) for l in LANGS},flush=True)
 for l in LANGS:bank[l]+=m[l]
def main():
 st=time.time();bank={l:[] for l in LANGS};fails=[]
 jobs=[]
 # history: battle dates, first point-in-time then start-time fallback
 try:
  m=make_year('history','battle-year','?item wdt:P31 wd:Q178561; wdt:P585 ?date.',('В каком году произошло сражение «{x}»?','In which year did the battle “{x}” take place?','«{x}» шайқасы қай жылы өтті?'))
  if min(len(m[l]) for l in LANGS)<40:
   m2=make_year('history','battle-start','?item wdt:P31 wd:Q178561; wdt:P580 ?date.',('В каком году началось сражение «{x}»?','In which year did the battle “{x}” begin?','«{x}» шайқасы қай жылы басталды?'))
   for l in LANGS:m[l]+=m2[l]
  add(bank,m,'history')
 except Exception as e:fails.append(['history',repr(e)])
 # sports: notable athletes -> citizenship; same answer type = countries
 try:
  r=select_labels('item','answer','?item wdt:P31 wd:Q5; wdt:P106 wd:Q2066131; wdt:P27 ?answer.',600)
  if len(r)<100:
   # competition -> sport if direct athlete occupation is sparse
   r=select_labels('item','answer','?item wdt:P31 wd:Q13406554; wdt:P641 ?answer.',600)
   t=('К какому виду спорта относится соревнование «{x}»?','Which sport is the competition “{x}” associated with?','«{x}» жарысы қай спорт түріне жатады?')
  else:t=('Какую страну представляет спортсмен «{x}» по данным Wikidata?','Which country is athlete “{x}” associated with by citizenship?','Wikidata дерегі бойынша «{x}» спортшысы қай елмен азаматтығы арқылы байланысты?')
  add(bank,make_relation('sports','sports-last',r,t),'sports')
 except Exception as e:fails.append(['sports',repr(e)])
 # memes: inception/publication years
 try:
  m=make_year('memes','meme-inception','?item wdt:P31 wd:Q2927074; wdt:P571 ?date.',('В каком году появился интернет-мем «{x}»?','In which year did the Internet meme “{x}” originate?','«{x}» интернет-мемі қай жылы пайда болды?'))
  if min(len(m[l]) for l in LANGS)<80:
   m2=make_year('memes','meme-publication','?item wdt:P31 wd:Q2927074; wdt:P577 ?date.',('В каком году был опубликован материал, ставший мемом «{x}»?','In which year was the material behind the meme “{x}” published?','«{x}» меміне негіз болған материал қай жылы жарияланды?'))
   for l in LANGS:m[l]+=m2[l]
  add(bank,m,'memes')
 except Exception as e:fails.append(['memes',repr(e)])
 # popscience: scientists -> citizenship, broad but factually clean
 try:
  r=select_labels('item','answer','?item wdt:P31 wd:Q5; wdt:P106 wd:Q901; wdt:P27 ?answer.',600)
  add(bank,make_relation('popscience','scientist-country',r,('С какой страной связан учёный «{x}» по гражданству?','Which country is scientist “{x}” associated with by citizenship?','«{x}» ғалымы азаматтығы бойынша қай елмен байланысты?')),'popscience')
 except Exception as e:fails.append(['popscience',repr(e)])
 # mythology: mythological characters -> father; fallback mother
 try:
  r=select_labels('item','answer','?item wdt:P31 wd:Q4271324; wdt:P22 ?answer.',500)
  m=make_relation('mythology','myth-father',r,('Кто указан отцом мифологического персонажа «{x}»?','Who is listed as the father of the mythological character “{x}”?','«{x}» мифологиялық кейіпкерінің әкесі кім?'))
  if min(len(m[l]) for l in LANGS)<80:
   r2=select_labels('item','answer','?item wdt:P31 wd:Q4271324; wdt:P25 ?answer.',500);m2=make_relation('mythology','myth-mother',r2,('Кто указан матерью мифологического персонажа «{x}»?','Who is listed as the mother of the mythological character “{x}”?','«{x}» мифологиялық кейіпкерінің анасы кім?'))
   for l in LANGS:m[l]+=m2[l]
  add(bank,m,'mythology')
 except Exception as e:fails.append(['mythology',repr(e)])
 # inventions: direct invention class plus broad P61 fallback; options are always inventors/discoverers
 try:
  r=select_labels('item','answer','?item wdt:P31 wd:Q12579633; wdt:P61 ?answer.',600)
  if len(r)<180:r=select_labels('item','answer','?item wdt:P61 ?answer.',700)
  add(bank,make_relation('inventions','inventor-last',r,('Кто указан изобретателем или первооткрывателем «{x}»?','Who is credited as the inventor or discoverer of “{x}”?','«{x}» үшін өнертапқыш немесе ашушы ретінде кім көрсетілген?')),'inventions')
 except Exception as e:fails.append(['inventions',repr(e)])
 rep={l:{'total':len(bank[l]),'byCategory':dict(Counter(x['category'] for x in bank[l]))} for l in LANGS};rep['failures']=fails;rep['seconds']=round(time.time()-st,2)
 (OUT/'lastmile.js').write_text('window.WIKIDATA_LASTMILE_V36='+json.dumps(bank,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf8');(OUT/'report.json').write_text(json.dumps(rep,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'SOURCES.txt').write_text('Wikidata structured data, CC0.\n',encoding='utf8');print(json.dumps(rep,ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':main()
