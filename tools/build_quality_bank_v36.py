#!/usr/bin/env python3
import gzip
import hashlib
import io
import json
import random
import re
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path

import requests

OUT = Path("quality-bank-v36")
OUT.mkdir(exist_ok=True)

MKQA_URL = "https://github.com/apple/ml-mkqa/raw/main/dataset/mkqa.jsonl.gz"
KAZ_MAIN = [
    "https://raw.githubusercontent.com/IS2AI/KazQAD/main/data/reading-comprehension/kazqad-reading-comprehension-v1.0-kk-train.jsonl",
    "https://raw.githubusercontent.com/IS2AI/KazQAD/main/data/reading-comprehension/kazqad-reading-comprehension-v1.0-kk-validation.jsonl",
    "https://raw.githubusercontent.com/IS2AI/KazQAD/main/data/reading-comprehension/kazqad-reading-comprehension-v1.0-kk-test.jsonl",
]
KAZ_EXTRA = "https://raw.githubusercontent.com/IS2AI/KazQAD/main/data/supplementary/nq-translate-kk/nq-reading-comprehension-translate-kk.jsonl.gz"

TARGET_MKQA = 6900
TARGET_KK = 7600

BANNED_COMMON = [
    "porn", "sexual", "sex position", "nude", "onlyfans", "suicide method",
]
BANNED_EN = [
    "current president", "current prime minister", "current ceo", "right now", "today's", "today ",
    "latest ", "this year", "weather", "score of", "who won last night", "who is the president",
    "when is the next", "what time does", "release date for", "release date of the next",
]
BANNED_RU = [
    "нынешний президент", "действующий президент", "текущий президент", "кто сейчас", "на данный момент",
    "сегодня ", "последние новости", "погода", "счёт матча", "когда выйдет следующий", "во сколько начинается",
]
BANNED_KK = [
    "қазіргі президент", "қазір кім", "бүгін", "ауа райы", "соңғы жаңалық", "келесі қашан",
]

