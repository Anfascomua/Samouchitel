import io,json,re,tarfile,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DP=ROOT/"content/en/dictionary.json"
URL="https://object.pouta.csc.fi/OPUS-100/v1.0/opus-100-corpus-en-ru-v1.0.tar.gz"
TOK=re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")
def pron(s):
    x=s.lower()
    for a,b in [("tion","шэн"),("sion","жэн"),("ture","чэр"),("igh","ай"),("ee","и"),("oo","у"),("ea","и"),("ai","эй"),("ay","эй"),("sh","ш"),("ch","ч"),("th","з"),("ph","ф"),("ng","нг"),("qu","кв")]: x=x.replace(a,b)
    m=dict(zip("abcdefghijklmnopqrstuvwxyz",["э","б","к","д","э","ф","г","х","и","дж","к","л","м","н","о","п","к","р","с","т","у","в","у","кс","й","з"]))
    return "".join(m.get(c,c) for c in x)
def tense(s):
    e=s.lower()
    if re.search(r"\b(will|shall|tomorrow|next week|next year|going to)\b",e): return "future"
    if re.search(r"\b(yesterday|last week|last year|ago|did|was|were|had)\b",e) or re.search(r"\b[a-z]+ed\b",e): return "past"
    return "present"
def good(en,word):
    n=len(TOK.findall(en))
    return 3<=n<=18 and re.search(r"(?<![A-Za-z])"+re.escape(word)+r"(?![A-Za-z])",en,re.I)
def main():
    d=json.loads(DP.read_text(encoding="utf-8"))
    missing={w["en"].lower():w for w in d["words"] if len(w.get("examples",[]))!=3}
    single={k for k in missing if re.fullmatch(r"[a-z]+(?:'[a-z]+)?",k)}
    cand={k:[] for k in missing}
    req=urllib.request.Request(URL,headers={"User-Agent":"Mozilla/5.0"})
    raw=urllib.request.urlopen(req,timeout=120).read()
    with tarfile.open(fileobj=io.BytesIO(raw),mode="r:gz") as tf:
        names=tf.getnames()
        enfiles=sorted([n for n in names if n.endswith(".en")])
        for ef in enfiles:
            rf=ef[:-3]+".ru"
            if rf not in names: continue
            ens=tf.extractfile(ef).read().decode("utf-8","ignore").splitlines()
            rus=tf.extractfile(rf).read().decode("utf-8","ignore").splitlines()
            for en,ru in zip(ens,rus):
                toks=set(x.lower() for x in TOK.findall(en))
                for k in toks & single:
                    if len(cand[k])<12 and good(en,k): cand[k].append((en.strip(),ru.strip()))
    filled=0
    for k,w in missing.items():
        rows=cand.get(k,[])
        seen=set(); uniq=[]
        for en,ru in rows:
            sig=re.sub(r"[^a-z ]","",en.lower())
            if sig in seen or not ru: continue
            seen.add(sig); uniq.append((en,ru))
        if (w.get("category") or "").lower()=="verb":
            buckets={"present":[],"past":[],"future":[]}
            for en,ru in uniq:
                t=tense(en)
                if not buckets[t]: buckets[t]=[(en,ru)]
            chosen=buckets["present"]+buckets["past"]+buckets["future"]
            if len(chosen)!=3: chosen=uniq[:3]
        else: chosen=uniq[:3]
        if len(chosen)==3:
            w["examples"]=[{"en":en,"pronunciationRu":pron(en),"ru":ru,"source":"OPUS-100 internet corpus"} for en,ru in chosen]
            filled+=1
    DP.write_text(json.dumps(d,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
    left=[{"id":w.get("id"),"en":w.get("en"),"ru":w.get("ru"),"category":w.get("category")} for w in d["words"] if len(w.get("examples",[]))!=3]
    (ROOT/"content/en/missing_examples.json").write_text(json.dumps(left,ensure_ascii=False,indent=2),encoding="utf-8")
    print("filled",filled,"total",len(d["words"])-len(left),"/",len(d["words"]),"left",len(left))
if __name__=="__main__": main()
