#!/usr/bin/env python3
import hashlib,json,random,re,time
from collections import defaultdict,Counter
from pathlib import Path
import requests

OUT=Path('quality-bank-v36-wikidata'); OUT.mkdir(exist_ok=True)
S=requests.Session(); S.headers.update({'User-Agent':'YandexMegaQuiz/3.6 quality-bank-builder (educational quiz; contact via GitHub artemkz147-ship-it)'})
SPARQL='https://query.wikidata.org/sparql'
API='https://www.wikidata.org/w/api.php'
LANGS=('ru','en','kk')
TARGET_PER_CATEGORY=285

PACKS=[
('literature','novel','P50',{'ru':'Кто написал «{item}»?','en':'Who wrote “{item}”?','kk':'«{item}» шығармасының авторы кім?'}),
('art','painting','P170',{'ru':'Кто создал картину «{item}»?','en':'Who created the painting “{item}”?','kk':'«{item}» картинасын кім салған?'}),
('series','television series','P170',{'ru':'Кто является создателем сериала «{item}»?','en':'Who created the TV series “{item}”?','kk':'«{item}» телесериалын кім жасаған?'}),
('animation','animated film','P57',{'ru':'Кто режиссировал мультфильм «{item}»?','en':'Who directed the animated film “{item}”?','kk':'«{item}» анимациялық фильмінің режиссері кім?'}),
('anime','anime','P170',{'ru':'Кто указан создателем аниме «{item}»?','en':'Who is credited as the creator of the anime “{item}”?','kk':'«{item}» анимесінің авторы кім?'}),
('space','astronomical object','P59',{'ru':'В каком созвездии находится объект «{item}»?','en':'In which constellation is “{item}” located?','kk':'«{item}» нысаны қай шоқжұлдызда орналасқан?'}),
('nature','river','P17',{'ru':'По территории какой страны протекает река «{item}»?','en':'Which country does the river “{item}” flow through?','kk':'«{item}» өзені қай елдің аумағымен ағады?'}),
('nature','mountain','P17',{'ru':'В какой стране находится гора «{item}»?','en':'In which country is the mountain “{item}” located?','kk':'«{item}» тауы қай елде орналасқан?'}),
('sports','athlete','P641',{'ru':'Каким видом спорта занимается «{item}»?','en':'Which sport is “{item}” associated with?','kk':'«{item}» қай спорт түрімен айналысады?'}),
('language','language','P282',{'ru':'Какая письменность используется для языка «{item}»?','en':'Which writing system is used for the language “{item}”?','kk':'«{item}» тілі үшін қандай жазу жүйесі қолданылады?'}),
('culture','World Heritage Site','P17',{'ru':'В какой стране находится объект Всемирного наследия «{item}»?','en':'In which country is the World Heritage Site “{item}” located?','kk':'«{item}» Дүниежүзілік мұра нысаны қай елде орналасқан?'}),
('food','dish','P495',{'ru':'С какой страной происхождения связано блюдо «{item}»?','en':'Which country of origin is associated with the dish “{item}”?','kk':'«{item}» тағамының шыққан елі қайсы?'}),
('transport','airport','P17',{'ru':'В какой стране находится аэропорт «{item}»?','en':'In which country is the airport “{item}” located?','kk':'«{item}» әуежайы қай елде орналасқан?'}),
('transport','aircraft','P176',{'ru':'Какая компания производит воздушное судно «{item}»?','en':'Which company manufactures the aircraft “{item}”?','kk':'«{item}» әуе кемесін қай компания өндіреді?'}),
('games','video game','P178',{'ru':'Какая студия разработала игру «{item}»?','en':'Which studio developed the video game “{item}”?','kk':'«{item}» бейне ойынын қай студия әзірледі?'}),
('internet','website','P127',{'ru':'Кому принадлежит сайт или интернет-сервис «{item}»?','en':'Who owns the website or online service “{item}”?','kk':'«{item}» сайты немесе интернет-қызметі кімге тиесілі?'}),
('memes','Internet meme','P170',{'ru':'Кто указан создателем интернет-мема «{item}»?','en':'Who is credited as the creator of the Internet meme “{item}”?','kk':'«{item}» интернет-мемінің авторы кім?'}),
('media','newspaper','P17',{'ru':'С какой страной связана газета «{item}»?','en':'Which country is the newspaper “{item}” associated with?','kk':'«{item}» газеті қай елмен байланысты?'}),
('media','television channel','P17',{'ru':'С какой страной связан телеканал «{item}»?','en':'Which country is the television channel “{item}” associated with?','kk':'«{item}» телеарнасы қай елмен байланысты?'}),
('popscience','scientist','P101',{'ru':'С какой областью исследований связан учёный «{item}»?','en':'Which field of research is the scientist “{item}” associated with?','kk':'«{item}» ғалымы қай зерттеу саласымен байланысты?'}),
('animals','taxon','P171',{'ru':'Какой родительский таксон указан для «{item}»?','en':'What is the parent taxon of “{item}”?','kk':'«{item}» үшін қандай ата-аналық таксон көрсетілген?'}),
('mythology','mythological character','P361',{'ru':'К какой мифологической традиции или циклу относится «{item}»?','en':'Which mythological tradition or cycle is “{item}” part of?','kk':'«{item}» қай мифологиялық дәстүрге немесе циклге жатады?'}),
('architecture','building','P84',{'ru':'Кто является архитектором здания «{item}»?','en':'Who is the architect of the building “{item}”?','kk':'«{item}» ғимаратының сәулетшісі кім?'}),
('inventions','invention','P61',{'ru':'Кто указан изобретателем «{item}»?','en':'Who is credited as the inventor of “{item}”?','kk':'«{item}» өнертабысының авторы кім?'}),
('brands','company','P17',{'ru':'С какой страной связана компания «{item}»?','en':'Which country is the company “{item}” associated with?','kk':'«{item}» компаниясы қай елмен байланысты?'}),
('comics','comics character','P170',{'ru':'Кто создал персонажа комиксов «{item}»?','en':'Who created the comics character “{item}”?','kk':'«{item}» комикс кейіпкерін кім жасаған?'}),
('cars','automobile model','P176',{'ru':'Какой производитель выпускает модель автомобиля «{item}»?','en':'Which manufacturer makes the automobile model “{item}”?','kk':'«{item}» автомобиль моделін қай өндіруші шығарады?'}),
]

