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
  const costs=document.querySelector('#cg-cost-form');
  const value=name=>costs?.querySelector(`[name="${name}"]`)?.value || '0';
  const updateCosts=()=>{
    if(!costs)return;
    const manual=costs.querySelector('[name="subtotalManual"]').value;
    const subtotal=manual!==''?cents(manual):['costoManoObra','materialesAsignados','servicios','materialesSuministradosEmpresa'].reduce((acc,k)=>acc+cents(value(k)),0n);
    const structure=rounded(subtotal*cents(value('costoEstructuralPorcentaje')),10000n); const benefit=rounded(subtotal*cents(value('beneficioEmpresarialPorcentaje')),10000n);
    document.querySelector('#cg-cost-subtotal').textContent=cash(subtotal); document.querySelector('#cg-cost-structure').textContent=cash(structure); document.querySelector('#cg-cost-benefit').textContent=cash(benefit); document.querySelector('#cg-cost-total').textContent=cash(subtotal+structure+benefit);
  };
  costs?.querySelectorAll('.cg-cost-field').forEach(i=>i.addEventListener('input',updateCosts));
  document.querySelector('#cg-calculate-mo')?.addEventListener('click',()=>{costs.querySelector('[name="costoManoObra"]').value=(Number(rounded(cents(value('cantidad'))*cents(value('precioUnitario')),100n))/100).toFixed(2);updateCosts();});
})();
