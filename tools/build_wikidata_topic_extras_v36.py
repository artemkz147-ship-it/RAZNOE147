#!/usr/bin/env python3
import hashlib,json,random,re,time
from collections import Counter
from pathlib import Path
import requests
OUT=Path('quality-bank-v36-wikidata-extras');OUT.mkdir(exist_ok=True)
S=requests.Session();S.headers.update({'User-Agent':'YandexMegaQuiz/3.6 sparse-topic-builder (GitHub artemkz147-ship-it)'})
API='https://www.wikidata.org/w/api.php';SP='https://query.wikidata.org/sparql';LANGS=('ru','en','kk');TARGET=245
IDX={'ru':0,'en':1,'kk':2}
# class can be a Q id or a search label. mode=instance means P31 class; occupation means P31 human + P106 class.
PACKS=[
('geography','Q515','P17','instance',('В какой стране находится город «{x}»?','In which country is the city “{x}” located?','«{x}» қаласы қай елде орналасқан?')),
('literature','literary work','P50','instance',('Кто написал произведение «{x}»?','Who wrote the literary work “{x}”?','«{x}» әдеби шығармасының авторы кім?')),
('music','Q7366','P175','instance',('Кто исполняет песню «{x}»?','Who performs the song “{x}”?','«{x}» әнін кім орындайды?')),
('cinema','Q11424','P57','instance',('Кто режиссировал фильм «{x}»?','Who directed the film “{x}”?','«{x}» фильмінің режиссері кім?')),
('history','battle','P17','instance',('В какой стране или на территории какой страны произошло сражение «{x}»?','In which country did the battle “{x}” take place?','«{x}» шайқасы қай елдің аумағында өтті?')),
('space','Q523','P59','instance',('В каком созвездии находится звезда «{x}»?','In which constellation is the star “{x}” located?','«{x}» жұлдызы қай шоқжұлдызда орналасқан?')),
('language','language','P282','instance',('Какая письменность используется для языка «{x}»?','Which writing system is used for the language “{x}”?','«{x}» тілі үшін қандай жазу жүйесі қолданылады?')),
('culture','museum','P17','instance',('В какой стране находится музей «{x}»?','In which country is the museum “{x}” located?','«{x}» мұражайы қай елде орналасқан?')),
('anime','anime television series','P57','instance',('Кто режиссировал аниме-сериал «{x}»?','Who directed the anime TV series “{x}”?','«{x}» аниме-сериалының режиссері кім?')),
('internet','website','P127','instance',('Кому принадлежит сайт или интернет-сервис «{x}»?','Who owns the website or online service “{x}”?','«{x}» сайты немесе интернет-қызметі кімге тиесілі?')),
('memes','Internet meme','P170','instance',('Кто указан создателем интернет-мема «{x}»?','Who is credited as the creator of the Internet meme “{x}”?','«{x}» интернет-мемінің авторы кім?')),
('popscience','scientist','P101','occupation',('С какой областью исследований связан учёный «{x}»?','Which field of research is the scientist “{x}” associated with?','«{x}» ғалымы қай зерттеу саласымен байланысты?')),
('animals','Q16521','P171','instance',('Какой родительский таксон указан для «{x}»?','What is the parent taxon of “{x}”?','«{x}» үшін қандай ата-аналық таксон көрсетілген?')),
('mythology','mythological character','P361','instance',('К какой мифологической традиции или циклу относится «{x}»?','Which mythological tradition or cycle is “{x}” part of?','«{x}» қай мифологиялық дәстүрге немесе циклге жатады?')),
('architecture','Q41176','P84','instance',('Кто является архитектором здания «{x}»?','Who is the architect of the building “{x}”?','«{x}» ғимаратының сәулетшісі кім?')),
('inventions','invention','P61','instance',('Кто указан изобретателем «{x}»?','Who is credited as the inventor of “{x}”?','«{x}» өнертабысының авторы кім?')),
('comics','comics character','P170','instance',('Кто создал персонажа комиксов «{x}»?','Who created the comics character “{x}”?','«{x}» комикс кейіпкерін кім жасаған?')),
]
def norm(x):return re.sub(r'\s+',' ',str(x or '').casefold().replace('ё','е')).strip()
def get(url,p,tries=5,to=60):
 e=None
 for i in range(tries):
  try:
   r=S.get(url,params=p,timeout=to)
   if r.status_code==429:time.sleep(4+i*3);continue
   r.raise_for_status();return r.json()
  except Exception as z:e=z;time.sleep(2+i*2)
 raise e or RuntimeError('request failed')
def resolve(x):
 if x.startswith('Q'):return x
 d=get(API,{'action':'wbsearchentities','search':x,'language':'en','type':'item','limit':5,'format':'json'},to=30)
 for z in d.get('search',[]):
  if norm(z.get('label'))==norm(x):return z['id']
 if d.get('search'):return d['search'][0]['id']
 raise RuntimeError('resolve '+x)
