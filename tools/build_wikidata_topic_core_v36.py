#!/usr/bin/env python3
import hashlib,json,random,re,time
from collections import Counter
from pathlib import Path
import requests

OUT=Path('quality-bank-v36-wikidata-core');OUT.mkdir(exist_ok=True)
S=requests.Session();S.headers.update({'User-Agent':'YandexMegaQuiz/3.6 topic-builder (GitHub artemkz147-ship-it)'})
API='https://www.wikidata.org/w/api.php';SP='https://query.wikidata.org/sparql';LANGS=('ru','en','kk');TARGET=260
PACKS=[
('literature','novel','P50',('Кто написал «{x}»?','Who wrote “{x}”?','«{x}» шығармасының авторы кім?')),
('art','painting','P170',('Кто создал картину «{x}»?','Who created the painting “{x}”?','«{x}» картинасын кім салған?')),
('series','television series','P170',('Кто является создателем сериала «{x}»?','Who created the TV series “{x}”?','«{x}» телесериалын кім жасаған?')),
('animation','animated film','P57',('Кто режиссировал мультфильм «{x}»?','Who directed the animated film “{x}”?','«{x}» анимациялық фильмінің режиссері кім?')),
('nature','river','P17',('По территории какой страны протекает река «{x}»?','Which country does the river “{x}” flow through?','«{x}» өзені қай елдің аумағымен ағады?')),
('nature','mountain','P17',('В какой стране находится гора «{x}»?','In which country is the mountain “{x}” located?','«{x}» тауы қай елде орналасқан?')),
('language','language','P282',('Какая письменность используется для языка «{x}»?','Which writing system is used for the language “{x}”?','«{x}» тілі үшін қандай жазу жүйесі қолданылады?')),
('culture','World Heritage Site','P17',('В какой стране находится объект Всемирного наследия «{x}»?','In which country is the World Heritage Site “{x}” located?','«{x}» Дүниежүзілік мұра нысаны қай елде орналасқан?')),
('food','dish','P495',('С какой страной происхождения связано блюдо «{x}»?','Which country of origin is associated with the dish “{x}”?','«{x}» тағамының шыққан елі қайсы?')),
('transport','airport','P17',('В какой стране находится аэропорт «{x}»?','In which country is the airport “{x}” located?','«{x}» әуежайы қай елде орналасқан?')),
('games','video game','P178',('Какая студия разработала игру «{x}»?','Which studio developed the video game “{x}”?','«{x}» бейне ойынын қай студия әзірледі?')),
('internet','website','P127',('Кому принадлежит сайт или интернет-сервис «{x}»?','Who owns the website or online service “{x}”?','«{x}» сайты немесе интернет-қызметі кімге тиесілі?')),
('media','newspaper','P17',('С какой страной связана газета «{x}»?','Which country is the newspaper “{x}” associated with?','«{x}» газеті қай елмен байланысты?')),
('animals','taxon','P171',('Какой родительский таксон указан для «{x}»?','What is the parent taxon of “{x}”?','«{x}» үшін қандай ата-аналық таксон көрсетілген?')),
('architecture','building','P84',('Кто является архитектором здания «{x}»?','Who is the architect of the building “{x}”?','«{x}» ғимаратының сәулетшісі кім?')),
('brands','company','P17',('С какой страной связана компания «{x}»?','Which country is the company “{x}” associated with?','«{x}» компаниясы қай елмен байланысты?')),
('cars','automobile model','P176',('Какой производитель выпускает модель автомобиля «{x}»?','Which manufacturer makes the automobile model “{x}”?','«{x}» автомобиль моделін қай өндіруші шығарады?')),
]
TIDX={'ru':0,'en':1,'kk':2}

def norm(x):return re.sub(r'\s+',' ',str(x or '').casefold().replace('ё','е')).strip()
def get(url,p,tries=3,to=45):
 e=None
 for i in range(tries):
  try:
   r=S.get(url,params=p,timeout=to);r.raise_for_status();return r.json()
  except Exception as z:e=z;time.sleep(1+i)
 raise e

def resolve(name):
 d=get(API,{'action':'wbsearchentities','search':name,'language':'en','type':'item','limit':5,'format':'json'})
 for x in d.get('search',[]):
  if norm(x.get('label'))==norm(name):return x['id']
 if d.get('search'):return d['search'][0]['id']
 raise RuntimeError('resolve '+name)

def rows(cid,prop):
 q=f'''SELECT DISTINCT ?item ?answer WHERE {{ ?item wdt:P31 wd:{cid}; wdt:{prop} ?answer; wikibase:sitelinks ?n. FILTER(isIRI(?answer)) FILTER(?n>=5) }} LIMIT 750'''
 d=get(SP,{'query':q,'format':'json'},tries=3,to=45)
 return [(b['item']['value'].rsplit('/',1)[-1],b['answer']['value'].rsplit('/',1)[-1]) for b in d['results']['bindings']]

def labels(ids):
 out={};ids=sorted(set(ids))
 for i in range(0,len(ids),45):
  d=get(API,{'action':'wbgetentities','ids':'|'.join(ids[i:i+45]),'props':'labels','languages':'ru|en|kk','format':'json'},to=30)
  for q,v in d.get('entities',{}).items():
   z=v.get('labels',{});out[q]={l:(z.get(l) or {}).get('value','') for l in LANGS}
 return out