# Social-generation questions are derived from Wikidata birth dates of notable people. No duplicated fact, no answer generation.
GEN_RANGES=[(1946,1964,'Baby Boomers','бэби-бумеры','бэби-бумерлер'),(1965,1980,'Generation X','поколение X','X буыны'),(1981,1996,'Millennials','миллениалы','миллениалдар'),(1997,2012,'Generation Z','поколение Z','Z буыны')]

def norm(s): return re.sub(r'\s+',' ',str(s or '').casefold().replace('ё','е')).strip()
def qid(uri): return uri.rsplit('/',1)[-1] if uri else ''

def get_json(url,params,tries=3,timeout=45):
    last=None
    for i in range(tries):
        try:
            r=S.get(url,params=params,timeout=timeout); r.raise_for_status(); return r.json()
        except Exception as e:
            last=e; time.sleep(2+i*2)
    raise last

def resolve(label):
    data=get_json(API,{'action':'wbsearchentities','search':label,'language':'en','type':'item','limit':5,'format':'json'})
    if not data.get('search'): raise RuntimeError('cannot resolve '+label)
    # Prefer exact English label, otherwise top search result.
    for x in data['search']:
        if norm(x.get('label'))==norm(label): return x['id']
    return data['search'][0]['id']

def sparql_rows(class_id,prop,limit=850):
    query=f'''SELECT DISTINCT ?item ?answer ?sitelinks WHERE {{
      ?item wdt:P31/wdt:P279* wd:{class_id}; wdt:{prop} ?answer; wikibase:sitelinks ?sitelinks.
      FILTER(isIRI(?answer))
      FILTER(?sitelinks >= 5)
    }} ORDER BY DESC(?sitelinks) LIMIT {limit}'''
    data=get_json(SPARQL,{'query':query,'format':'json'},tries=4,timeout=70)
    return [(qid(b['item']['value']),qid(b['answer']['value']),int(float(b.get('sitelinks',{}).get('value',0)))) for b in data['results']['bindings']]

