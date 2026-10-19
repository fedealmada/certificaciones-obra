from pathlib import Path
from decimal import Decimal as D
import hashlib,json,re,csv,io
src=Path(r'C:\Users\feede\.codex\attachments\f2929c00-470a-40c6-a99a-26e2853f7038\Texto pegado.txt')
raw=src.read_bytes();sha=hashlib.sha256(raw).hexdigest();rows=[x.split('\t') for x in raw.decode('utf-8-sig').splitlines()]
links={x['linea']:x for x in json.loads(Path('target/conciliacion-oc-cobro.json').read_text(encoding='utf-8'))}
ocs={o['id']:o for o in [json.loads(x) for x in Path('target/oc-para-conciliar.jsonl').read_text(encoding='utf-8-sig').splitlines()]}
def num(s):
 t=s.replace('$','').replace('%','').replace(' ','').replace('.','').replace(',','.');return D(t) if re.fullmatch('-?[0-9.]+',t or 'x') and t!='-' else D(0)
def val(r,k):return num(r[k])
starts=[22,29,36,44,52,60,68,76];periods=[('2025-02-01','2025-05-31'),('2025-06-01','2025-11-30'),('2025-12-01','2025-12-31'),('2026-01-01','2026-01-31'),('2026-02-01','2026-02-28'),('2026-03-01','2026-03-31'),('2026-04-01','2026-04-30'),('2026-05-01','2026-05-31')]
tasks=[(i+1,r) for i,r in enumerate(rows) if 7<=i<214 and r[5].strip()]
manifest={'sha256':sha,'archivo':str(src),'tareas':len(tasks),'vinculadas':sum(e['vinculo'] is not None for e in links.values()),'certificados':[]}
for n,start in enumerate(starts,1):
 data=[]
 for line,r in tasks:
  mensual=r[20].strip()=='ANEXO MENSUAL'
  if mensual and n<4:continue
  l=links[line];previous=val(r,start+3);actual=val(r,start+4);acc=val(r,start+5)
  row={'linea':line,'clave':'simende:'+sha[:24]+':fila:'+str(line),'ocItemId':l['vinculo'],'referencia':r[0].strip(),'item':r[0].strip() if r[0].strip() not in ('','-') else (r[4].strip() or r[3].strip() or 'MANUAL-'+str(line)),'codigoTarea':r[3].strip(),'contratista':r[1].strip(),'rubro':r[2].strip(),'descripcion':r[5].strip(),'unidad':r[6].strip(),'cantidad':val(r,7),'precioUnitario':val(r,8),'mo':val(r,9),'materiales':val(r,10),'servicios':val(r,11),'matEmpresa':val(r,12),'subtotal':val(r,13),'estructura':val(r,15),'beneficio':val(r,17),'total':actual if mensual else val(r,20),'recurrente':mensual,'anteriorPct':val(r,start),'actualPct':val(r,start+1),'acumPct':val(r,start+2),'anterior':previous,'actual':actual,'acumulado':acc,'adicional':val(r,start-1) if n>=4 else D(0),'criterio':l['criterio']}
  if mensual:row['servicios']=actual;row['subtotal']=actual
  assert row['anteriorPct']+row['actualPct']==row['acumPct']
  data.append(row)
 prev=sum(r['anterior'] for r in data);actual=sum(r['actual'] for r in data);fp=val(rows[214],start+3);fa=val(rows[214],start+4);fac=val(rows[214],start+5)
 manifest['certificados'].append({'numero':n,'desde':periods[n-1][0],'hasta':periods[n-1][1],'origen':'simende:'+sha+':cert:'+str(n),'anterior':fp,'actual':fa,'acumulado':fac,'ajusteAcumulado':fac-fp-fa,'ajusteAnterior':fp-prev,'ajusteActual':fa-actual,'presupuestoFuente':val(rows[214],20),'filas':data})
