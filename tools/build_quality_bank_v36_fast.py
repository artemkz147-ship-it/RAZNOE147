#!/usr/bin/env python3
import bisect
import gzip
import hashlib
import io
import json
import random
import re
import time
from collections import Counter, defaultdict
from functools import lru_cache
from pathlib import Path

import requests

OUT = Path('quality-bank-v36')
OUT.mkdir(exist_ok=True)

MKQA_URL = 'https://github.com/apple/ml-mkqa/raw/main/dataset/mkqa.jsonl.gz'
KAZ_MAIN = [
    'https://raw.githubusercontent.com/IS2AI/KazQAD/main/data/reading-comprehension/kazqad-reading-comprehension-v1.0-kk-train.jsonl',
    'https://raw.githubusercontent.com/IS2AI/KazQAD/main/data/reading-comprehension/kazqad-reading-comprehension-v1.0-kk-validation.jsonl',
    'https://raw.githubusercontent.com/IS2AI/KazQAD/main/data/reading-comprehension/kazqad-reading-comprehension-v1.0-kk-test.jsonl',
]
KAZ_EXTRA = 'https://raw.githubusercontent.com/IS2AI/KazQAD/main/data/supplementary/nq-translate-kk/nq-reading-comprehension-translate-kk.jsonl.gz'
TARGET = {'en': 6800, 'ru': 6800, 'kk': 7800}

BANNED = {
    'en': ['current president','current prime minister','current ceo','right now','today ','latest ','this year','weather','score of','won last night','when is the next','what time does','release date','how old is','age of ','net worth','price of ','phone number','zip code','population of'],
    'ru': ['нынешний президент','действующий президент','текущий президент','кто сейчас','на данный момент','сегодня ','последние новости','погода','счёт матча','счет матча','когда выйдет следующий','во сколько начинается','сколько лет','возраст ','состояние ','цена ','номер телефона','население '],
    'kk': ['қазіргі президент','қазір кім','бүгін','ауа райы','соңғы жаңалық','келесі қашан','неше жаста','жасы ','бағасы ','телефон нөмірі','халқы '],
}
COMMON_BANNED = ['porn','sexual','onlyfans','suicide method','nude']
BINARY = {'yes','no','true','false','да','нет','верно','неверно','иә','жоқ','дұрыс','қате'}