# Keyword order matters: specialised topics first, broad facts last.
KEYWORDS = {
    "en": [
        ("anime", ["anime", "manga", "naruto", "pokemon", "one piece", "dragon ball", "studio ghibli"]),
        ("memes", ["meme", "viral image", "internet meme"]),
        ("games", ["video game", "playstation", "xbox", "nintendo", "steam", "minecraft", "fortnite", "game developer"]),
        ("cinema", ["film", "movie", "director", "actor", "actress", "oscar", "academy award", "box office"]),
        ("series", ["tv series", "television series", "episode", "season", "sitcom", "netflix series", "hbo series"]),
        ("animation", ["animated", "cartoon", "pixar", "disney animation", "dreamworks animation"]),
        ("music", ["song", "singer", "band", "album", "music", "rapper", "composer", "guitar", "piano"]),
        ("internet", ["website", "internet", "browser", "google", "youtube", "facebook", "twitter", "instagram", "reddit", "domain"]),
        ("media", ["newspaper", "radio", "television", "tv channel", "journalist", "news network", "magazine"]),
        ("brands", ["brand", "company", "manufacturer", "founded", "headquarters", "logo"]),
        ("cars", ["car", "automobile", "vehicle model", "ferrari", "toyota", "bmw", "mercedes", "ford", "tesla"]),
        ("transport", ["airline", "airport", "train", "railway", "ship", "aircraft", "metro", "transport"]),
        ("sports", ["football", "soccer", "basketball", "tennis", "olympic", "sport", "baseball", "hockey", "athlete", "championship"]),
        ("medicine", ["medicine", "medical", "disease", "doctor", "syndrome", "treatment", "hospital", "anatomy", "vaccine"]),
        ("human", ["human body", "organ", "brain", "heart", "bone", "blood", "muscle"]),
        ("chemistry", ["chemical", "element", "periodic table", "molecule", "compound", "acid", "atom"]),
        ("physics", ["physics", "force", "energy", "velocity", "electric", "quantum", "particle", "gravity"]),
        ("space", ["planet", "moon", "solar system", "space", "nasa", "astronaut", "galaxy", "star", "mars"]),
        ("science", ["scientist", "science", "biology", "experiment", "discovery", "theory"]),
        ("animals", ["animal", "bird", "fish", "mammal", "reptile", "insect", "species"]),
        ("nature", ["plant", "forest", "river", "ocean", "mountain", "climate", "tree", "flower"]),
        ("geography", ["capital", "country", "city", "state", "continent", "island", "located", "border", "population"]),
        ("history", ["war", "battle", "empire", "king", "queen", "president", "revolution", "century", "history", "ancient"]),
        ("literature", ["book", "novel", "author", "writer", "poem", "poet", "literature", "character in"]),
        ("art", ["painting", "painter", "artist", "museum", "sculpture", "artwork"]),
        ("architecture", ["architect", "building", "tower", "cathedral", "palace", "architecture", "bridge"]),
        ("mythology", ["myth", "mythology", "god", "goddess", "zeus", "odin", "legendary creature"]),
        ("food", ["food", "dish", "recipe", "cuisine", "cheese", "wine", "fruit", "vegetable", "restaurant"]),
        ("language", ["language", "word", "means", "translation", "alphabet", "grammar", "spoken in"]),
        ("inventions", ["invented", "inventor", "patent", "invention"]),
        ("technology", ["computer", "software", "technology", "processor", "programming", "smartphone", "operating system"]),
        ("culture", ["festival", "tradition", "religion", "culture", "holiday", "ceremony"]),
        ("comics", ["comic", "superhero", "marvel", "dc comics", "batman", "superman", "spider-man"]),
        ("popscience", ["scientific", "research", "phenomenon", "evolution", "universe"]),
    ],
    "ru": [
        ("anime", ["аниме", "манга", "наруто", "покемон", "ван пис"]),
        ("memes", ["мем", "вирусн"]),
        ("games", ["видеоигр", "игра ", "playstation", "xbox", "nintendo", "minecraft", "разработчик игры"]),
        ("cinema", ["фильм", "кино", "режисс", "актёр", "актер", "актрис", "оскар"]),
        ("series", ["сериал", "эпизод", "сезон", "ситком"]),
        ("animation", ["мультфильм", "анимац", "pixar", "мультсериал"]),
        ("music", ["песня", "певец", "певица", "группа", "альбом", "музык", "рэпер", "композитор"]),
        ("internet", ["сайт", "интернет", "браузер", "youtube", "facebook", "twitter", "instagram", "reddit", "домен"]),
        ("media", ["газета", "радио", "телевид", "телеканал", "журналист", "журнал"]),
        ("brands", ["бренд", "компания", "производител", "штаб-квартир", "логотип"]),
        ("cars", ["автомобил", "машин", "ferrari", "toyota", "bmw", "mercedes", "ford", "tesla"]),
        ("transport", ["авиакомпан", "аэропорт", "поезд", "железн", "корабл", "самолёт", "самолет", "метро", "транспорт"]),
        ("sports", ["футбол", "баскетбол", "теннис", "олимпи", "спорт", "хоккей", "спортсмен", "чемпион"]),
        ("medicine", ["медицин", "болезн", "врач", "синдром", "лечен", "больниц", "вакцин", "диагноз"]),
        ("human", ["орган", "мозг", "сердц", "кость", "кров", "мышц", "тело человека"]),
        ("chemistry", ["химичес", "элемент", "периодичес", "молекул", "соединен", "кислот", "атом"]),
        ("physics", ["физик", "сила", "энерги", "скорост", "электр", "квант", "частиц", "гравитац"]),
        ("space", ["планет", "луна", "солнечн", "космос", "nasa", "астронавт", "галактик", "звезд", "марс"]),
        ("science", ["учён", "учен", "наук", "биолог", "эксперимент", "открыт", "теори"]),
        ("animals", ["животн", "птиц", "рыб", "млекопита", "рептили", "насеком", "вид живот"]),
        ("nature", ["растен", "лес", "рек", "океан", "гор", "климат", "дерев", "цветок"]),
        ("geography", ["столиц", "страна", "город", "континент", "остров", "находится", "гранич", "населени"]),
        ("history", ["войн", "битв", "импери", "король", "королев", "революц", "век", "истори", "древн"]),
        ("literature", ["книга", "роман", "автор", "писател", "стих", "поэт", "литератур"]),
        ("art", ["картин", "художник", "музей", "скульптур", "искусств"]),
        ("architecture", ["архитектор", "здание", "башн", "собор", "дворец", "архитектур", "мост"]),
        ("mythology", ["миф", "мифолог", "бог ", "богин", "зевс", "один", "легендар"]),
        ("food", ["еда", "блюд", "рецепт", "кухн", "сыр", "фрукт", "овощ"]),
        ("language", ["язык", "слово", "означает", "перевод", "алфавит", "граммат"]),
        ("inventions", ["изобрёл", "изобрел", "изобрет", "патент"]),
        ("technology", ["компьютер", "программ", "технолог", "процессор", "смартфон", "операционн"]),
        ("culture", ["фестивал", "традиц", "религи", "культур", "праздник", "церемон"]),
        ("comics", ["комикс", "супергер", "marvel", "dc comics", "бэтмен", "супермен", "человек-паук"]),
        ("popscience", ["научн", "исследован", "явлени", "эволюц", "вселенн"]),
    ],
    "kk": [
        ("anime", ["аниме", "манга", "наруто", "покемон"]),
        ("games", ["бейне ойын", "ойын", "playstation", "xbox", "nintendo", "minecraft"]),
        ("cinema", ["фильм", "кино", "режиссер", "актер", "актриса", "оскар"]),
        ("series", ["сериал", "эпизод", "маусым"]),
        ("animation", ["мультфильм", "анимация"]),
        ("music", ["ән", "әнші", "топ", "альбом", "музыка", "композитор"]),
        ("internet", ["сайт", "интернет", "браузер", "youtube", "facebook", "instagram", "домен"]),
        ("media", ["газет", "радио", "теледидар", "журналист", "журнал"]),
        ("cars", ["автомобиль", "көлік", "машина", "toyota", "bmw", "mercedes", "ford", "tesla"]),
        ("transport", ["әуежай", "пойыз", "теміржол", "кеме", "ұшақ", "метро", "көлік"]),
        ("sports", ["футбол", "баскетбол", "теннис", "олимпи", "спорт", "хоккей", "чемпион"]),
        ("medicine", ["медицина", "ауру", "дәрігер", "синдром", "емдеу", "вакцина"]),
        ("human", ["адам ағз", "ми", "жүрек", "сүйек", "қан", "бұлшықет"]),
        ("chemistry", ["химия", "элемент", "молекула", "қосылыс", "қышқыл", "атом"]),
        ("physics", ["физика", "күш", "энергия", "жылдамдық", "электр", "квант", "бөлшек", "гравитация"]),
        ("space", ["планета", "ай", "күн жүй", "ғарыш", "астронавт", "галактика", "жұлдыз", "марс"]),
        ("science", ["ғалым", "ғылым", "биология", "эксперимент", "теория", "зерттеу"]),
        ("animals", ["жануар", "құс", "балық", "сүтқоректі", "жәндік"]),
        ("nature", ["өсімдік", "орман", "өзен", "мұхит", "тау", "климат", "ағаш", "гүл"]),
        ("geography", ["астана", "ел", "қала", "құрлық", "арал", "орналасқан", "шекара", "халық"]),
        ("history", ["соғыс", "шайқас", "империя", "патша", "революция", "ғасыр", "тарих", "ежелгі"]),
        ("literature", ["кітап", "роман", "автор", "жазушы", "өлең", "ақын", "әдебиет"]),
        ("art", ["сурет", "суретші", "мұражай", "мүсін", "өнер"]),
        ("architecture", ["сәулетші", "ғимарат", "мұнара", "сарай", "сәулет", "көпір"]),
        ("mythology", ["миф", "мифология", "құдай", "аңыз"]),
        ("food", ["тағам", "ас", "рецепт", "ірімшік", "жеміс", "көкөніс"]),
        ("language", ["тіл", "сөз", "аудар", "әліпби", "грамматика"]),
        ("inventions", ["ойлап тап", "өнертабыс", "патент"]),
        ("technology", ["компьютер", "бағдарлама", "технология", "процессор", "смартфон"]),
        ("culture", ["дәстүр", "мәдениет", "мереке", "дін", "рәсім"]),
        ("comics", ["комикс", "суперқаһарман", "marvel", "dc comics"]),
        ("popscience", ["ғылыми", "құбылыс", "эволюция", "ғалам"]),
    ],
}


