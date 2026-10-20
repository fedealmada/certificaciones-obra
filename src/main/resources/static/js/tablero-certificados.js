(() => {
  const money = new Intl.NumberFormat('es-AR',{minimumFractionDigits:2,maximumFractionDigits:2});
  const normalize = v => String(v || '').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const cents = v => {
    const s=String(v || '0').replace(',','.'); const neg=s.startsWith('-'); const [a,b='']=s.replace(/^[-+]/,'').split('.');
    if(!/^\d*$/.test(a)||!/^\d*$/.test(b)) return 0n;
    const n=BigInt(a||'0')*100n+BigInt((b+'00').slice(0,2)); return neg?-n:n;
  };
  const rounded = (numerator,denominator) => numerator<0n?-((-numerator+denominator/2n)/denominator):(numerator+denominator/2n)/denominator;
  const cash = c => '$ '+money.format(Number(c)/100);
  let dirty=false;
  const ledger=document.querySelector('#cg-ledger');
  const recalc=() => {
    if(!ledger) return; let total=0n;
    ledger.querySelectorAll('tbody tr').forEach(row=>{
      const input=row.querySelector('.cg-percent'); if(!input) return;
      row.classList.toggle('cg-has-current-progress',Number(input.value)>0);
      const pct=cents(input.value); const sinCambio = row.dataset.guardado !== undefined && pct===cents(row.dataset.guardado);
      const actual=sinCambio ? cents(row.dataset.importeGuardado) : rounded(cents(row.dataset.total)*pct,10000n)+cents(row.dataset.extra);
      const prev=cents(row.dataset.previo); total+=actual;
      row.querySelector('.cg-row-current').textContent=cash(actual);
      row.querySelector('.cg-row-acc').textContent=cash(sinCambio ? cents(row.dataset.acumuladoGuardado) : prev+actual);
      row.querySelector('.cg-row-accpct').textContent=money.format((row.dataset.recurrente==='true'?0:Number(row.dataset.prevpct))+Number(input.value||0))+' %';
    });
    if(ledger.querySelector('.cg-percent')) {
      total += cents(document.querySelector('#cg-total-current').dataset.ajuste);
      document.querySelector('#cg-total-current').textContent=cash(total);
      document.querySelector('#cg-total-acc').textContent=cash(total+cents(document.querySelector('#cg-total-acc').dataset.anterior)+cents(document.querySelector('#cg-total-acc').dataset.ajuste));
    }
  };
  const markDirty=()=>{dirty=true; recalc(); document.querySelector('#cg-save-state').textContent='Cambios sin guardar'; document.querySelector('.cg-save-bar')?.classList.add('is-dirty'); document.querySelector('#cg-total-caption').textContent='Vista previa · pendiente de guardar';};
  ledger?.querySelectorAll('.cg-percent').forEach(i=>i.addEventListener('input',markDirty));
  ledger?.querySelectorAll('.cg-fill').forEach(b=>b.addEventListener('click',()=>{const row=b.closest('tr'); row.querySelector('.cg-percent').value=row.dataset.pendiente; markDirty();}));
  const filterRows=()=>{
    const query=normalize(document.querySelector('#cg-search')?.value); const pending=document.querySelector('#cg-pending')?.checked; let visible=0;
    ledger?.querySelectorAll('tbody tr').forEach(r=>{r.hidden= !normalize(r.dataset.search).includes(query) || (pending && Number(r.dataset.pendiente)<=0); if(!r.hidden)visible++;});
    if(document.querySelector('#cg-visible-count'))document.querySelector('#cg-visible-count').textContent=visible+' tareas visibles';
    if(document.querySelector('#cg-no-results'))document.querySelector('#cg-no-results').hidden=visible!==0 || !ledger?.querySelector('tbody tr');
  };
  document.querySelector('#cg-search')?.addEventListener('input',filterRows); document.querySelector('#cg-pending')?.addEventListener('change',filterRows); filterRows();
  document.querySelector('#cg-advances-form')?.addEventListener('submit',()=>{dirty=false;});
  document.querySelectorAll('form').forEach(f=>f.addEventListener('submit',e=>{
    if(dirty && f.id!=='cg-advances-form') {e.preventDefault(); alert('Guardá los avances antes de realizar esta acción.');return;}
    if(f.dataset.confirm && !confirm(f.dataset.confirm))e.preventDefault();
  }));
  window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
  const pick=document.querySelector('#cg-pick');
  const selected=()=>{const count=pick?.querySelectorAll('input:checked').length||0; const target=document.querySelector('#cg-pick-count'); if(target) target.textContent=count+' tareas seleccionadas';};
  pick?.addEventListener('change',selected);
  document.querySelector('#cg-pick-search')?.addEventListener('input',e=>{const query=normalize(e.target.value); pick?.querySelectorAll('tbody tr').forEach(r=>r.hidden=!normalize(r.dataset.search).includes(query));});
  document.querySelector('#cg-pick-all')?.addEventListener('click',()=>{const boxes=[...pick.querySelectorAll('tbody tr:not([hidden]) input:not(:disabled)')]; const check=boxes.some(i=>!i.checked); boxes.forEach(i=>i.checked=check); selected();});
  function initMaterialPicker(costs) {
    if(!costs || costs.dataset.materialPicker)return;
    costs.dataset.materialPicker='true';
    const locked=costs.querySelector('#cg-materials-locked')?.value==='true';
    const panel=document.createElement('details');panel.className='cg-material-picker';
    panel.innerHTML='<summary><i class="bi bi-box-seam"></i> Compras de materiales del período</summary><div class="cg-material-filters"><label>Desde<input class="form-control" type="date" data-from></label><label>Hasta<input class="form-control" type="date" data-to></label><label>Buscar OC, proveedor o material<input class="form-control" type="search" data-query></label><button class="btn btn-outline-primary" type="button" data-search>Buscar</button></div><p class="cg-muted">Fecha de emisión de la OC. El disponible descuenta lo asignado a otras tareas.</p><p role="status" data-message></p><div class="cg-material-results"></div><div class="cg-toolbar"><button type="button" class="btn btn-outline-secondary" data-prev>Anterior</button><span data-page></span><button type="button" class="btn btn-outline-secondary" data-next>Siguiente</button></div><h3>Materiales seleccionados</h3><div data-selected></div><div class="cg-toolbar"><strong data-total></strong><button type="button" class="btn btn-primary" data-apply>Aplicar selección a materiales</button></div><small class="cg-muted">La selección y los costos se guardan juntos al guardar la tarea. Aplicar reemplaza el importe manual de materiales y quita el costo base informado.</small>';
    costs.querySelector('fieldset')?.prepend(panel);
    const message=panel.querySelector('[data-message]');
    const results=panel.querySelector('.cg-material-results');
    const selectedBox=panel.querySelector('[data-selected]');
    const chosen=new Map();let page=0,pages=0,initialized=false,busy=false,blocked=locked;
    const endpoint=costs.action.replace(/\/$/,'')+'/materiales';
    const el=(tag,text,className)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n;};
    const amount=v=>cash(cents(v));
    const drawSelected=()=>{
      selectedBox.replaceChildren();
      for(const [id,m] of chosen) {
        const row=el('div',undefined,'cg-material-selected');
        row.append(el('span',`OC ${m.oc} · ${m.item || ''} — ${m.detalle}`));
        const input=el('input');input.type='number';input.step='0.01';input.min='0.01';input.value=m.importe;
        input.className='form-control';input.disabled=blocked;input.setAttribute('aria-label',`Importe asignado a ${m.item}`);
        input.addEventListener('input',()=>{m.importe=input.value;drawTotal();});
        const remove=el('button','Quitar','btn btn-outline-danger');remove.type='button';remove.disabled=blocked;
        remove.addEventListener('click',()=>{chosen.delete(id);drawSelected();});row.append(input,remove);selectedBox.append(row);
      }
      if(!chosen.size)selectedBox.append(el('p','Sin compras seleccionadas.','cg-muted'));
      drawTotal();
    };
    const drawTotal=()=>{panel.querySelector('[data-total]').textContent='Total: '+cash([...chosen.values()].reduce((sum,m)=>sum+cents(m.importe),0n));};
    async function read(params) {
      const response=await fetch(endpoint+'?'+params,{credentials:'same-origin',headers:{Accept:'application/json'}});
      const type=response.headers.get('content-type')||'';
      if(!type.includes('application/json'))throw new Error('No se pudo consultar las compras. Revisá tu sesión.');
      const data=await response.json();if(!response.ok)throw new Error(data.error||'No se pudo consultar las compras.');return data;
    }
    const params=()=>{
      const p=new URLSearchParams({pagina:String(page),q:panel.querySelector('[data-query]').value});
      const from=panel.querySelector('[data-from]').value,to=panel.querySelector('[data-to]').value;
      if(from)p.set('desde',from);if(to)p.set('hasta',to);return p;
    };
    const choose=m=>{if(cents(m.disponible)>0n)chosen.set(String(m.id),{materialId:m.id,oc:m.oc,item:m.item,detalle:m.detalle,importe:m.disponible});};
    async function load() {
      if(busy)return;busy=true;message.textContent='Buscando compras…';
      try {
        const data=await read(params());
        if(!panel.isConnected)return;
        blocked=data.bloqueado;
        if(!initialized){data.seleccionados.forEach(m=>chosen.set(String(m.materialId),m));initialized=true;}
        pages=data.paginas;page=data.pagina;
        panel.querySelector('[data-from]').value=data.desde;panel.querySelector('[data-to]').value=data.hasta;
        results.replaceChildren();
        let currentOc=null;
        for(const m of data.compras) {
          if(currentOc!==m.ocId) {
            currentOc=m.ocId;const head=el('div',undefined,'cg-material-oc');
            head.append(el('strong',`OC ${m.oc} · ${m.proveedor} · ${m.fecha}`));
            const all=el('button','Seleccionar OC completa','btn btn-sm btn-outline-primary');all.type='button';all.disabled=blocked;
            all.addEventListener('click',async()=>{
              if(busy)return;busy=true;message.textContent='Cargando todos los materiales de la OC…';
              try {
                const p=params();p.set('ocId',m.ocId);p.set('q','');let index=0,last=1;const collected=[];
                while(index<last){p.set('pagina',index++);const batch=await read(p);collected.push(...batch.compras);last=batch.paginas;}
                collected.forEach(choose);drawSelected();message.textContent='OC seleccionada. Podés ajustar los importes antes de aplicar.';
              }catch(error){message.textContent=error.message;}finally{busy=false;}
            });head.append(all);results.append(head);
          }
          const row=el('div',undefined,'cg-material-purchase');
          const description=el('div');description.append(el('strong',`${m.item || ''} · ${m.detalle}`),el('small',`${m.cantidad ?? ''} ${m.unidad || ''} · Unitario ${amount(m.precio)} · Compra ${amount(m.importe)}`));
          const values=el('div');values.append(el('span','Disponible '+amount(m.disponible)),el('small','Asignado a otras tareas '+amount(m.usado)));
          const add=el('button','Agregar','btn btn-outline-primary');add.type='button';add.disabled=blocked||cents(m.disponible)<=0n;
          add.addEventListener('click',()=>{choose(m);drawSelected();});row.append(description,values,add);results.append(row);
        }
        if(!data.compras.length)results.append(el('p','No hay compras de materiales con estos filtros.','cg-muted'));
        panel.querySelector('[data-page]').textContent=`${data.cantidad} materiales · Página ${pages?page+1:0} de ${pages}`;
        panel.querySelector('[data-prev]').disabled=page<=0;panel.querySelector('[data-next]').disabled=page+1>=pages;
        panel.querySelector('[data-apply]').disabled=blocked;
        drawSelected();
        const field=costs.querySelector('[name="materialesAsignados"]');
        if(chosen.size)field.readOnly=true;
        message.textContent=blocked?'Materiales bloqueados: esta tarea ya fue certificada.':'';
      }catch(error){message.textContent=error.message;}finally{busy=false;}
    }
    panel.addEventListener('toggle',()=>{if(panel.open&&!initialized)load();});
    panel.querySelector('[data-search]').addEventListener('click',()=>{page=0;load();});
    panel.querySelector('[data-query]').addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();page=0;load();}});
    panel.querySelector('[data-prev]').addEventListener('click',()=>{if(!busy){page--;load();}});
    panel.querySelector('[data-next]').addEventListener('click',()=>{if(!busy){page++;load();}});
    panel.querySelector('[data-apply]').addEventListener('click',()=>{
      if(blocked||!initialized||busy)return;
      if([...selectedBox.querySelectorAll('input')].some(i=>!i.reportValidity()))return;
      costs.querySelectorAll('[data-material-payload]').forEach(n=>n.remove());
      const hidden=(name,value)=>{const n=el('input');n.type='hidden';n.name=name;n.value=value;n.dataset.materialPayload='true';costs.append(n);};
      hidden('materialSelectionPresent','true');
      chosen.forEach(m=>{hidden('materialIds',m.materialId);hidden('materialImportes',m.importe);});
      const total=[...chosen.values()].reduce((sum,m)=>sum+cents(m.importe),0n);
      const field=costs.querySelector('[name="materialesAsignados"]');field.value=(Number(total)/100).toFixed(2);field.readOnly=chosen.size>0;
      costs.querySelector('[name="subtotalManual"]').value='';
      const check=costs.querySelector('[name="materialesConfirmados"]');if(check)check.checked=true;
      field.dispatchEvent(new Event('input',{bubbles:true}));message.textContent='Selección aplicada. Guardá la tarea para confirmar los vínculos y los costos.';
    });
  }

  function initCosts(scope=document) {
  const costs=scope.querySelector('#cg-cost-form');
  initMaterialPicker(costs);
  if(costs?.querySelector('#cg-materials-locked')?.value==='true') {
    ['materialesAsignados','materialesSuministradosEmpresa','materialesAdicionalesEtapa','subtotalManual'].forEach(name=>{
      const input=costs.querySelector(`[name="${name}"]`);if(input){input.readOnly=true;input.title='Bloqueado: la tarea ya fue certificada';}
    });
  }
  const value=name=>costs?.querySelector(`[name="${name}"]`)?.value || '0';
  const updateCosts=()=>{
    if(!costs)return;
    const manual=costs.querySelector('[name="subtotalManual"]').value;
    const subtotal=manual!==''?cents(manual):['costoManoObra','materialesAsignados','servicios','materialesSuministradosEmpresa'].reduce((acc,k)=>acc+cents(value(k)),0n);
    const structure=rounded(subtotal*cents(value('costoEstructuralPorcentaje')),10000n); const benefit=rounded(subtotal*cents(value('beneficioEmpresarialPorcentaje')),10000n);
    scope.querySelector('#cg-cost-subtotal').textContent=cash(subtotal); scope.querySelector('#cg-cost-structure').textContent=cash(structure); scope.querySelector('#cg-cost-benefit').textContent=cash(benefit); scope.querySelector('#cg-cost-total').textContent=cash(subtotal+structure+benefit);
  };
  costs?.querySelectorAll('.cg-cost-field').forEach(i=>i.addEventListener('input',updateCosts));
  scope.querySelector('#cg-calculate-mo')?.addEventListener('click',()=>{costs.querySelector('[name="costoManoObra"]').value=(Number(rounded(cents(value('cantidad'))*cents(value('precioUnitario')),100n))/100).toFixed(2);updateCosts();});
  }
  initCosts();

  const prepareRow=row=>{
    const ready=row.querySelector('[data-materiales-preparados]')?.dataset.materialesPreparados;
    if(ready===undefined)return;
    const input=row.querySelector('.cg-percent');
    if(input){input.readOnly=ready!=='true';input.title=ready==='true'?'Avance actual':'Primero revisá y confirmá los materiales';}
    const fill=row.querySelector('.cg-fill');if(fill && ready!=='true')fill.disabled=true;
  };
  ledger?.querySelectorAll('tbody tr').forEach(prepareRow);
  if (ledger) {
    const materialsView=document.createElement('dialog');
    materialsView.className='cg-cost-dialog cg-material-detail';
    materialsView.setAttribute('aria-labelledby','cg-material-detail-title');
    materialsView.innerHTML='<header class="cg-cost-dialog-header"><h2 id="cg-material-detail-title">Materiales vinculados</h2><button type="button" class="btn btn-outline-secondary" aria-label="Cerrar detalle de materiales"><i class="bi bi-x-lg"></i></button></header><div class="cg-cost-dialog-body"><p data-task-name></p><p role="status"></p><div data-material-lines></div></div>';
    document.body.append(materialsView);
    materialsView.querySelector('header button').addEventListener('click',()=>materialsView.close());
    let materialsRequest;
    materialsView.addEventListener('close',()=>materialsRequest?.abort());
    ledger.addEventListener('click',async event=>{
      const button=event.target.closest('[data-materials-url]');if(!button)return;
      materialsRequest?.abort();materialsRequest=new AbortController();
      const signal=materialsRequest.signal;
      const body=materialsView.querySelector('[data-material-lines]');
      const status=materialsView.querySelector('[role="status"]');
      body.replaceChildren();status.textContent='Cargando materiales…';
      materialsView.querySelector('[data-task-name]').textContent=button.dataset.task;
      materialsView.showModal();
      try {
        const response=await fetch(button.dataset.materialsUrl,{credentials:'same-origin',headers:{Accept:'application/json'},signal});
        if(!response.ok || !response.headers.get('content-type')?.includes('application/json'))throw new Error('No se pudo cargar el detalle de materiales. Revisá tu sesión e intentá nuevamente.');
        const data=await response.json();if(signal.aborted)return;
        if(!data.seleccionados.length){status.textContent='Este importe fue cargado manualmente o importado, sin compras de materiales vinculadas.';return;}
        const list=document.createElement('ul');list.className='cg-material-detail-list';
        let total=0n;
        data.seleccionados.forEach(material=>{
          const row=document.createElement('li');
          const description=document.createElement('div');
          const reference=document.createElement('small');reference.textContent=`OC ${material.oc} · Ítem ${material.item || '—'}`;
          const name=document.createElement('span');name.textContent=material.detalle;
          description.append(reference,name);
          const amount=document.createElement('strong');amount.textContent=cash(cents(material.importe));
          row.append(description,amount);list.append(row);total+=cents(material.importe);
        });
        const summary=document.createElement('p');summary.className='cg-material-detail-total';summary.textContent='Total asignado: '+cash(total);
        body.append(list,summary);status.textContent=`${data.seleccionados.length} materiales vinculados${data.bloqueado?' · Asignación bloqueada':''}`;
      }catch(error){if(error.name!=='AbortError')status.textContent=error.message;}
    });
    const modal=document.createElement('dialog');
    modal.className='cg-cost-dialog';
    modal.setAttribute('aria-labelledby','cg-cost-dialog-title');
    modal.innerHTML='<header class="cg-cost-dialog-header"><h2 id="cg-cost-dialog-title">Datos y costos de la tarea</h2><button type="button" class="btn btn-outline-secondary" aria-label="Cerrar"><i class="bi bi-x-lg"></i></button></header><div class="cg-cost-dialog-body"></div><p class="cg-cost-dialog-status" role="status"></p>';
    document.body.append(modal);
    const content=modal.querySelector('.cg-cost-dialog-body');
    const status=modal.querySelector('[role="status"]');
    let saving=false;
    const close=()=>{if(!saving)modal.close();};
    modal.querySelector('header button').addEventListener('click',close);
    modal.addEventListener('cancel',e=>{if(saving)e.preventDefault();});
    let activeRow;
    const parse=html=>new DOMParser().parseFromString(html,'text/html');
    const renderForm=doc=>{
      const form=doc.querySelector('#cg-cost-form');
      if(!form)throw new Error('No se pudo cargar el formulario. Revisá tu sesión e intentá nuevamente.');
      content.replaceChildren(document.importNode(form,true));
      const mounted=content.querySelector('form');
      mounted.querySelectorAll('a').forEach(a=>{a.removeAttribute('href');a.setAttribute('role','button');a.tabIndex=0;a.addEventListener('click',close);a.addEventListener('keydown',e=>{if(e.key==='Enter')close();});});
      initCosts(content);
      mounted.addEventListener('submit',save);
      content.querySelector('input:not([type="hidden"]),textarea')?.focus();
    };
    async function save(event) {
      event.preventDefault();
      if(saving)return;
      const form=event.currentTarget;
      if(!form.reportValidity())return;
      saving=true;status.textContent='Guardando costos…';
      const buttons=[...form.querySelectorAll('button')];buttons.forEach(b=>b.disabled=true);
      try {
        const response=await fetch(form.action,{method:'POST',body:new URLSearchParams(new FormData(form)),credentials:'same-origin'});
        if(!response.ok)throw new Error('No se pudieron guardar los costos. Intentá nuevamente.');
        const doc=parse(await response.text());
        const error=doc.querySelector('.alert-danger');
        if(error){status.textContent=error.textContent.trim();return;}
        const fresh=doc.getElementById(activeRow.id);
        if(!fresh || !doc.querySelector('#cg-ledger'))throw new Error('No se pudo confirmar el guardado. Revisá tu sesión.');
        const percentage=activeRow.querySelector('.cg-percent')?.value;
        const wasHidden=activeRow.hidden;
        const replacement=document.importNode(fresh,true);
        activeRow.replaceWith(replacement);activeRow=replacement;replacement.hidden=wasHidden;
        prepareRow(replacement);
        const input=replacement.querySelector('.cg-percent');
        if(input){input.value=percentage;input.addEventListener('input',markDirty);}
        replacement.querySelector('.cg-fill')?.addEventListener('click',()=>{input.value=replacement.dataset.pendiente;markDirty();});
        for(const id of ['cg-total-current','cg-total-acc']) {
          const next=doc.getElementById(id);const current=document.getElementById(id);
          if(next && current)Object.assign(current.dataset,next.dataset);
        }
        const budget=document.querySelector('.cg-metrics article:last-child');
        const nextBudget=doc.querySelector('.cg-metrics article:last-child');
        if(budget && nextBudget)budget.replaceChildren(...Array.from(nextBudget.childNodes).map(n=>document.importNode(n,true)));
        recalc();filterRows();
        if(dirty)markDirty();
        saving=false;modal.close();
      } catch(error){status.textContent=error.message;}
      finally{saving=false;buttons.forEach(b=>b.disabled=false);}
    }
    ledger.addEventListener('click',async event=>{
      const link=event.target.closest('a.cg-edit-link');
      if(!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)return;
      event.preventDefault();activeRow=link.closest('tr');
      content.replaceChildren();status.textContent='Cargando costos…';modal.showModal();
      try {
        const response=await fetch(link.href,{credentials:'same-origin'});
        if(!response.ok)throw new Error('No se pudo abrir la tarea.');
        const doc=parse(await response.text());
        if(!modal.open)return;
        renderForm(doc);status.textContent='';
      }catch(error){status.textContent=error.message;}
    });
  }
})();