# Specialised categories first. Any question that does not strongly match a topic stays in facts.
KEYWORDS = {
'en': [
('anime',['anime','manga','naruto','pokemon','one piece','dragon ball','ghibli']),('memes',['meme','viral image']),('games',['video game','playstation','xbox','nintendo','steam','minecraft','fortnite']),('cinema',['film','movie','director','actor','actress','oscar','academy award','box office']),('series',['tv series','television series','episode','season','sitcom','netflix series','hbo series']),('animation',['animated film','animation studio','cartoon','pixar','dreamworks']),('music',['song','singer','band','album','rapper','composer','guitar','piano','music']),('internet',['website','internet','browser','youtube','facebook','twitter','instagram','reddit','domain']),('media',['newspaper','radio','television','tv channel','journalist','magazine']),('generations',['generation z','millennial','baby boomer','gen z']),('brands',['brand','company','manufacturer','headquarters','logo']),('cars',['car model','automobile','ferrari','toyota','bmw','mercedes','ford','tesla']),('transport',['airline','airport','train','railway','ship','aircraft','metro','transport']),('sports',['football','soccer','basketball','tennis','olympic','baseball','hockey','athlete','championship','sport']),('medicine',['medicine','medical','disease','doctor','syndrome','treatment','hospital','vaccine','diagnosis']),('human',['human body','organ','brain','heart','bone','blood','muscle']),('chemistry',['chemical','element','periodic table','molecule','compound','acid','atom']),('physics',['physics','force','energy','velocity','electric','quantum','particle','gravity']),('space',['planet','moon','solar system','spacecraft','nasa','astronaut','galaxy','mars','saturn']),('science',['scientist','science','biology','experiment','discovery','theory']),('animals',['animal','bird','fish','mammal','reptile','insect','species']),('nature',['plant','forest','river','ocean','mountain','climate','tree','flower']),('geography',['capital of','country','city','continent','island','border','located in','geography']),('history',['war','battle','empire','king','queen','revolution','century','ancient','history']),('literature',['book','novel','author','writer','poem','poet','literature']),('art',['painting','painter','artist','museum','sculpture','artwork']),('architecture',['architect','building','tower','cathedral','palace','architecture','bridge']),('mythology',['myth','mythology','god','goddess','zeus','odin','legendary creature']),('food',['food','dish','recipe','cuisine','cheese','fruit','vegetable']),('language',['language','word','means','translation','alphabet','grammar','spoken in']),('inventions',['invented','inventor','patent','invention']),('technology',['computer','software','technology','processor','programming','smartphone','operating system']),('culture',['festival','tradition','religion','culture','holiday','ceremony']),('comics',['comic','superhero','marvel','dc comics','batman','superman','spider-man']),('popscience',['scientific','research','phenomenon','evolution','universe']),('logic',['logic','puzzle','riddle','chess']),
],
'ru': [
('anime',['аниме','манга','наруто','покемон']),('memes',['мем','вирусн']),('games',['видеоигр','playstation','xbox','nintendo','minecraft','разработчик игры']),('cinema',['фильм','кино','режисс','актёр','актер','актрис','оскар']),('series',['сериал','эпизод','сезон','ситком']),('animation',['мультфильм','анимац','pixar','мультсериал']),('music',['песня','певец','певица','группа','альбом','рэпер','композитор','музык']),('internet',['сайт','интернет','браузер','youtube','facebook','twitter','instagram','reddit','домен']),('media',['газета','радио','телевид','телеканал','журналист','журнал']),('generations',['зумер','миллениал','бумер','поколение z']),('brands',['бренд','компания','производител','штаб-квартир','логотип']),('cars',['автомобил','машин','ferrari','toyota','bmw','mercedes','ford','tesla']),('transport',['авиакомпан','аэропорт','поезд','железн','корабл','самолёт','самолет','метро','транспорт']),('sports',['футбол','баскетбол','теннис','олимпи','хоккей','спортсмен','чемпион','спорт']),('medicine',['медицин','болезн','врач','синдром','лечен','больниц','вакцин','диагноз']),('human',['орган','мозг','сердц','кость','кров','мышц','тело человека']),('chemistry',['химичес','элемент','периодичес','молекул','соединен','кислот','атом']),('physics',['физик','сила','энерги','скорост','электр','квант','частиц','гравитац']),('space',['планет','луна','солнечн','космос','nasa','астронавт','галактик','марс','сатурн']),('science',['учён','учен','наук','биолог','эксперимент','открыт','теори']),('animals',['животн','птиц','рыб','млекопита','рептили','насеком']),('nature',['растен','лес','река','океан','гора','климат','дерев','цветок']),('geography',['столиц','страна','город','континент','остров','находится','гранич','географ']),('history',['войн','битв','импери','король','королев','революц','век','истори','древн']),('literature',['книга','роман','автор','писател','стих','поэт','литератур']),('art',['картин','художник','музей','скульптур','искусств']),('architecture',['архитектор','здание','башн','собор','дворец','архитектур','мост']),('mythology',['миф','мифолог','бог ','богин','зевс','один','легендар']),('food',['еда','блюд','рецепт','кухн','сыр','фрукт','овощ']),('language',['язык','слово','означает','перевод','алфавит','граммат']),('inventions',['изобрёл','изобрел','изобрет','патент']),('technology',['компьютер','программ','технолог','процессор','смартфон','операционн']),('culture',['фестивал','традиц','религи','культур','праздник','церемон']),('comics',['комикс','супергер','marvel','dc comics','бэтмен','супермен','человек-паук']),('popscience',['научн','исследован','явлени','эволюц','вселенн']),('logic',['логик','головолом','шахмат','задач']),
],
'kk': [
('anime',['аниме','манга','наруто','покемон']),('memes',['мем','вирус']),('games',['бейне ойын','playstation','xbox','nintendo','minecraft']),('cinema',['фильм','кино','режиссер','актер','актриса','оскар']),('series',['сериал','эпизод','маусым']),('animation',['мультфильм','анимация']),('music',['ән','әнші','альбом','музыка','композитор']),('internet',['сайт','интернет','браузер','youtube','facebook','instagram','домен']),('media',['газет','радио','теледидар','журналист','журнал']),('generations',['зумер','миллениал','буын']),('brands',['бренд','компания','өндіруші','логотип']),('cars',['автомобиль','машина','toyota','bmw','mercedes','ford','tesla']),('transport',['әуежай','пойыз','теміржол','кеме','ұшақ','метро','көлік']),('sports',['футбол','баскетбол','теннис','олимпи','хоккей','чемпион','спорт']),('medicine',['медицина','ауру','дәрігер','синдром','емдеу','вакцина','диагноз']),('human',['адам ағз','ми','жүрек','сүйек','қан','бұлшық']),('chemistry',['химия','элемент','молекула','қышқыл','атом']),('physics',['физика','күш','энергия','жылдамдық','электр','квант','гравитация']),('space',['планета','ай ','күн жүйесі','ғарыш','астронавт','галактика','марс']),('science',['ғалым','ғылым','биология','эксперимент','теория']),('animals',['жануар','құс','балық','сүтқоректі','жәндік']),('nature',['өсімдік','орман','өзен','мұхит','тау','климат','ағаш','гүл']),('geography',['астана','ел ','қала','құрлық','арал','шекара','география']),('history',['соғыс','шайқас','империя','патша','революция','ғасыр','тарих','ежелгі']),('literature',['кітап','роман','автор','жазушы','өлең','ақын','әдебиет']),('art',['картина','суретші','мұражай','мүсін','өнер']),('architecture',['сәулетші','ғимарат','мұнара','сарай','сәулет','көпір']),('mythology',['миф','мифология','құдай','аңыз']),('food',['тағам','ас ','рецепт','асхана','ірімшік','жеміс','көкөніс']),('language',['тіл ','сөз','аудар','әліпби','грамматика']),('inventions',['ойлап тап','өнертабыс','патент']),('technology',['компьютер','бағдарлама','технология','процессор','смартфон','операциялық']),('culture',['фестиваль','дәстүр','дін','мәдениет','мереке','рәсім']),('comics',['комикс','суперқаһарман','marvel','dc comics','бэтмен','супермен']),('popscience',['ғылыми','зерттеу','құбылыс','эволюция','ғалам']),('logic',['логика','жұмбақ','шахмат','есеп']),
]}