def http_bytes(url, tries=4):
    last = None
    for i in range(tries):
        try:
            r = requests.get(url, timeout=90, headers={"User-Agent":"Knowdium-quality-bank-builder/3.6"})
            r.raise_for_status()
            return r.content
        except Exception as e:
            last=e
            time.sleep(2+i*2)
    raise last


def norm(s):
    s = str(s or "").casefold().replace("ё","е")
    s = re.sub(r"[^\w\d]+", " ", s, flags=re.UNICODE)
    return " ".join(s.split())


def polish_question(q, lang):
    q = re.sub(r"\s+", " ", str(q or "").strip())
    if not q: return q
    if lang in ("ru","kk","en") and q[0].isalpha():
        q = q[0].upper()+q[1:]
    if q[-1] not in "?!.": q += "?"
    return q


def is_bad(q, a, lang):
    qn, an = norm(q), norm(a)
    if len(q) < 8 or len(q) > 190 or len(a) < 1 or len(a) > 72: return True
    if len(an) > 3 and an in qn: return True
    if any(x in qn for x in BANNED_COMMON): return True
    banned = BANNED_EN if lang=="en" else BANNED_RU if lang=="ru" else BANNED_KK
    if any(norm(x) in qn for x in banned): return True
    if re.search(r"\b(202[4-9]|203\d)\b", qn): return True
    if q.count("/") > 3 or "http" in qn: return True
    return False


