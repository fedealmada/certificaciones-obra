from pathlib import Path
from decimal import Decimal as D
import json,re,unicodedata,difflib
source=Path(r'C:\Users\feede\.codex\attachments\f2929c00-470a-40c6-a99a-26e2853f7038\Texto pegado.txt')
rows=[l.split('\t') for l in source.read_text(encoding='utf-8-sig').splitlines()]
ocs=[json.loads(l,parse_float=D) for l in Path('target/oc-para-conciliar.jsonl').read_text(encoding='utf-8-sig').splitlines() if l.strip()]
def norm(s):return re.sub(r'[^a-z0-9]+',' ',''.join(c for c in unicodedata.normalize('NFD',s or '') if unicodedata.category(c)!='Mn').lower()).strip()
def num(v):
 s=v.replace('$','').replace('%','').replace(' ','').replace('.','').replace(',','.');return D(s) if re.fullmatch('-?[0-9.]+',s or 'x') and s!='-' else D(0)
def ratio(a,b):
 a,b=norm(a),norm(b);aa,bb=set(a.split()),set(b.split());return .55*difflib.SequenceMatcher(None,a,b).ratio()+.45*len(aa&bb)/max(1,len(aa|bb))
def prov(a,b):
 aa=set(norm(a).split())-{'de','la','el','s','a','r','l'};bb=set(norm(b).split());return len(aa&bb)/len(aa) if aa else .5
results=[]
for line,r in enumerate(rows[7:214],8):
 if not r[5].strip():continue
 code=r[0].strip();pref=r[1].strip(); rank=[]
 for o in ocs:
  full=o['codigo'] if '.' in (o['codigo'] or '') else str(o['oc'])+'.'+str(o['codigo'])
  exact=code not in ('','-') and full==code
  sim=ratio(r[5],o['detalle']);ps=prov(pref,o['proveedor']);sameMoney=num(r[9])>0 and abs(num(r[9])-D(str(o['importe'])))<D('1')
  score=sim+(.12 if exact else 0)+(.04 if sameMoney else 0)
  if pref not in ('','-') and ps<.5:score-=.3
  rank.append((score,sim,ps,exact,sameMoney,o))
 rank.sort(key=lambda x:x[0],reverse=True);best=rank[0];second=rank[1]
 score,sim,ps,exact,same,b=best
 ok=(exact and (sim>=.58 or (same and sim>=.35)) and (pref in ('','-') or ps>=.5)) or (sim>=.88 and score-second[0]>.09 and (pref in ('','-') or ps>=.7))
 # A duplicated explicit reference requires decisive descriptive evidence.
 entry={'linea':line,'ref':code,'contratista':pref,'descripcion':r[5].strip(),'vinculo':b['id'] if ok else None,'oc':b['oc'] if ok else None,'criterio':'codigo y detalle' if ok and exact else ('descripcion univoca' if ok else 'sin coincidencia segura'),'candidatos':[{'id':v[5]['id'],'oc':v[5]['oc'],'codigo':v[5]['codigo'],'proveedor':v[5]['proveedor'],'detalle':v[5]['detalle'],'score':round(v[0],3),'similitud':round(v[1],3),'proveedorMatch':v[2],'codigoExacto':v[3],'importeCoincide':v[4]} for v in rank[:3]]}
 results.append(entry)
Path('target/conciliacion-oc-cobro.json').write_text(json.dumps(results,ensure_ascii=False,indent=2,default=str),encoding='utf-8')
print('Tareas',len(results),'Vinculos seguros',sum(e['vinculo'] is not None for e in results))
for e in results:
 if e['vinculo'] is None:
  print(e['linea'],e['ref'],e['contratista'],e['descripcion'][:70],'=>',[(a['id'],a['oc'],a['proveedor'],a['similitud'],a['codigoExacto']) for a in e['candidatos'][:2]])
