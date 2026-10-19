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
  function initCosts(scope=document) {
  const costs=scope.querySelector('#cg-cost-form');
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

  if (ledger) {
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
