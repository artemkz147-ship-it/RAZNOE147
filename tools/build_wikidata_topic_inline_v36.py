#!/usr/bin/env python3
import hashlib,json,random,re,time
from collections import Counter
from pathlib import Path
import requests
OUT=Path('quality-bank-v36-wikidata-inline');OUT.mkdir(exist_ok=True)
S=requests.Session();S.headers.update({'User-Agent':'YandexMegaQuiz/3.6 inline-topic-builder (GitHub artemkz147-ship-it)'})
API='https://www.wikidata.org/w/api.php';SP='https://query.wikidata.org/sparql';LANGS=('ru','en','kk');TARGET=235
P=[
('literature','novel','P50',('Кто написал «{x}»?','Who wrote “{x}”?','«{x}» шығармасының авторы кім?')),
('art','painting','P170',('Кто создал картину «{x}»?','Who created the painting “{x}”?','«{x}» картинасын кім салған?')),
('series','television series','P170',('Кто является создателем сериала «{x}»?','Who created the TV series “{x}”?','«{x}» телесериалын кім жасаған?')),
('animation','animated film','P57',('Кто режиссировал мультфильм «{x}»?','Who directed the animated film “{x}”?','«{x}» анимациялық фильмінің режиссері кім?')),
('nature','river','P17',('По территории какой страны протекает река «{x}»?','Which country does the river “{x}” flow through?','«{x}» өзені қай елдің аумағымен ағады?')),
('culture','World Heritage Site','P17',('В какой стране находится объект Всемирного наследия «{x}»?','In which country is the World Heritage Site “{x}” located?','«{x}» Дүниежүзілік мұра нысаны қай елде орналасқан?')),
('food','dish','P495',('С какой страной происхождения связано блюдо «{x}»?','Which country of origin is associated with the dish “{x}”?','«{x}» тағамының шыққан елі қайсы?')),
('transport','airport','P17',('В какой стране находится аэропорт «{x}»?','In which country is the airport “{x}” located?','«{x}» әуежайы қай елде орналасқан?')),
('games','video game','P178',('Какая студия разработала игру «{x}»?','Which studio developed the video game “{x}”?','«{x}» бейне ойынын қай студия әзірледі?')),
('media','newspaper','P17',('С какой страной связана газета «{x}»?','Which country is the newspaper “{x}” associated with?','«{x}» газеті қай елмен байланысты?')),
('animals','taxon','P171',('Какой родительский таксон указан для «{x}»?','What is the parent taxon of “{x}”?','«{x}» үшін қандай ата-аналық таксон көрсетілген?')),
('architecture','building','P84',('Кто является архитектором здания «{x}»?','Who is the architect of the building “{x}”?','«{x}» ғимаратының сәулетшісі кім?')),
('brands','company','P17',('С какой страной связана компания «{x}»?','Which country is the company “{x}” associated with?','«{x}» компаниясы қай елмен байланысты?')),
('cars','automobile model','P176',('Какой производитель выпускает модель автомобиля «{x}»?','Which manufacturer makes the automobile model “{x}”?','«{x}» автомобиль моделін қай өндіруші шығарады?')),
]
IDX={'ru':0,'en':1,'kk':2}
def norm(x):return re.sub(r'\s+',' ',str(x or '').casefold().replace('ё','е')).strip()
def get(url,p,tries=3,to=60):
 e=None
 for i in range(tries):
  try:r=S.get(url,params=p,timeout=to);r.raise_for_status();return r.json()
  except Exception as z:e=z;time.sleep(2+i)
 raise e
def resolve(x):
 d=get(API,{'action':'wbsearchentities','search':x,'language':'en','type':'item','limit':5,'format':'json'},to=30)
 for z in d.get('search',[]):
  if norm(z.get('label'))==norm(x):return z['id']
 return d['search'][0]['id']
def val(b,k):return (b.get(k) or {}).get('value','').strip()
def query(cid,prop):
 q=f'''SELECT DISTINCT ?item ?answer ?itemEn ?itemRu ?itemKk ?ansEn ?ansRu ?ansKk WHERE {{
 ?item wdt:P31 wd:{cid}; wdt:{prop} ?answer.
 ?article schema:about ?item; schema:isPartOf <https://en.wikipedia.org/>.
 ?item rdfs:label ?itemEn. FILTER(LANG(?itemEn)="en")
 ?answer rdfs:label ?ansEn. FILTER(LANG(?ansEn)="en")
 OPTIONAL {{ ?item rdfs:label ?itemRu. FILTER(LANG(?itemRu)="ru") }}
 OPTIONAL {{ ?item rdfs:label ?itemKk. FILTER(LANG(?itemKk)="kk") }}
 OPTIONAL {{ ?answer rdfs:label ?ansRu. FILTER(LANG(?ansRu)="ru") }}
 OPTIONAL {{ ?answer rdfs:label ?ansKk. FILTER(LANG(?ansKk)="kk") }}
 }} LIMIT 420'''
 d=get(SP,{'query':q,'format':'json'},tries=3,to=65)
 out=[]
 for b in d['results']['bindings']:
  item=val(b,'item').rsplit('/',1)[-1];ans=val(b,'answer').rsplit('/',1)[-1]
  labs={'en':val(b,'itemEn'),'ru':val(b,'itemRu') or val(b,'itemEn'),'kk':val(b,'itemKk') or val(b,'itemRu') or val(b,'itemEn')}
  als={'en':val(b,'ansEn'),'ru':val(b,'ansRu') or val(b,'ansEn'),'kk':val(b,'ansKk') or val(b,'ansRu') or val(b,'ansEn')}
  out.append((item,ans,labs,als))
 return out