Path('target/importacion-simende-manifiesto.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2,default=str),encoding='utf-8')
# SQL uses UTF-8 hex literals: no data is interpolated as executable SQL.
def sql(v):
 if v is None:return 'NULL'
 if isinstance(v,bool):return '1' if v else '0'
 if isinstance(v,(D,int)):return str(v)
 return "CONVERT(0x"+str(v).encode('utf-8').hex()+" USING utf8mb4)" if str(v) else "''"
def insert(table,fields):return 'INSERT INTO '+table+' ('+','.join(fields)+') VALUES ('+','.join(sql(x) for x in fields.values())+');'
cmd=['SET NAMES utf8mb4;','START TRANSACTION;','CREATE TEMPORARY TABLE guard_importacion (id INT PRIMARY KEY);','INSERT INTO guard_importacion VALUES (1);',"INSERT INTO guard_importacion SELECT 1 WHERE EXISTS (SELECT 1 FROM tablero_certificado WHERE obra_id=1 AND (numero BETWEEN 1 AND 8 OR origen_importacion LIKE 'simende:"+sha+":%'));",'SET @cert_anterior=NULL;']
for c in manifest['certificados']:
 note='Importado de planilla SIMENDE, archivo SHA256 '+sha[:16]+'. Porcentajes e importes de cobro propios, independientes del avance real de las OC.'
 if c['numero']>=4:note+=' Anexos mensuales: saldo anterior de $45.740.000 informado desde enero, sin reparto documental entre certificados 1 a 3.'
 fields={'obra_id':1,'numero':c['numero'],'nombre':'Certificado N.º '+str(c['numero'])+' · SIMENDE','cliente':'BAPRISUD','empresa':'SIMENDE','fecha_desde':c['desde'],'fecha_hasta':c['hasta'],'emitido':True,'observacion':note,'origen_importacion':c['origen'],'ajuste_anterior':c['ajusteAnterior'],'ajuste_actual':c['ajusteActual'],'ajuste_acumulado':c['ajusteAcumulado'],'presupuesto_fuente':c['presupuestoFuente']}
 stmt=insert('tablero_certificado',fields);cmd.append(stmt);cmd.append('SET @cert_actual=LAST_INSERT_ID();');cmd.append('UPDATE tablero_certificado SET anterior_id=@cert_anterior WHERE id=@cert_actual;')
 for idx,r in enumerate(c['filas'],1):
  obs='Planilla fuente, línea '+str(r['linea'])+'. '+r['criterio']+'.'
  if r['recurrente']:obs+=' Anexo mensual; saldo anterior documental conservado desde enero.'
  fields={'tablero_id':None,'item_orden_compra_id':r['ocItemId'],'clave_continuidad':r['clave'],'grupo':False,'orden_fila':idx,'contratista':r['contratista'],'rubro':r['rubro'],'codigo_tarea':r['codigoTarea'],'item_codigo':r['item'],'descripcion_tarea':r['descripcion'],'unidad':r['unidad'],'cantidad':r['cantidad'],'precio_unitario':r['precioUnitario'],'costo_mano_obra':r['mo'],'materiales_asignados':r['materiales'],'servicios':r['servicios'],'materiales_suministrados_empresa':r['matEmpresa'],'materiales_adicionales_etapa':r['adicional'],'subtotal_manual':r['subtotal'],'costo_estructural_porcentaje':r['estructura'],'beneficio_empresarial_porcentaje':r['beneficio'],'avance_anterior_porcentaje':r['anteriorPct'],'avance_certificado_porcentaje':r['actualPct'],'monto_anterior_fijo':r['anterior'],'total_tarea_informado':r['total'],'monto_actual_informado':r['actual'],'monto_acumulado_informado':r['acumulado'],'recurrente':r['recurrente'],'observacion':obs[:1200]}
  cmd.append(insert('tablero_certificado_item',fields).replace('VALUES (NULL,','VALUES (@cert_actual,',1))
 cmd.append('SET @cert_anterior=@cert_actual;')
cmd+=['COMMIT;']
Path('target/importar-simende-correlacionado.sql').write_text('\n'.join(cmd),encoding='utf-8')
# Reviewable, permanent cross-reference report.
out=io.StringIO();w=csv.writer(out,delimiter=';');w.writerow(['Linea fuente','Referencia planilla','Tarea','Responsable en planilla','OC vinculada','Item OC ID','Proveedor OC','Criterio'])
for line,r in tasks:
 l=links[line];o=ocs.get(l['vinculo']);w.writerow([line,r[0],r[5],r[1],o['oc'] if o else '',o['id'] if o else '',o['proveedor'] if o else '',l['criterio']])
Path('backups/conciliacion-items-simende-20261019.csv').write_text('\ufeff'+out.getvalue(),encoding='utf-8')
for c in manifest['certificados']:print(c['numero'],c['desde'],c['hasta'],'filas',len(c['filas']),'actual',c['actual'],'acumulado',c['acumulado'],'ajustes',c['ajusteAnterior'],c['ajusteActual'])
print('SHA',sha,'SQL preparado; no ejecutado.')