def labels_for(ids):
    ids=sorted({x for x in ids if x.startswith('Q')}); out={}
    for i in range(0,len(ids),45):
        chunk=ids[i:i+45]
        data=get_json(API,{'action':'wbgetentities','ids':'|'.join(chunk),'props':'labels','languages':'ru|en|kk','languagefallback':'1','format':'json'},timeout=45)
        for k,v in data.get('entities',{}).items():
            labs=v.get('labels',{})
            out[k]={lang:(labs.get(lang) or {}).get('value','') for lang in LANGS}
    return out

def usable_label(labs,q,lang):
    s=(labs.get(q) or {}).get(lang,'').strip()
    if s: return s
    # Proper names often lack a Kazakh label. For entity names only, fallback keeps the question readable rather than dropping the fact.
    if lang=='kk': return (labs.get(q) or {}).get('ru','').strip() or (labs.get(q) or {}).get('en','').strip()
    return (labs.get(q) or {}).get('en','').strip()

def mc_from_rows(category,pack_key,rows,labs,templates):
    result={lang:[] for lang in LANGS}
    # One answer pool per relation: all wrong options are the same semantic type.
    for lang in LANGS:
        candidates=[]; seenpairs=set()
        for item,ans,sl in rows:
            il=usable_label(labs,item,lang); al=usable_label(labs,ans,lang)
            if not il or not al or len(il)>100 or len(al)>90: continue
            key=(norm(il),norm(al))
            if key in seenpairs: continue
            seenpairs.add(key); candidates.append((item,ans,il,al,sl))
        answer_pool=[]; seenans=set()
        for _,a,_,al,_ in candidates:
            na=norm(al)
            if na not in seenans: seenans.add(na); answer_pool.append(al)
        if len(answer_pool)<4: continue
        for item,ans,il,al,sl in candidates:
            rng=random.Random(int(hashlib.sha256((pack_key+'|'+item+'|'+lang).encode()).hexdigest()[:16],16))
            wrong=[x for x in answer_pool if norm(x)!=norm(al)]
            rng.shuffle(wrong); opts=[al]+wrong[:3]
            if len(opts)<4: continue
            rng.shuffle(opts); correct=opts.index(al)
            qq=templates[lang].format(item=il)
            fam='wd-'+hashlib.sha1((pack_key+'|'+item+'|'+ans).encode()).hexdigest()[:16]
            result[lang].append({'id':fam+'-'+lang,'family':fam,'rootFamily':fam,'category':category,'q':qq,'answers':opts,'correct':correct,'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'})
    return result

def generation_rows():
    query='''SELECT DISTINCT ?item ?dob ?sitelinks WHERE {
      ?item wdt:P31 wd:Q5; wdt:P569 ?dob; wikibase:sitelinks ?sitelinks.
      FILTER(YEAR(?dob)>=1946 && YEAR(?dob)<=2012)
      FILTER(?sitelinks>=40)
    } ORDER BY DESC(?sitelinks) LIMIT 1000'''
    data=get_json(SPARQL,{'query':query,'format':'json'},tries=4,timeout=70)
    rows=[]
    for b in data['results']['bindings']:
        item=qid(b['item']['value']); m=re.match(r'(\d{4})-',b['dob']['value'])
        if item and m: rows.append((item,int(m.group(1)),int(float(b.get('sitelinks',{}).get('value',0)))))
    return rows

