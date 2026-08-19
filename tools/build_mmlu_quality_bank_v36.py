#!/usr/bin/env python3
import hashlib, json, random, re, time
from collections import Counter
from pathlib import Path
import pandas as pd

OUT=Path('quality-bank-v36-mmlu'); OUT.mkdir(exist_ok=True)
URLS={
'en':'https://huggingface.co/datasets/TIGER-Lab/MMLU-Pro/resolve/main/data/test-00000-of-00001.parquet?download=true',
'ru':'https://huggingface.co/datasets/issai/MMLU-Pro_Kazakh_Russian/resolve/main/russian/test-00000-of-00001.parquet?download=true',
'kk':'https://huggingface.co/datasets/issai/MMLU-Pro_Kazakh_Russian/resolve/main/kazakh/test-00000-of-00001.parquet?download=true',
}
# No mathematics in this game. Other categories are mapped into the existing quiz taxonomy.
MAP={
'biology':'science','business':'brands','chemistry':'chemistry','computer science':'technology','computer_science':'technology',
'economics':'facts','engineering':'technology','health':'medicine','history':'history','law':'culture','other':'facts',
'philosophy':'logic','physics':'physics','psychology':'human'
}
BANNED_CATEGORIES={'math','mathematics'}
DYNAMIC={
'en':['currently','right now','today','this year','last year','next year','latest','current president','current ceo','current prime minister','as of 20','according to the passage','according to the text'],
'ru':['сейчас','сегодня','в этом году','в прошлом году','в следующем году','последн','действующ','текущ','по состоянию на 20','согласно тексту','согласно отрывку'],
'kk':['қазір','бүгін','биыл','өткен жылы','келесі жылы','соңғы','қазіргі','20 жағдай бойынша','мәтінге сәйкес','үзіндіге сәйкес'],
}

def norm(s):
    s=str(s or '').casefold().replace('ё','е')
    s=re.sub(r'\s+',' ',s).strip()
    return s

def clean_text(s): return re.sub(r'\s+',' ',str(s or '').strip())

def bad_text(q,opts,lang,cat):
    qn=norm(q)
    if cat in BANNED_CATEGORIES: return True
    if len(q)<18 or len(q)>330: return True
    if any(x in qn for x in DYNAMIC[lang]): return True
    if any(x in qn for x in ['porn','sexual intercourse','suicide method','onlyfans']): return True
    # Remove benchmark/instruction remnants and heavy equation exercises that feel like math even under other labels.
    if any(x in qn for x in ['which of the following is the value of','solve for x','calculate the value','what is the integral','what is the derivative']): return True
    if q.count('\\')>2 or q.count('$')>4: return True
    if len(opts)<4: return True
    if any(not x or len(x)>170 for x in opts): return True
    # Questions whose choices are giant paragraphs do not work in this UI.
    if sum(len(x) for x in opts)>520: return True
    return False

def deterministic_four(options,correct_idx,seed):
    correct=options[correct_idx]
    pool=[(i,x) for i,x in enumerate(options) if i!=correct_idx and norm(x)!=norm(correct)]
    unique=[]; seen={norm(correct)}
    for i,x in pool:
        nx=norm(x)
        if not nx or nx in seen: continue
        seen.add(nx); unique.append(x)
    if len(unique)<3: return None
    rng=random.Random(int(hashlib.sha256(seed.encode()).hexdigest()[:16],16))
    rng.shuffle(unique); selected=[correct]+unique[:3]; rng.shuffle(selected)
    return selected,selected.index(correct)

def difficulty(cat,q):
    if cat in {'chemistry','physics','medicine','logic'}: return 'hard'
    return 'hard' if len(q)>190 else 'medium' if len(q)>115 else 'easy'

def main():
    started=time.time(); banks={}; report={}
    for lang,url in URLS.items():
        print('read',lang,flush=True)
        df=pd.read_parquet(url)
        arr=[]; seen=set(); cats=Counter(); srcs=Counter(); rejected=Counter()
        for _,r in df.iterrows():
            q=clean_text(r.get('question'))
            rawcat=norm(r.get('category')).replace('_',' ')
            if rawcat in BANNED_CATEGORIES:
                rejected['math-category']+=1; continue
            cat=MAP.get(rawcat,'facts')
            rawopts=r.get('options')
            opts=[clean_text(x) for x in (list(rawopts) if rawopts is not None else [])]
            try: ci=int(r.get('answer_index'))
            except: rejected['answer-index']+=1; continue
            if ci<0 or ci>=len(opts): rejected['answer-index']+=1; continue
            if bad_text(q,opts,lang,rawcat): rejected['quality-filter']+=1; continue
            nq=norm(q)
            if nq in seen: rejected['duplicate-question']+=1; continue
            pack=deterministic_four(opts,ci,f'{lang}|{r.get("question_id")}|{q}')
            if not pack: rejected['options']+=1; continue
            answers,correct=pack
            seen.add(nq)
            family='mmlupro-'+hashlib.sha1((str(r.get('question_id'))+'|'+norm(df.iloc[0].get('category',''))).encode()).hexdigest()[:16]
            item={'id':family+'-'+lang,'family':family,'rootFamily':family,'category':cat,'q':q,'answers':answers,'correct':correct,'difficulty':difficulty(cat,q),'type':'choice','local':False,'source':'MMLU-Pro'}
            arr.append(item); cats[cat]+=1; srcs[str(r.get('src') or '')]+=1
        banks[lang]=arr
        report[lang]={'total':len(arr),'byCategory':dict(sorted(cats.items())),'rejected':dict(rejected)}
        print(lang,len(arr),dict(cats),flush=True)
    report['seconds']=round(time.time()-started,2)
    (OUT/'mmlu_bank_v36.js').write_text('window.MMLU_QUALITY_BANK_V36='+json.dumps(banks,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf-8')
    (OUT/'mmlu_bank_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'SOURCES.txt').write_text('English: TIGER-Lab/MMLU-Pro (MIT)\nRussian/Kazakh: issai/MMLU-Pro_Kazakh_Russian (MIT)\nMathematics category excluded.\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)

if __name__=='__main__': main()