def category_for(q, lang):
    qn=norm(q)
    for cat, words in KEYWORDS[lang]:
        if any(norm(w) in qn for w in words): return cat
    return "facts"


def answer_signature(q, a, atype, lang):
    qn=norm(q); a=str(a).strip()
    if atype in ("date","number","number_with_unit"): return atype
    who = ["who ","кто ","кім ","whose ","чей ","кімнің "]
    where=["where ","где ","қайда ","which country","какой стране","қай ел"]
    when=["when ","когда ","қашан ","what year","каком году","қай жылы"]
    howmany=["how many","сколько","қанша"]
    if any(x in qn for x in who): return "person"
    if any(x in qn for x in where): return "place"
    if any(x in qn for x in when): return "date"
    if any(x in qn for x in howmany): return "number"
    if re.fullmatch(r"[-+]?\d[\d\s.,:%/-]*", a): return "number"
    if re.fullmatch(r"\d{3,4}", a): return "date"
    if len(a.split()) <= 4 and any(ch.isupper() for ch in a): return "entity"
    return "phrase"


def difficulty(q, a, cat):
    n=len(q)+len(a)
    if cat in {"physics","chemistry","medicine","popscience"} or n>150: return "hard"
    if n>100: return "medium"
    return "easy"


def pick_answer(ans_list, lang):
    if not isinstance(ans_list,list): return None,None
    for item in ans_list:
        if not isinstance(item,dict): continue
        typ=str(item.get("type") or "")
        text=str(item.get("text") or "").strip()
        if typ=="unanswerable" or not text: continue
        if typ=="long_answer" and len(text)>72: continue
        return text,typ
    return None,None