def lab(L,q,lang):
 v=(L.get(q) or {}).get(lang,'').strip()
 if v:return v
 if lang=='kk':return (L.get(q) or {}).get('ru','').strip() or (L.get(q) or {}).get('en','').strip()
 return (L.get(q) or {}).get('en','').strip()

def build_pack(cat,key,pairs,L,templ):
 ans={l:[] for l in LANGS};seen={l:set() for l in LANGS}
 for _,a in pairs:
  for l in LANGS:
   x=lab(L,a,l);nx=norm(x)
   if x and nx not in seen[l]:seen[l].add(nx);ans[l].append(x)
 out={l:[] for l in LANGS};seenitem={l:set() for l in LANGS}
 for item,a in pairs:
  for l in LANGS:
   if len(out[l])>=TARGET:continue
   x=lab(L,item,l);correct=lab(L,a,l)
   if not x or not correct or len(x)>110 or len(correct)>90 or norm(x) in seenitem[l]:continue
   pool=[z for z in ans[l] if norm(z)!=norm(correct)]
   if len(pool)<3:continue
   rng=random.Random(int(hashlib.sha256((key+'|'+item+'|'+l).encode()).hexdigest()[:16],16));rng.shuffle(pool);opts=[correct]+pool[:3];rng.shuffle(opts)
   fam='wdc-'+hashlib.sha1((key+'|'+item+'|'+a).encode()).hexdigest()[:16]
   out[l].append({'id':fam+'-'+l,'family':fam,'rootFamily':fam,'category':cat,'q':templ[TIDX[l]].format(x=x),'answers':opts,'correct':opts.index(correct),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'})
   seenitem[l].add(norm(x))
 return out

def generations():
 q='''SELECT DISTINCT ?item ?dob WHERE { ?item wdt:P31 wd:Q5; wdt:P569 ?dob; wikibase:sitelinks ?n. FILTER(YEAR(?dob)>=1946 && YEAR(?dob)<=2012) FILTER(?n>=60) } LIMIT 700'''
 d=get(SP,{'query':q,'format':'json'},tries=3,to=45);raw=[]
 for b in d['results']['bindings']:
  m=re.match(r'(\d{4})-',b['dob']['value']); item=b['item']['value'].rsplit('/',1)[-1]
  if m:raw.append((item,int(m.group(1))))
 L=labels([x[0] for x in raw]);names={'ru':['бэби-бумеры','поколение X','миллениалы','поколение Z'],'en':['Baby Boomers','Generation X','Millennials','Generation Z'],'kk':['бэби-бумерлер','X буыны','миллениалдар','Z буыны']};ranges=[(1946,1964,0),(1965,1980,1),(1981,1996,2),(1997,2012,3)]
 tm={'ru':'К какому поколению по распространённой классификации относят {x}, родившегося в {y} году?','en':'Under a common generational classification, which generation includes {x}, born in {y}?','kk':'Кең таралған жіктеу бойынша {y} жылы туған {x} қай буынға жатады?'}
 out={l:[] for l in LANGS}
 for item,y in raw:
  idx=next((i for lo,hi,i in ranges if lo<=y<=hi),None)
  if idx is None:continue
  for l in LANGS:
   if len(out[l])>=TARGET:continue
   x=lab(L,item,l)
   if not x:continue
   opts=list(names[l]);rng=random.Random(int(hashlib.sha256((item+'|gen|'+l).encode()).hexdigest()[:16],16));rng.shuffle(opts);corr=names[l][idx]
   fam='wdg-'+hashlib.sha1((item+'|'+str(y)).encode()).hexdigest()[:16]
   out[l].append({'id':fam+'-'+l,'family':fam,'rootFamily':fam,'category':'generations','q':tm[l].format(x=x,y=y),'answers':opts,'correct':opts.index(corr),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'})
 return out

def main():
 start=time.time();bank={l:[] for l in LANGS};fails=[]
 for cat,cname,prop,templ in PACKS:
  try:
   cid=resolve(cname); print(cat,cname,cid,flush=True); ps=rows(cid,prop); L=labels([z for p in ps for z in p]); made=build_pack(cat,cname+'|'+prop,ps,L,templ)
   for l in LANGS:bank[l]+=made[l]
   print(' ',len(ps),{l:len(made[l]) for l in LANGS},flush=True)
  except Exception as e:print(' FAIL',cat,e,flush=True);fails.append([cat,repr(e)])
  time.sleep(.5)
 try:
  g=generations()
  for l in LANGS:bank[l]+=g[l]
  print('generations',{l:len(g[l]) for l in LANGS},flush=True)
 except Exception as e:fails.append(['generations',repr(e)])
 rep={l:{'total':len(bank[l]),'byCategory':dict(Counter(x['category'] for x in bank[l]))} for l in LANGS};rep['failures']=fails;rep['seconds']=round(time.time()-start,2)
 (OUT/'wikidata_core_v36.js').write_text('window.WIKIDATA_CORE_V36='+json.dumps(bank,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf8');(OUT/'report.json').write_text(json.dumps(rep,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'SOURCES.txt').write_text('Wikidata structured data, CC0.\n',encoding='utf8');print(json.dumps(rep,ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':main()