@lru_cache(maxsize=200000)
def norm(s):
    s = str(s or '').casefold().replace('ё','е')
    s = re.sub(r'[^\w\d]+', ' ', s, flags=re.UNICODE)
    return ' '.join(s.split())

def http_bytes(url, tries=3):
    last = None
    for i in range(tries):
        try:
            r = requests.get(url, timeout=70, headers={'User-Agent':'Knowdium-quality-bank-fast/3.6'})
            r.raise_for_status()
            return r.content
        except Exception as e:
            last = e; time.sleep(1+i)
    raise last

def polish(q):
    q = re.sub(r'\s+',' ',str(q or '').strip())
    if q and q[0].isalpha(): q = q[0].upper()+q[1:]
    if q and q[-1] not in '?!': q += '?'
    return q

def bad(q,a,lang):
    qn, an = norm(q), norm(a)
    if len(q) < 9 or len(q) > 185 or len(a) < 1 or len(a) > 68: return True
    if an in BINARY: return True
    if len(an) > 3 and an in qn: return True
    if any(x in qn for x in COMMON_BANNED): return True
    if any(norm(x) in qn for x in BANNED[lang]): return True
    if re.search(r'\b(202[5-9]|203\d)\b', qn): return True
    if 'http' in qn or q.count('/') > 3: return True
    if len(set(qn.split())) < 3: return True
    return False

def category_for(q,lang):
    qn = norm(q)
    for cat,words in KEYWORDS[lang]:
        if any(norm(w) in qn for w in words): return cat
    return 'facts'