def mkqa_records():
    raw=http_bytes(MKQA_URL)
    rows=[]
    with gzip.GzipFile(fileobj=io.BytesIO(raw)) as gz:
        for line in gz:
            try: obj=json.loads(line)
            except Exception: continue
            eid=str(obj.get("example_id",""))
            for lang in ("en","ru"):
                q=(obj.get("queries") or {}).get(lang) or ""
                a,typ=pick_answer((obj.get("answers") or {}).get(lang),lang)
                if not a: continue
                q=polish_question(q,lang)
                if is_bad(q,a,lang): continue
                cat=category_for(q,lang)
                rows.append({"lang":lang,"source":"MKQA","sourceId":eid,"q":q,"answer":a,"atype":typ or "phrase","category":cat})
    out={}
    for lang in ("en","ru"):
        subset=[r for r in rows if r["lang"]==lang]
        # stable source order but favour underrepresented categories while selecting.
        buckets=defaultdict(list)
        seen=set()
        for r in subset:
            sig=norm(r["q"])
            if sig in seen: continue
            seen.add(sig); buckets[r["category"]].append(r)
        chosen=[]
        cats=list(buckets)
        ptr=0
        while len(chosen)<TARGET_MKQA and cats:
            cat=cats[ptr%len(cats)]
            if buckets[cat]: chosen.append(buckets[cat].pop(0))
            else: cats.remove(cat); ptr-=1
            ptr+=1
        out[lang]=chosen
    return out


def parse_kaz_stream(data, gzipped=False, source="KazQAD"):
    fh=gzip.GzipFile(fileobj=io.BytesIO(data)) if gzipped else io.BytesIO(data)
    for line in fh:
        try: obj=json.loads(line)
        except Exception: continue
        q=polish_question(obj.get("question") or "","kk")
        answers=obj.get("answers") or {}
        texts=answers.get("text") if isinstance(answers,dict) else None
        if not texts: continue
        a=str(texts[0]).strip()
        if is_bad(q,a,"kk"): continue
        yield {"lang":"kk","source":source,"sourceId":str(obj.get("id") or hashlib.sha1(q.encode()).hexdigest()[:14]),"q":q,"answer":a,"atype":"entity" if len(a.split())<=4 else "phrase","category":category_for(q,"kk")}


def kaz_records():
    rows=[];seen=set()
    for url in KAZ_MAIN:
        for r in parse_kaz_stream(http_bytes(url),False,"KazQAD"):
            sig=norm(r["q"])
            if sig in seen: continue
            seen.add(sig);rows.append(r)
    if len(rows)<TARGET_KK:
        for r in parse_kaz_stream(http_bytes(KAZ_EXTRA),True,"KazQAD-NQ"):
            sig=norm(r["q"])
            if sig in seen: continue
            seen.add(sig);rows.append(r)
            if len(rows)>=TARGET_KK: break
    return rows[:TARGET_KK]


def stable_rng(text):
    return random.Random(int(hashlib.sha256(text.encode("utf-8")).hexdigest()[:16],16))


def build_mc(records, lang):
    by_sig=defaultdict(list);by_cat=defaultdict(list);global_pool=defaultdict(list)
    for r in records:
        sig=answer_signature(r["q"],r["answer"],r["atype"],lang);r["asig"]=sig
        by_sig[(r["category"],sig)].append(r["answer"])
        by_cat[r["category"]].append(r["answer"])
        global_pool[sig].append(r["answer"])
    out=[]; seenq=set()
    for r in records:
        qsig=norm(r["q"])
        if qsig in seenq: continue
        seenq.add(qsig)
        correct=r["answer"].strip(); sig=r["asig"]
        if sig=="binary":
            opts={"en":["Yes","No"],"ru":["Да","Нет"],"kk":["Иә","Жоқ"]}[lang]
            if norm(correct) not in {norm(x) for x in opts}: continue
        else:
            candidates=[]
            for pool in (by_sig[(r["category"],sig)], global_pool[sig], by_cat[r["category"]]):
                for x in pool:
                    if norm(x)==norm(correct) or norm(x) in {norm(z) for z in candidates}: continue
                    if len(x)>78 or len(x)<1: continue
                    # Keep distractor lengths in the same ballpark where possible.
                    ratio=(len(x)+2)/(len(correct)+2)
                    if 0.32 <= ratio <= 3.1: candidates.append(x)
                if len(candidates)>=20: break
            rng=stable_rng(r["sourceId"]+"|"+lang)
            rng.shuffle(candidates)
            opts=[correct]+candidates[:3]
            if len(opts)<4: continue
        rng=stable_rng("shuffle|"+r["sourceId"]+"|"+lang)
        rng.shuffle(opts)
        ci=next((i for i,x in enumerate(opts) if norm(x)==norm(correct)),None)
        if ci is None: continue
        cat=r["category"]
        out.append({
            "id":f"v36-{r['source'].lower().replace(' ','-')}-{lang}-{hashlib.sha1((r['sourceId']+'|'+r['q']).encode()).hexdigest()[:14]}",
            "q":r["q"],"answers":opts,"correct":ci,"category":cat,
            "difficulty":difficulty(r["q"],correct,cat),
            "rootFamily":f"v36:{r['source']}:{r['sourceId']}",
            "source":r["source"]
        })
    return out


