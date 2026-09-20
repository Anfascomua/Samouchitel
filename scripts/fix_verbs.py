import io,json,re,tarfile,urllib.request
from pathlib import Path
from lemminflect import getInflection
ROOT=Path(__file__).resolve().parents[1]; DP=ROOT/"content/en/dictionary.json"
URL="https://object.pouta.csc.fi/OPUS-100/v1.0/opus-100-corpus-en-ru-v1.0.tar.gz"
TOK=re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")
def pron(s):
    x=s.lower()
    for a,b in [("tion","шэн"),("sion","жэн"),("ture","чэр"),("igh","ай"),("ee","и"),("oo","у"),("ea","и"),("ai","эй"),("ay","эй"),("sh","ш"),("ch","ч"),("th","з"),("ph","ф"),("ng","нг"),("qu","кв")]: x=x.replace(a,b)
    m=dict(zip("abcdefghijklmnopqrstuvwxyz",["э","б","к","д","э","ф","г","х","и","дж","к","л","м","н","о","п","к","р","с","т","у","в","у","кс","й","з"]))
    return "".join(m.get(c,c) for c in x)
d=json.loads(DP.read_text(encoding="utf-8"))
verbs={w["en"].lower():w for w in d["words"] if (w.get("category") or "").lower()=="verb" and re.fullmatch(r"[a-z]+",w["en"].lower())}
index={}; pastforms={}; presforms={}
for k in verbs:
    pres=set([k]+[x.lower() for x in getInflection(k,tag="VBZ")]+[x.lower() for x in getInflection(k,tag="VBP")])
    past=set(x.lower() for x in getInflection(k,tag="VBD"))
    presforms[k]=pres; pastforms[k]=past
    for form in pres|past: index.setdefault(form,set()).add(k)
cand={k:{"present":[],"past":[],"future":[]} for k in verbs}
raw=urllib.request.urlopen(urllib.request.Request(URL,headers={"User-Agent":"Mozilla/5.0"}),timeout=120).read()
with tarfile.open(fileobj=io.BytesIO(raw),mode="r:gz") as tf:
    names=tf.getnames()
    for ef in sorted(n for n in names if n.endswith(".en")):
        rf=ef[:-3]+".ru"
        if rf not in names: continue
        ens=tf.extractfile(ef).read().decode("utf-8","ignore").splitlines(); rus=tf.extractfile(rf).read().decode("utf-8","ignore").splitlines()
        for en,ru in zip(ens,rus):
            toks=[x.lower() for x in TOK.findall(en)]
            if not ru or not 3<=len(toks)<=20: continue
            keys=set()
            for tok in set(toks): keys.update(index.get(tok,()))
            if not keys: continue
            low=en.lower()
            for k in keys:
                b=cand[k]
                if all(b.values()): continue
                ts=set(toks)
                if re.search(r"\\b(?:will|shall)\\s+"+re.escape(k)+r"\\b",low) or re.search(r"\\bgoing to\\s+"+re.escape(k)+r"\\b",low): t="future"
                elif ts & pastforms[k]: t="past"
                elif ts & presforms[k]: t="present"
                else: continue
                if not b[t]: b[t]=[(en.strip(),ru.strip())]
fixed=0
for k,w in verbs.items():
    b=cand[k]
    if all(b.values()):
        chosen=[b["present"][0],b["past"][0],b["future"][0]]
        w["examples"]=[{"en":en,"pronunciationRu":pron(en),"ru":ru,"source":"OPUS-100 internet corpus","tense":t} for (en,ru),t in zip(chosen,["present","past","future"])]
        fixed+=1
DP.write_text(json.dumps(d,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
print("verbs_three_tenses",fixed,"/",len(verbs))
