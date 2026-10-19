(() => {
    'use strict';
    const sourcePath = '/certificados-quincenales';
    const contextParam = 'desdeQuincenas';
    const restoreParam = 'retomarQuincenas';
    const keyPrefix = 'obraControl.quincenas.return.';
    const current = new URL(window.location.href);
    const isOc = path => path === '/oc' || path.startsWith('/oc/');
    const read = token => {
        if (!token || !/^[a-zA-Z0-9-]{1,80}$/.test(token)) return null;
        try {
            const saved = JSON.parse(sessionStorage.getItem(keyPrefix + token));
            if (!saved || typeof saved.url !== 'string') return null;
            const target = new URL(saved.url, current.origin);
            if (target.origin !== current.origin || target.pathname !== sourcePath) return null;
            return saved;
        } catch (_) { return null; }
    };

    if (current.pathname === sourcePath) {
        const page = document.querySelector('[data-quincenas-url]');
        if (!page) return;
        const saved = read(current.searchParams.get(restoreParam));
        const token = saved ? current.searchParams.get(restoreParam) : crypto.randomUUID();
        const certificates = Array.from(page.querySelectorAll('[data-certificado-id]'));
        const save = () => {
            try {
                sessionStorage.setItem(keyPrefix + token, JSON.stringify({
                    url: page.dataset.quincenasUrl,
                    open: certificates.filter(c => c.open).map(c => c.dataset.certificadoId),
                    scroll: window.scrollY,
                    search: document.querySelector('#q-provider-search')?.value || ''
                }));
            } catch (_) { /* La navegacion normal sigue disponible sin almacenamiento. */ }
        };
        if (saved) {
            certificates.forEach(c => { c.open = Array.isArray(saved.open) && saved.open.includes(c.dataset.certificadoId); });
            const search = document.querySelector('#q-provider-search');
            if (search && typeof saved.search === 'string') {
                search.value = saved.search;
                search.dispatchEvent(new Event('input', { bubbles: true }));
            }
            const expand = document.querySelector('#q-expand');
            if (expand) {
                const allOpen = certificates.length > 0 && certificates.every(c => c.open);
                expand.textContent = allOpen ? 'Ocultar tareas' : 'Ver todas las tareas';
                expand.setAttribute('aria-expanded', String(allOpen));
            }
            window.addEventListener('pageshow', () => {
                requestAnimationFrame(() => requestAnimationFrame(() => {
                    window.scrollTo(0, Number.isFinite(saved.scroll) ? Math.max(0, saved.scroll) : 0);
                }));
            }, { once: true });
        }
        page.querySelectorAll('a[href]').forEach(link => {
            const target = new URL(link.href, current.origin);
            if (target.origin !== current.origin || !isOc(target.pathname)) return;
            target.searchParams.set(contextParam, token);
            link.href = target.href;
            ['click', 'auxclick', 'contextmenu'].forEach(event => link.addEventListener(event, save));
        });
        window.addEventListener('pagehide', save);
        return;
    }

    if (!isOc(current.pathname)) return;
    let token = current.searchParams.get(contextParam);
    // Conservar el origen tambien despues de guardar un formulario y su redireccion.
    if (!token && document.referrer) {
        try {
            const previous = new URL(document.referrer);
            if (previous.origin === current.origin && isOc(previous.pathname)) token = previous.searchParams.get(contextParam);
        } catch (_) { }
    }
    const saved = read(token);
    if (!saved) return;
    if (!current.searchParams.has(contextParam)) {
        current.searchParams.set(contextParam, token);
        window.history.replaceState(window.history.state, '', current.href);
    }
    const returnUrl = new URL(saved.url, current.origin);
    returnUrl.searchParams.set(restoreParam, token);
    const button = document.createElement('a');
    button.className = 'btn btn-outline-primary mb-3 no-print';
    button.href = returnUrl.href;
    button.textContent = '← Volver a Quincenas';
    document.querySelector('main')?.prepend(button);

    document.querySelectorAll('a[href], form[action]').forEach(element => {
        const attribute = element.tagName === 'FORM' ? 'action' : 'href';
        const target = new URL(element.getAttribute(attribute), current.origin);
        if (target.origin === current.origin && target.pathname === sourcePath && attribute === 'href') {
            element.setAttribute(attribute, returnUrl.href);
            return;
        }
        if (target.origin !== current.origin || !isOc(target.pathname)) return;
        target.searchParams.set(contextParam, token);
        element.setAttribute(attribute, target.href);
    });
})();