def validate(arr, lang):
    ids=set(); qs=set(); bad=[]
    for i,q in enumerate(arr):
        if q["id"] in ids: bad.append((i,"duplicate id"))
        ids.add(q["id"])
        nq=norm(q["q"])
        if nq in qs: bad.append((i,"duplicate question"))
        qs.add(nq)
        if len(q["answers"]) not in (2,4): bad.append((i,"answer count"))
        if not 0<=q["correct"]<len(q["answers"]): bad.append((i,"correct index"))
        if len({norm(x) for x in q["answers"]})!=len(q["answers"]): bad.append((i,"duplicate option"))
    if bad: raise RuntimeError(f"{lang} validation failures: {bad[:10]}")


def main():
    print("Downloading and parsing MKQA…", flush=True)
    mk=mkqa_records()
    print({k:len(v) for k,v in mk.items()}, flush=True)
    print("Downloading and parsing KazQAD…", flush=True)
    kk=kaz_records(); print("kk raw",len(kk), flush=True)
    bank={"en":build_mc(mk["en"],"en"),"ru":build_mc(mk["ru"],"ru"),"kk":build_mc(kk,"kk")}
    for lang,arr in bank.items(): validate(arr,lang)
    report={lang:{"total":len(arr),"categories":dict(sorted(Counter(q["category"] for q in arr).items()))} for lang,arr in bank.items()}
    print(json.dumps(report,ensure_ascii=False,indent=2), flush=True)
    payload={"version":"3.6.0","licenseNote":"MKQA CC BY-SA 3.0; KazQAD CC BY-SA 4.0. See THIRD_PARTY_NOTICES.md","data":bank}
    js="(()=>{window.QUIZ_QUALITY_BANK_V36="+json.dumps(payload,ensure_ascii=False,separators=(",",":"))+";})();\n"
    (OUT/"quality_bank_v36.js").write_text(js,encoding="utf-8")
    (OUT/"quality_bank_report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    notices="""# Third-party question data notices\n\nThis build includes question/answer material adapted for multiple-choice play from the following openly licensed datasets.\n\n## MKQA — Multilingual Knowledge Questions & Answers\n- Authors: Shayne Longpre, Yi Lu, Joachim Daiber\n- Project: https://github.com/apple/ml-mkqa\n- License: Creative Commons Attribution-ShareAlike 3.0 Unported (CC BY-SA 3.0)\n- Used for English and Russian knowledge questions. The game adds distractors, category metadata and presentation metadata.\n\n## KazQAD — Kazakh Open-Domain Question Answering Dataset\n- Authors: Rustem Yeshpanov, Pavel Efimov, Leonid Boytsov, Ardak Shalkarbayuli, Pavel Braslavski\n- Project: https://github.com/IS2AI/KazQAD\n- License: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)\n- Used for Kazakh knowledge questions. The game adds distractors, category metadata and presentation metadata.\n\nThe adapted question-data portion is distributed under the applicable source ShareAlike terms. Game code and independently created assets are separate components.\n"""
    (OUT/"THIRD_PARTY_NOTICES.md").write_text(notices,encoding="utf-8")
    if min(len(bank["en"]),len(bank["ru"]))<5600 or len(bank["kk"])<6000:
        raise RuntimeError("Not enough quality records survived filtering")

if __name__=="__main__": main()