def val(b,k):return (b.get(k) or {}).get('value','').strip()
def query(cid,prop,mode):
 subject=(f'?item wdt:P31 wd:Q5; wdt:P106 wd:{cid}; wdt:{prop} ?answer.' if mode=='occupation' else f'?item wdt:P31 wd:{cid}; wdt:{prop} ?answer.')
 q=f'''SELECT DISTINCT ?item ?answer ?itemEn ?itemRu ?itemKk ?ansEn ?ansRu ?ansKk WHERE {{
 {subject}
 ?item wikibase:sitelinks ?n. FILTER(?n>=5)
 ?item rdfs:label ?itemEn. FILTER(LANG(?itemEn)="en")
 ?answer rdfs:label ?ansEn. FILTER(LANG(?ansEn)="en")
 OPTIONAL{{?item rdfs:label ?itemRu.FILTER(LANG(?itemRu)="ru")}} OPTIONAL{{?item rdfs:label ?itemKk.FILTER(LANG(?itemKk)="kk")}}
 OPTIONAL{{?answer rdfs:label ?ansRu.FILTER(LANG(?ansRu)="ru")}} OPTIONAL{{?answer rdfs:label ?ansKk.FILTER(LANG(?ansKk)="kk")}}
 }} LIMIT 460'''
 d=get(SP,{'query':q,'format':'json'},tries=3,to=70);out=[]
 for b in d['results']['bindings']:
  item=val(b,'item').rsplit('/',1)[-1];a=val(b,'answer').rsplit('/',1)[-1]
  il={'en':val(b,'itemEn'),'ru':val(b,'itemRu') or val(b,'itemEn'),'kk':val(b,'itemKk') or val(b,'itemRu') or val(b,'itemEn')};al={'en':val(b,'ansEn'),'ru':val(b,'ansRu') or val(b,'ansEn'),'kk':val(b,'ansKk') or val(b,'ansRu') or val(b,'ansEn')};out.append((item,a,il,al))
 return out
def make(cat,key,rows,t):
 out={l:[] for l in LANGS}
 for l in LANGS:
  pool=[];sp=set()
  for _,_,_,a in rows:
   z=a[l];nz=norm(z)
   if z and nz not in sp:sp.add(nz);pool.append(z)
  si=set()
  for item,a,il,al in rows:
   if len(out[l])>=TARGET:break
   x=il[l];c=al[l];nx=norm(x)
   if not x or not c or nx in si or len(x)>110 or len(c)>95:continue
   w=[z for z in pool if norm(z)!=norm(c)]
   if len(w)<3:continue
   rng=random.Random(int(hashlib.sha256((key+'|'+item+'|'+l).encode()).hexdigest()[:16],16));rng.shuffle(w);opts=[c]+w[:3];rng.shuffle(opts);fam='wdx-'+hashlib.sha1((key+'|'+item+'|'+a).encode()).hexdigest()[:16]
   out[l].append({'id':fam+'-'+l,'family':fam,'rootFamily':fam,'category':cat,'q':t[IDX[l]].format(x=x),'answers':opts,'correct':opts.index(c),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'});si.add(nx)
 return out
def sports():
 q='''SELECT DISTINCT ?item ?answer ?itemEn ?itemRu ?itemKk ?ansEn ?ansRu ?ansKk WHERE { ?item wdt:P31 wd:Q5; wdt:P641 ?answer; wikibase:sitelinks ?n. FILTER(?n>=10) ?item rdfs:label ?itemEn.FILTER(LANG(?itemEn)="en") ?answer rdfs:label ?ansEn.FILTER(LANG(?ansEn)="en") OPTIONAL{?item rdfs:label ?itemRu.FILTER(LANG(?itemRu)="ru")} OPTIONAL{?item rdfs:label ?itemKk.FILTER(LANG(?itemKk)="kk")} OPTIONAL{?answer rdfs:label ?ansRu.FILTER(LANG(?ansRu)="ru")} OPTIONAL{?answer rdfs:label ?ansKk.FILTER(LANG(?ansKk)="kk")} } LIMIT 500'''
 d=get(SP,{'query':q,'format':'json'},to=70);rows=[]
 for b in d['results']['bindings']:
  il={'en':val(b,'itemEn'),'ru':val(b,'itemRu') or val(b,'itemEn'),'kk':val(b,'itemKk') or val(b,'itemRu') or val(b,'itemEn')};al={'en':val(b,'ansEn'),'ru':val(b,'ansRu') or val(b,'ansEn'),'kk':val(b,'ansKk') or val(b,'ansRu') or val(b,'ansEn')};rows.append((val(b,'item').rsplit('/',1)[-1],val(b,'answer').rsplit('/',1)[-1],il,al))
 return make('sports','human|P641',rows,('Каким видом спорта занимается «{x}»?','Which sport is “{x}” associated with?','«{x}» қай спорт түрімен айналысады?'))
def main():
 st=time.time();bank={l:[] for l in LANGS};fails=[]
 try:
  m=sports();print('sports',{l:len(m[l]) for l in LANGS},flush=True)
  for l in LANGS:bank[l]+=m[l]
 except Exception as e:fails.append(['sports',repr(e)])
 for cat,cl,p,mode,t in PACKS:
  try:
   cid=resolve(cl);r=query(cid,p,mode);m=make(cat,cl+'|'+p,r,t);print(cat,cid,len(r),{l:len(m[l]) for l in LANGS},flush=True)
   for l in LANGS:bank[l]+=m[l]
  except Exception as e:print('FAIL',cat,e,flush=True);fails.append([cat,repr(e)])
  time.sleep(1.2)
 rep={l:{'total':len(bank[l]),'byCategory':dict(Counter(x['category'] for x in bank[l]))} for l in LANGS};rep['failures']=fails;rep['seconds']=round(time.time()-st,2);(OUT/'extras.js').write_text('window.WIKIDATA_EXTRAS_V36='+json.dumps(bank,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf8');(OUT/'report.json').write_text(json.dumps(rep,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'SOURCES.txt').write_text('Wikidata structured data, CC0.\n',encoding='utf8');print(json.dumps(rep,ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':main()
