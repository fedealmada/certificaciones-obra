(() => {
    const search=document.getElementById('launch-search');
    if(!search) return;
    const cards=[...document.querySelectorAll('[data-module-search]')];
    const clear=document.getElementById('launch-clear');
    const count=document.getElementById('launch-count');
    const empty=document.getElementById('launch-empty');
    const normalize=value=>value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es').trim();
    function filter() {
        const terms=normalize(search.value).split(/\s+/).filter(Boolean);
        let visible=0;
        cards.forEach(card=>{const text=normalize(card.dataset.moduleSearch);card.hidden=!terms.every(term=>text.includes(term));if(!card.hidden) visible++;});
        count.textContent=visible+' '+(visible===1?'módulo':'módulos');
        empty.hidden=visible!==0;clear.hidden=search.value.length===0;
    }
    search.addEventListener('input',filter);
    search.addEventListener('keydown',event=>{if(event.key==='Escape'){search.value='';filter();}});
    clear.addEventListener('click',()=>{search.value='';filter();search.focus();});
    filter();
})();
