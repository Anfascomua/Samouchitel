import json,re,zipfile,io,urllib.request,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DICT=ROOT/"content/en/dictionary.json"
URL="https://www.manythings.org/anki/rus-eng.zip"
CYR=re.compile(r"[А-Яа-яЁё]")
TOKEN=re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")
def pron(s):
    x=s.lower()
    rules=[("tion","шэн"),("sion","жэн"),("ture","чэр"),("ough","оу"),("igh","ай"),("ee","и"),("oo","у"),("ea","и"),("ai","эй"),("ay","эй"),("ow","оу"),("ou","ау"),("oi","ой"),("oy","ой"),("th","з"),("sh","ш"),("ch","ч"),("ph","ф"),("wh","у"),("ck","к"),("ng","нг"),("qu","кв")]
    for a,b in rules:x=x.replace(a,b)
    mp={"a":"э","b":"б","c":"к","d":"д","e":"э","f":"ф","g":"г","h":"х","i":"и","j":"дж","k":"к","l":"л","m":"м","n":"н","o":"о","p":"п","q":"к","r":"р","s":"с","t":"т","u":"у","v":"в","w":"у","x":"кс","y":"й","z":"з"}
    return "".join(mp.get(c,c) for c in x)
def verb_tense(en):
    e=en.lower()
    if re.search(r"\\b(yesterday|last |ago|did|was|were|had)\\b",e) or re.search(r"\\b\\w+ed\\b",e): return "past"
    if re.search(r"\\b(tomorrow|next |will|shall|going to)\\b",e): return "future"
    return "present"

def score(en,word):
    toks=TOKEN.findall(en.lower()); n=len(toks)
    if not 4<=n<=14:return -999
    sc=100-abs(n-8)*5
    if toks.count(word.lower())==1:sc+=10
    if en.endswith((".","?","!")):sc+=4
    if any(ch.isdigit() for ch in en):sc-=20
    if re.search(r"https?://|www\.|@",en,re.I):sc-=100
    return sc
def main():
    d=json.loads(DICT.read_text(encoding="utf-8"))
    words={w["en"].lower():w for w in d["words"] if re.fullmatch(r"[A-Za-z]+(?:'[A-Za-z]+)?",w["en"])}
    cand={k:[] for k in words}
    req=urllib.request.Request(URL,headers={"User-Agent":"Mozilla/5.0 (Samouchitel dictionary builder)","Accept":"*/*"})\n    data=urllib.request.urlopen(req,timeout=120).read()
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        name=[n for n in z.namelist() if n.endswith(".txt")][0]
        for raw in z.read(name).decode("utf-8",errors="ignore").splitlines():
            p=raw.split("\t")
            if len(p)<2:continue
            a,b=p[0].strip(),p[1].strip()
            en,ru=(b,a) if CYR.search(a) and not CYR.search(b) else (a,b)
            if not CYR.search(ru):continue
            toks=set(TOKEN.findall(en.lower()))
            for w in toks & words.keys():
                sc=score(en,w)
                if sc>-900:cand[w].append((sc,en,ru))
    filled=0
    for key,w in words.items():
        if len(w.get("examples",[]))==3:continue
        seen=set(); chosen=[]
        for sc,en,ru in sorted(cand[key],reverse=True):
            norm=re.sub(r"[^a-z ]","",en.lower())
            stem=" ".join(norm.split()[:3])
            if stem in seen:continue
            seen.add(stem);chosen.append({"en":en,"pronunciationRu":pron(en),"ru":ru,"source":"Tatoeba / ManyThings"})
            if len(chosen)==3:break
        if (w.get("category") or "").lower()=="verb":
            buckets={"present":[],"past":[],"future":[]}
            for sc,en,ru in sorted(cand[key],reverse=True):
                t=verb_tense(en)
                if not buckets[t]: buckets[t]=[{"en":en,"pronunciationRu":pron(en),"ru":ru,"source":"Tatoeba / ManyThings","tense":t}]
            tense_examples=buckets["present"]+buckets["past"]+buckets["future"]
            if len(tense_examples)==3:
                w["examples"]=tense_examples;filled+=1
        elif len(chosen)==3:w["examples"]=chosen;filled+=1
    DICT.write_text(json.dumps(d,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
    total=sum(1 for w in d["words"] if len(w.get("examples",[]))==3)
    print(f"filled this run={filled}; total with 3 examples={total}/{len(d['words'])}")
if __name__=="__main__":main()

# workflow trigger