def pick_answer(items):
    if not isinstance(items,list): return None,None
    for it in items:
        if not isinstance(it,dict): continue
        typ = str(it.get('type') or '')
        text = str(it.get('text') or '').strip()
        if not text or typ in {'unanswerable','long_answer','binary'}: continue
        return text,typ
    return None,None

def load_mkqa():
    raw = http_bytes(MKQA_URL)
    out = {'en':[],'ru':[]}; seen={'en':set(),'ru':set()}
    with gzip.GzipFile(fileobj=io.BytesIO(raw)) as gz:
        for line in gz:
            try: obj=json.loads(line)
            except Exception: continue
            sid=str(obj.get('example_id') or '')
            for lang in ('en','ru'):
                if len(out[lang]) >= TARGET[lang]: continue
                q=polish((obj.get('queries') or {}).get(lang) or '')
                a,typ=pick_answer((obj.get('answers') or {}).get(lang))
                if not a or bad(q,a,lang): continue
                nq=norm(q)
                if nq in seen[lang]: continue
                seen[lang].add(nq)
                out[lang].append({'q':q,'answer':a,'atype':typ or 'entity','category':category_for(q,lang),'source':'MKQA','sourceId':sid or hashlib.sha1(nq.encode()).hexdigest()[:14]})
    return out

def iter_kaz(data,gzipped,source):
    fh = gzip.GzipFile(fileobj=io.BytesIO(data)) if gzipped else io.BytesIO(data)
    for line in fh:
        try: obj=json.loads(line)
        except Exception: continue
        q=polish(obj.get('question') or '')
        answers=obj.get('answers') or {}; texts=answers.get('text') if isinstance(answers,dict) else None
        if not texts: continue
        a=str(texts[0]).strip()
        if bad(q,a,'kk'): continue
        yield {'q':q,'answer':a,'atype':'entity' if len(a.split())<=4 else 'phrase','category':category_for(q,'kk'),'source':source,'sourceId':str(obj.get('id') or hashlib.sha1(norm(q).encode()).hexdigest()[:14])}

def load_kaz():
    rows=[];seen=set()
    for url in KAZ_MAIN:
        for r in iter_kaz(http_bytes(url),False,'KazQAD'):
            nq=norm(r['q'])
            if nq in seen: continue
            seen.add(nq); rows.append(r)
            if len(rows)>=TARGET['kk']: return rows
    if len(rows)<TARGET['kk']:
        for r in iter_kaz(http_bytes(KAZ_EXTRA),True,'KazQAD-NQ'):
            nq=norm(r['q'])
            if nq in seen: continue
            seen.add(nq); rows.append(r)
            if len(rows)>=TARGET['kk']: break
    return rows

def signature(r,lang):
    qn=norm(r['q']); a=str(r['answer']).strip(); an=norm(a)
    if re.fullmatch(r'[+-]?\d+(?:[.,]\d+)?',a): return 'number'
    if re.fullmatch(r'\d{3,4}',a): return 'date'
    when=['when ','what year','когда ','каком году','қашан ','қай жылы']
    where=['where ','which country','which city','где ','какой стране','каком городе','қайда ','қай ел','қай қала']
    who=['who ','whose ','кто ','чей ','кім ','кімнің ']
    if any(x in qn for x in when): return 'date'
    if any(x in qn for x in where): return 'place'
    if any(x in qn for x in who): return 'person'
    if len(a.split())<=4 and any(ch.isupper() for ch in a): return 'entity'
    if len(an.split())<=2: return 'short'
    return 'phrase'

def diff(q,a,cat):
    n=len(q)+len(a)
    if cat in {'physics','chemistry','medicine','popscience'} or n>145: return 'hard'
    if n>95: return 'medium'
    return 'easy'

def stable_rng(seed):
    return random.Random(int(hashlib.sha256(seed.encode()).hexdigest()[:16],16))

def unique_pool(values):
    seen=set(); out=[]
    for x in values:
        nx=norm(x)
        if not nx or nx in seen: continue
        seen.add(nx); out.append(x)
    out.sort(key=lambda x:(len(x),norm(x)))
    return out