def make(cat,key,rows,t):
 res={l:[] for l in LANGS}
 for l in LANGS:
  ap=[];sa=set()
  for _,_,_,a in rows:
   x=a[l];nx=norm(x)
   if x and nx not in sa:sa.add(nx);ap.append(x)
  si=set()
  for item,ans,il,al in rows:
   if len(res[l])>=TARGET:break
   x=il[l];c=al[l];nx=norm(x)
   if not x or not c or nx in si or len(x)>105 or len(c)>90:continue
   wrong=[z for z in ap if norm(z)!=norm(c)]
   if len(wrong)<3:continue
   rng=random.Random(int(hashlib.sha256((key+'|'+item+'|'+l).encode()).hexdigest()[:16],16));rng.shuffle(wrong);opts=[c]+wrong[:3];rng.shuffle(opts)
   fam='wdi-'+hashlib.sha1((key+'|'+item+'|'+ans).encode()).hexdigest()[:16]
   res[l].append({'id':fam+'-'+l,'family':fam,'rootFamily':fam,'category':cat,'q':t[IDX[l]].format(x=x),'answers':opts,'correct':opts.index(c),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'});si.add(nx)
 return res
def generations():
 q='''SELECT DISTINCT ?item ?dob ?en ?ru ?kk WHERE { ?item wdt:P31 wd:Q5; wdt:P569 ?dob. ?a schema:about ?item; schema:isPartOf <https://en.wikipedia.org/>. ?item rdfs:label ?en. FILTER(LANG(?en)="en") OPTIONAL{?item rdfs:label ?ru.FILTER(LANG(?ru)="ru")} OPTIONAL{?item rdfs:label ?kk.FILTER(LANG(?kk)="kk")} FILTER(YEAR(?dob)>=1946&&YEAR(?dob)<=2012) } LIMIT 500'''
 d=get(SP,{'query':q,'format':'json'},to=65);names={'ru':['бэби-бумеры','поколение X','миллениалы','поколение Z'],'en':['Baby Boomers','Generation X','Millennials','Generation Z'],'kk':['бэби-бумерлер','X буыны','миллениалдар','Z буыны']};ranges=[(1946,1964,0),(1965,1980,1),(1981,1996,2),(1997,2012,3)];tm={'ru':'К какому поколению по распространённой классификации относят {x}, родившегося в {y} году?','en':'Under a common generational classification, which generation includes {x}, born in {y}?','kk':'Кең таралған жіктеу бойынша {y} жылы туған {x} қай буынға жатады?'};out={l:[] for l in LANGS}
 for b in d['results']['bindings']:
  y=int(val(b,'dob')[:4]);idx=next((z for lo,hi,z in ranges if lo<=y<=hi),None);item=val(b,'item').rsplit('/',1)[-1]
  if idx is None:continue
  labs={'en':val(b,'en'),'ru':val(b,'ru') or val(b,'en'),'kk':val(b,'kk') or val(b,'ru') or val(b,'en')}
  for l in LANGS:
   if len(out[l])>=TARGET:continue
   x=labs[l];opts=list(names[l]);rng=random.Random(int(hashlib.sha256((item+l).encode()).hexdigest()[:16],16));rng.shuffle(opts);c=names[l][idx];fam='wdigen-'+hashlib.sha1((item+str(y)).encode()).hexdigest()[:16];out[l].append({'id':fam+'-'+l,'family':fam,'rootFamily':fam,'category':'generations','q':tm[l].format(x=x,y=y),'answers':opts,'correct':opts.index(c),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'})
 return out
def main():
 st=time.time();bank={l:[] for l in LANGS};fails=[]
 for cat,cl,pr,t in P:
  try:
   cid=resolve(cl);r=query(cid,pr);m=make(cat,cl+'|'+pr,r,t);print(cat,cid,len(r),{l:len(m[l]) for l in LANGS},flush=True)
   for l in LANGS:bank[l]+=m[l]
  except Exception as e:print('FAIL',cat,e,flush=True);fails.append([cat,repr(e)])
  time.sleep(.4)
 try:
  g=generations();print('generations',{l:len(g[l]) for l in LANGS},flush=True)
  for l in LANGS:bank[l]+=g[l]
 except Exception as e:fails.append(['generations',repr(e)])
 rep={l:{'total':len(bank[l]),'byCategory':dict(Counter(x['category'] for x in bank[l]))} for l in LANGS};rep['failures']=fails;rep['seconds']=round(time.time()-st,2);(OUT/'bank.js').write_text('window.WIKIDATA_INLINE_V36='+json.dumps(bank,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf8');(OUT/'report.json').write_text(json.dumps(rep,ensure_ascii=False,indent=2),encoding='utf8');(OUT/'SOURCES.txt').write_text('Wikidata structured data, CC0.\n',encoding='utf8');print(json.dumps(rep,ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':main()
