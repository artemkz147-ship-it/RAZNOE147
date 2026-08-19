#!/usr/bin/env python3
import hashlib,json,random,re,time
from pathlib import Path
import requests
OUT=Path('quality-bank-v36-memes');OUT.mkdir(exist_ok=True)
S=requests.Session();S.headers.update({'User-Agent':'YandexMegaQuiz/3.6 meme-subject-builder (GitHub artemkz147-ship-it)'})
SP='https://query.wikidata.org/sparql';LANGS=('ru','en','kk');TARGET=90
BANNED=['porn','sexual','sex ','nude','nudity','порн','секс','обнаж','эрот','жыныстық']
def norm(x):return re.sub(r'\s+',' ',str(x or '').casefold().replace('ё','е')).strip()
def val(b,k):return (b.get(k) or {}).get('value','').strip()
def main():
 q='''SELECT DISTINCT ?item ?answer ?itemEn ?itemRu ?itemKk ?ansEn ?ansRu ?ansKk WHERE {
   ?item wdt:P31 wd:Q2927074; wdt:P921 ?answer; wikibase:sitelinks ?n. FILTER(?n>=1)
   ?item rdfs:label ?itemEn.FILTER(LANG(?itemEn)="en") ?answer rdfs:label ?ansEn.FILTER(LANG(?ansEn)="en")
   OPTIONAL{?item rdfs:label ?itemRu.FILTER(LANG(?itemRu)="ru")} OPTIONAL{?item rdfs:label ?itemKk.FILTER(LANG(?itemKk)="kk")}
   OPTIONAL{?answer rdfs:label ?ansRu.FILTER(LANG(?ansRu)="ru")} OPTIONAL{?answer rdfs:label ?ansKk.FILTER(LANG(?ansKk)="kk")}
 } LIMIT 300'''
 r=S.get(SP,params={'query':q,'format':'json'},timeout=70);r.raise_for_status();rows=[]
 for b in r.json()['results']['bindings']:
  il={'en':val(b,'itemEn'),'ru':val(b,'itemRu') or val(b,'itemEn'),'kk':val(b,'itemKk') or val(b,'itemRu') or val(b,'itemEn')};al={'en':val(b,'ansEn'),'ru':val(b,'ansRu') or val(b,'ansEn'),'kk':val(b,'ansKk') or val(b,'ansRu') or val(b,'ansEn')}
  if any(z in norm(' '.join(il.values())+' '+ ' '.join(al.values())) for z in BANNED):continue
  rows.append((val(b,'item').rsplit('/',1)[-1],val(b,'answer').rsplit('/',1)[-1],il,al))
 bank={l:[] for l in LANGS};T={'ru':'Какова основная тема интернет-мема «{x}»?','en':'What is the main subject of the Internet meme “{x}”?','kk':'«{x}» интернет-мемінің негізгі тақырыбы қандай?'}
 for l in LANGS:
  pool=[];seen=set()
  for _,_,_,a in rows:
   z=a[l];nz=norm(z)
   if z and nz not in seen:seen.add(nz);pool.append(z)
  si=set()
  for item,a,il,al in rows:
   if len(bank[l])>=TARGET:break
   x=il[l];c=al[l];nx=norm(x)
   if not x or not c or nx in si or len(x)>100 or len(c)>80:continue
   wrong=[z for z in pool if norm(z)!=norm(c)]
   if len(wrong)<3:continue
   rng=random.Random(int(hashlib.sha256((item+'|meme-subject|'+l).encode()).hexdigest()[:16],16));rng.shuffle(wrong);opts=[c]+wrong[:3];rng.shuffle(opts);fam='wdms-'+hashlib.sha1((item+'|'+a).encode()).hexdigest()[:16]
   bank[l].append({'id':fam+'-'+l,'family':fam,'rootFamily':fam,'category':'memes','q':T[l].format(x=x),'answers':opts,'correct':opts.index(c),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'});si.add(nx)
 rep={l:len(bank[l]) for l in LANGS};(OUT/'memes.js').write_text('window.WIKIDATA_MEMES_V36='+json.dumps(bank,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf8');(OUT/'report.json').write_text(json.dumps(rep,ensure_ascii=False,indent=2),encoding='utf8');print(rep)
if __name__=='__main__':main()