def nearby(pool, correct, rng, want=10):
    if not pool: return []
    lengths=[len(x) for x in pool]
    i=bisect.bisect_left(lengths,len(correct))
    lo=max(0,i-28); hi=min(len(pool),i+29)
    chunk=pool[lo:hi]
    rng.shuffle(chunk)
    nc=norm(correct); out=[]; seen={nc}
    for x in chunk:
        nx=norm(x)
        if nx in seen: continue
        ratio=(len(x)+3)/(len(correct)+3)
        if .38 <= ratio <= 2.7:
            seen.add(nx); out.append(x)
            if len(out)>=want: break
    return out

def build_mc(records,lang):
    for r in records: r['sig']=signature(r,lang)
    raw_cat=defaultdict(list); raw_sig=defaultdict(list); raw_all=defaultdict(list)
    for r in records:
        raw_cat[(r['category'],r['sig'])].append(r['answer'])
        raw_sig[r['sig']].append(r['answer'])
        raw_all[r['category']].append(r['answer'])
    by_cat={k:unique_pool(v) for k,v in raw_cat.items()}
    by_sig={k:unique_pool(v) for k,v in raw_sig.items()}
    by_topic={k:unique_pool(v) for k,v in raw_all.items()}
    out=[]; seenq=set()
    for r in records:
        nq=norm(r['q'])
        if nq in seenq: continue
        seenq.add(nq)
        correct=r['answer'].strip(); rng=stable_rng(r['sourceId']+'|'+lang)
        cand=[]; used={norm(correct)}
        for pool in (by_cat.get((r['category'],r['sig']),[]),by_sig.get(r['sig'],[]),by_topic.get(r['category'],[])):
            for x in nearby(pool,correct,rng,16):
                nx=norm(x)
                if nx in used: continue
                used.add(nx); cand.append(x)
                if len(cand)>=9: break
            if len(cand)>=9: break
        if len(cand)<3: continue
        rng.shuffle(cand); answers=[correct]+cand[:3]; rng.shuffle(answers)
        idx=answers.index(correct)
        fam='webq-'+hashlib.sha1((lang+'|'+nq).encode()).hexdigest()[:16]
        out.append({'id':fam+'-'+lang,'family':fam,'rootFamily':fam,'category':r['category'],'q':r['q'],'answers':answers,'correct':idx,'difficulty':diff(r['q'],correct,r['category']),'type':'choice','local':False,'source':r['source']})
    return out

def validate(arr,lang):
    ids=set(); qs=set(); bads=[]
    for i,q in enumerate(arr):
        if q['id'] in ids: bads.append((i,'duplicate id'))
        ids.add(q['id']); nq=norm(q['q'])
        if nq in qs: bads.append((i,'duplicate q'))
        qs.add(nq)
        if len(q['answers'])!=4 or not 0<=q['correct']<4: bads.append((i,'options'))
        if len({norm(x) for x in q['answers']})!=4: bads.append((i,'duplicate option'))
    if bads: raise RuntimeError(f'{lang}: {bads[:10]}')

def main():
    t=time.time(); print('download MKQA',flush=True); mk=load_mkqa(); print({k:len(v) for k,v in mk.items()},flush=True)
    print('download KazQAD',flush=True); kk=load_kaz(); print('kk',len(kk),flush=True)
    banks={'en':build_mc(mk['en'],'en'),'ru':build_mc(mk['ru'],'ru'),'kk':build_mc(kk,'kk')}
    report={}
    for lang,arr in banks.items():
        validate(arr,lang)
        report[lang]={'total':len(arr),'byCategory':dict(sorted(Counter(q['category'] for q in arr).items())),'sources':dict(sorted(Counter(q['source'] for q in arr).items()))}
        print(lang,report[lang]['total'],flush=True)
    report['seconds']=round(time.time()-t,2)
    js='window.QUALITY_BANK_V36='+json.dumps(banks,ensure_ascii=False,separators=(',',':'))+';\n'
    (OUT/'quality_bank_v36.js').write_text(js,encoding='utf-8')
    (OUT/'quality_bank_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'SOURCES.txt').write_text('MKQA: https://github.com/apple/ml-mkqa\nKazQAD: https://github.com/IS2AI/KazQAD\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)

if __name__=='__main__': main()
