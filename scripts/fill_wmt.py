import json,re,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; P=ROOT/"content/en/dictionary.json"
CYR=re.compile(r"[А-Яа-яЁё]"); TOK=re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")
def pron(s):
 x=s.lower()
 for a,b in [("tion","шэн"),("sion","жэн"),("ture","чэр"),("ough","оу"),("igh","ай"),("ee","и"),("oo","у"),("ea","и"),("ai","эй"),("ay","эй"),("ow","оу"),("ou","ау"),("th","з"),("sh","ш"),("ch","ч"),("ph","ф"),("ck","к"),("ng","нг")]:x=x.replace(a,b)
 m=dict(zip("abcdefghijklmnopqrstuvwxyz",["э","б","к","д","э","ф","г","х","и","дж","к","л","м","н","о","п","к","р","с","т","у","в","у","кс","й","з"]))
 return "".join(m.get(c,c) for c in x)
def tense(e):
 e=e.lower()
 if re.search(r"\b(yesterday|last |ago|did|was|were|had)\b",e) or re.search(r"\b\w+ed\b",e):return "past"
 if re.search(r"\b(tomorrow|next |will|shall|going to)\b",e):return "future"
 return "present"
def ok(en,w):
 n=len(TOK.findall(en))
 return 3<=n<=18 and ((w in en.lower()) if " " in w else w in set(TOK.findall(en.lower()))) and "http" not in en.lower()
d=json.loads(P.read_text(encoding="utf-8")); missing={w["en"].lower():w for w in d["words"] if len(w.get("examples",[]))!=3}; keys=set(missing); phrases=[k for k in keys if " " in k]; cand={k:[] for k in keys}
for i in range(1,11):
 url=f"https://raw.githubusercontent.com/mashashma/WMT2022-data/main/en-ru/en-ru.1m_{i}.tsv"; req=urllib.request.Request(url,headers={"User-Agent":"Samouchitel/1.0"})
 with urllib.request.urlopen(req,timeout=60) as r:
  for raw in r:
   try:p=raw.decode("utf-8").rstrip().split("\t")
   except:continue
   if len(p)<2:continue
   a,b=p[0].strip(),p[1].strip()
   if CYR.search(a) and not CYR.search(b):ru,en=a,b
   elif CYR.search(b) and not CYR.search(a):en,ru=a,b
   else:continue
   low=en.lower(); hits=set(TOK.findall(low))&keys
   for k in phrases:
    if k in low:hits.add(k)
   for k in hits:
    if ok(en,k):cand[k].append((100-abs(len(TOK.findall(en))-8)*4,en,ru))
filled=0
for k,w in missing.items():
 rows=sorted(cand[k],reverse=True); chosen=[]
 if (w.get("category") or "").lower()=="verb":
  b={"present":None,"past":None,"future":None}
  for _,en,ru in rows:
   t=tense(en)
   if b[t] is None:b[t]={"en":en,"pronunciationRu":pron(en),"ru":ru,"source":"WMT2022 web parallel corpus","tense":t}
  if all(b.values()):chosen=[b[x] for x in ("present","past","future")]
 else:
  seen=set()
  for _,en,ru in rows:
   stem=" ".join(TOK.findall(en.lower())[:4])
   if stem in seen:continue
   seen.add(stem);chosen.append({"en":en,"pronunciationRu":pron(en),"ru":ru,"source":"WMT2022 web parallel corpus"})
   if len(chosen)==3:break
 if len(chosen)==3:w["examples"]=chosen;filled+=1
P.write_text(json.dumps(d,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
left=[{"id":w.get("id"),"en":w.get("en"),"ru":w.get("ru"),"category":w.get("category")} for w in d["words"] if len(w.get("examples",[]))!=3]
(ROOT/"content/en/missing_examples.json").write_text(json.dumps(left,ensure_ascii=False,indent=2),encoding="utf-8")
print(f"WMT filled={filled}; total={len(d['words'])-len(left)}/{len(d['words'])}; left={len(left)}")