def gen_name(year,lang):
    for lo,hi,en,ru,kk in GEN_RANGES:
        if lo<=year<=hi: return {'en':en,'ru':ru,'kk':kk}[lang]
    return None

def add_generations(bank):
    rows=generation_rows(); labs=labels_for([x[0] for x in rows])
    opts_by_lang={
      'en':['Baby Boomers','Generation X','Millennials','Generation Z'],
      'ru':['бэби-бумеры','поколение X','миллениалы','поколение Z'],
      'kk':['бэби-бумерлер','X буыны','миллениалдар','Z буыны']}
    templ={
      'ru':'К какому поколению по распространённой классификации относят человека года рождения {year}: {item}?',
      'en':'Under a common generational classification, which generation includes {item}, born in {year}?',
      'kk':'Кең таралған буындар жіктемесі бойынша {year} жылы туған {item} қай буынға жатады?'}
    for lang in LANGS:
        arr=[]
        for item,year,sl in rows:
            il=usable_label(labs,item,lang); correct=gen_name(year,lang)
            if not il or not correct: continue
            opts=list(opts_by_lang[lang]); rng=random.Random(int(hashlib.sha256((item+'|gen|'+lang).encode()).hexdigest()[:16],16)); rng.shuffle(opts)
            fam='wdgen-'+hashlib.sha1((item+'|'+str(year)).encode()).hexdigest()[:16]
            arr.append({'id':fam+'-'+lang,'family':fam,'rootFamily':fam,'category':'generations','q':templ[lang].format(item=il,year=year),'answers':opts,'correct':opts.index(correct),'difficulty':'medium','type':'choice','local':False,'source':'Wikidata'})
            if len(arr)>=TARGET_PER_CATEGORY: break
        bank[lang].extend(arr)
        print('generations',lang,len(arr),flush=True)

def main():
    started=time.time(); bank={lang:[] for lang in LANGS}; stats={lang:Counter() for lang in LANGS}; failures=[]
    cache={}
    for n,(category,class_label,prop,templates) in enumerate(PACKS,1):
        try:
            cid=cache.get(class_label) or resolve(class_label); cache[class_label]=cid
            print(f'[{n}/{len(PACKS)}] {category} / {class_label} -> {cid} {prop}',flush=True)
            rows=sparql_rows(cid,prop)
            ids=[x for r in rows for x in r[:2]]; labs=labels_for(ids)
            made=mc_from_rows(category,class_label+'|'+prop,rows,labs,templates)
            for lang in LANGS:
                # Multiple packs may feed the same category; only take as many as needed.
                need=max(0,TARGET_PER_CATEGORY-stats[lang][category])
                take=made[lang][:need]
                bank[lang].extend(take); stats[lang][category]+=len(take)
            print('  rows',len(rows),'made', {l:stats[l][category] for l in LANGS},flush=True)
        except Exception as e:
            print('  FAIL',e,flush=True); failures.append({'category':category,'class':class_label,'property':prop,'error':repr(e)})
        time.sleep(1.1)
    try: add_generations(bank)
    except Exception as e: failures.append({'category':'generations','error':repr(e)})
    report={lang:{'total':len(bank[lang]),'byCategory':dict(sorted(Counter(x['category'] for x in bank[lang]).items()))} for lang in LANGS}
    report['failures']=failures; report['seconds']=round(time.time()-started,2)
    (OUT/'wikidata_topic_bank_v36.js').write_text('window.WIKIDATA_TOPIC_BANK_V36='+json.dumps(bank,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf-8')
    (OUT/'wikidata_topic_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'SOURCES.txt').write_text('Wikidata structured data (CC0): https://www.wikidata.org/\nQuestions are generated from distinct factual item-property-answer statements; distractors come from the same property relation.\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)

if __name__=='__main__': main()
