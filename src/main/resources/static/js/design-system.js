(() => {
    const STORAGE_THEME = 'obraControl.theme';
    const STORAGE_PALETTE = 'obraControl.palette';
    const DEFAULT_PALETTE = 'blue';

    const root = document.documentElement;

    function preferredTheme() {
        const saved = localStorage.getItem(STORAGE_THEME);
        if (saved === 'dark' || saved === 'light') {
            return saved;
        }
        return 'light';
    }

    function setTheme(theme) {
        const nextTheme = theme === 'dark' ? 'dark' : 'light';
        root.dataset.theme = nextTheme;
        root.dataset.bsTheme = nextTheme;
        localStorage.setItem(STORAGE_THEME, nextTheme);

        document.querySelectorAll('.theme-toggle-button').forEach(button => {
            const icon = button.querySelector('i');
            if (icon) {
                icon.className = nextTheme === 'dark' ? 'bi bi-sun' : 'bi bi-moon-stars';
            }
            const label = nextTheme === 'dark' ? 'Modo claro' : 'Modo oscuro';
            button.title = label;
            button.setAttribute('aria-label', label);
        });
        syncThemeControls();
    }

    function setPalette(palette) {
        const nextPalette = palette || DEFAULT_PALETTE;
        root.dataset.palette = nextPalette;
        localStorage.setItem(STORAGE_PALETTE, nextPalette);
        syncThemeControls();
    }

    function syncThemeControls() {
        const theme = root.dataset.theme || 'light';
        const palette = root.dataset.palette || DEFAULT_PALETTE;

        document.querySelectorAll('[data-theme-option]').forEach(button => {
            button.classList.toggle('is-selected', button.dataset.themeOption === theme);
        });

        document.querySelectorAll('[data-palette-option]').forEach(button => {
            button.classList.toggle('is-selected', button.dataset.paletteOption === palette);
        });
    }

    function initTheme() {
        setTheme(root.dataset.theme || preferredTheme());
        setPalette(root.dataset.palette || localStorage.getItem(STORAGE_PALETTE) || DEFAULT_PALETTE);
    }

    function initSidebar() {
        document.querySelectorAll('.app-sidebar').forEach(sidebar => {
            const currentPath = window.location.pathname.replace(/\/$/, '') || '/';
            let bestLink = null;
            let bestLength = -1;

            sidebar.querySelectorAll('.app-nav-links a.nav-link[href]').forEach(link => {
                const href = link.getAttribute('href');
                if (!href || href === '#') {
                    return;
                }

                const url = new URL(href, window.location.origin);
                const linkPath = url.pathname.replace(/\/$/, '') || '/';
                const matches = linkPath === '/'
                    ? currentPath === '/'
                    : currentPath === linkPath || currentPath.startsWith(`${linkPath}/`);

                link.classList.remove('is-active');
                link.removeAttribute('aria-current');

                if (matches && linkPath.length > bestLength) {
                    bestLink = link;
                    bestLength = linkPath.length;
                }
            });

            if (bestLink) {
                bestLink.classList.add('is-active');
                bestLink.setAttribute('aria-current', 'page');
            }

            sidebar.addEventListener('pointerenter', () => sidebar.classList.add('is-open'));
            sidebar.addEventListener('pointerleave', event => {
                if (!event.relatedTarget || !sidebar.contains(event.relatedTarget)) {
                    sidebar.classList.remove('is-open');
                }
            });
            sidebar.addEventListener('click', event => {
                if (!event.target.closest('a, button')) {
                    sidebar.classList.add('is-open');
                }
            });
        });
    }

    function initThemeControls() {
        document.querySelectorAll('.theme-toggle-button').forEach(button => {
            button.addEventListener('click', () => {
                setTheme(root.dataset.theme === 'dark' ? 'light' : 'dark');
            });
        });

        document.querySelectorAll('[data-theme-option]').forEach(button => {
            button.addEventListener('click', () => setTheme(button.dataset.themeOption));
        });

        document.querySelectorAll('[data-palette-option]').forEach(button => {
            button.addEventListener('click', () => setPalette(button.dataset.paletteOption));
        });

        window.addEventListener('obra-theme-change', event => {
            if (event.detail?.theme) {
                setTheme(event.detail.theme);
            }
            if (event.detail?.palette) {
                setPalette(event.detail.palette);
            }
        });
        syncThemeControls();
    }

    function initBootstrapHelpers() {
        if (!window.bootstrap) {
            return;
        }

        document.querySelectorAll('[data-bs-toggle="tooltip"], [title][data-ds-tooltip]').forEach(element => {
            window.bootstrap.Tooltip.getOrCreateInstance(element);
        });
    }

    function openDialog(dialog) {
        if (!dialog) {
            return;
        }
        dialog.classList.remove('d-none', 'is-closing');
        dialog.setAttribute('aria-hidden', 'false');
        document.body.classList.add('document-modal-open');
        requestAnimationFrame(() => dialog.classList.add('is-open'));
    }

    function closeDialog(dialog) {
        if (!dialog) {
            return;
        }
        dialog.classList.add('is-closing');
        dialog.classList.remove('is-open');
        dialog.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('document-modal-open');
        window.setTimeout(() => {
            dialog.classList.add('d-none');
            dialog.classList.remove('is-closing');
        }, 180);
    }

    function initSharedDialogs() {
        document.addEventListener('click', event => {
            const opener = event.target.closest('[data-ds-dialog-open]');
            if (opener) {
                event.preventDefault();
                openDialog(document.querySelector(opener.dataset.dsDialogOpen));
                return;
            }

            const closer = event.target.closest('[data-ds-dialog-close]');
            if (closer) {
                event.preventDefault();
                closeDialog(closer.closest('.document-quick-view, .ds-dialog'));
            }
        });

        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                document.querySelectorAll('.document-quick-view.is-open, .ds-dialog.is-open').forEach(closeDialog);
            }
        });
    }

    function normalizeTableRows(table) {
        const rows = [...table.querySelectorAll('tbody tr:not(.d-none)')];
        rows.forEach((row, index) => {
            row.classList.toggle('is-visible-even', index % 2 === 1);
            row.classList.toggle('is-visible-odd', index % 2 === 0);
        });
    }

    function initDesignTables() {
        document.querySelectorAll('[data-ds-table]').forEach(table => normalizeTableRows(table));
    }

    function initProgressBars() {
        document.querySelectorAll('[data-progress]').forEach((bar, index) => {
            if (bar.dataset.progressReady === 'true') {
                return;
            }

            const rawValue = Number.parseFloat(String(bar.dataset.progress || '0').replace(',', '.'));
            const value = Number.isFinite(rawValue) ? Math.max(0, Math.min(100, rawValue)) : 0;
            const isMainProgress = bar.classList.contains('oc-total-progress-fill');
            const duration = isMainProgress ? 4600 : 3400;
            const delay = isMainProgress ? 360 : 520 + Math.min(index, 8) * 110;

            bar.dataset.progressReady = 'true';
            bar.style.setProperty('--progress-target', `${value}%`);
            bar.style.transition = 'none';
            bar.style.width = '0%';
            bar.style.transformOrigin = 'left center';
            bar.style.transform = 'scaleX(0)';
            bar.setAttribute('aria-valuenow', String(value));
            bar.classList.toggle('is-empty', value <= 0);
            bar.classList.toggle('is-warning', value > 0 && value < 50);
            bar.classList.toggle('is-primary', value >= 50 && value < 100);
            bar.classList.toggle('is-complete', value >= 100);

            if (value <= 0) {
                return;
            }

            window.setTimeout(() => {
                bar.style.width = `${value}%`;
                bar.style.transition = `transform ${duration}ms cubic-bezier(0.18, 0.92, 0.16, 1), background-color 180ms ease`;
                window.requestAnimationFrame(() => {
                    bar.style.transform = 'scaleX(1)';
                    bar.classList.add('is-loaded');
                });
            }, delay);
        });
    }

    function normalizeText(value) {
        return String(value || '')
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '');
    }

    function setDialogText(dialog, selector, value) {
        const element = dialog?.querySelector(selector);
        if (element) {
            element.textContent = value || '-';
        }
    }

    function setDocumentQuickField(dialog, name, value) {
        setDialogText(dialog, `[data-doc-field="${name}"]`, value);
    }

    function setDocumentQuickNote(dialog, name, label, value) {
        const element = dialog?.querySelector(`[data-doc-note="${name}"]`);
        if (!element) {
            return false;
        }

        const hasValue = Boolean(value && String(value).trim());
        element.classList.toggle('d-none', !hasValue);
        element.replaceChildren();
        if (hasValue) {
            const strong = document.createElement('strong');
            strong.textContent = `${label}:`;
            element.append(strong, ` ${value}`);
        }
        return hasValue;
    }

    function openDocumentQuickViewFrom(target) {
        const dialog = document.getElementById('document-quick-view');
        if (!dialog || !target) {
            return;
        }

        const data = target.dataset;
        setDialogText(dialog, '#document-quick-kicker', data.docVinculo);
        setDialogText(dialog, '#document-quick-title', data.docName);
        setDialogText(dialog, '#document-quick-subtitle', data.docSujetoNombre);

        const status = dialog.querySelector('#document-quick-status');
        if (status) {
            status.textContent = data.docStatus || 'Estado';
            status.className = `doc-status-pill ${data.docStatusClass || 'is-muted'}`;
        }

        setDocumentQuickField(dialog, 'proveedor', data.docProveedor);
        setDocumentQuickField(dialog, 'sujeto', data.docSujeto);
        setDocumentQuickField(dialog, 'presentacion', data.docPresentacion);
        setDocumentQuickField(dialog, 'vencimiento', data.docVencimiento);
        setDocumentQuickField(dialog, 'dias', data.docDias);
        setDocumentQuickField(dialog, 'mensual', data.docMensual);
        setDocumentQuickField(dialog, 'ingreso', data.docIngreso);
        setDocumentQuickField(dialog, 'pdf', data.docPdf);
        setDocumentQuickField(dialog, 'print', data.docPrint);
        setDocumentQuickField(dialog, 'fisicoHasta', data.docFisicoHasta);
        setDocumentQuickField(dialog, 'fisicoVerificado', data.docFisicoVerificado);
        setDocumentQuickField(dialog, 'creacion', data.docCreacion);
        setDocumentQuickField(dialog, 'actualizacion', data.docActualizacion);

        const printAlert = dialog.querySelector('#document-quick-print-alert');
        if (printAlert) {
            const physicalClass = data.docFisicoClass || 'is-danger';
            printAlert.className = `document-quick-print-alert ${physicalClass === 'is-ok' ? 'is-ok' : 'is-missing'}`;
            const icon = printAlert.querySelector('i');
            if (icon) {
                icon.className = physicalClass === 'is-ok' ? 'bi bi-folder-check' : (physicalClass === 'is-warning' ? 'bi bi-printer' : 'bi bi-folder-x');
            }
            const title = printAlert.querySelector('strong');
            if (title) {
                title.textContent = `${data.docFisico || 'Carpeta fisica'} - ${data.docFisicoAccion || ''}`;
            }
            const subtitle = printAlert.querySelector('small');
            if (subtitle) {
                subtitle.textContent = data.docUbicacion || 'Sin ubicacion fisica cargada';
            }
        }

        const hasArchivo = setDocumentQuickNote(dialog, 'archivo', 'Archivo', data.docArchivo);
        const hasVehiculo = setDocumentQuickNote(dialog, 'vehiculo', 'Vehiculo / maquinaria', data.docVehiculo);
        const hasObservacion = setDocumentQuickNote(dialog, 'observacion', 'Observacion', data.docObservacion);
        dialog.querySelector('#document-quick-notes')?.classList.toggle('d-none', !(hasArchivo || hasVehiculo || hasObservacion));

        const pdf = dialog.querySelector('#document-quick-pdf');
        if (pdf) {
            pdf.href = data.docPdfUrl || '#';
            pdf.classList.toggle('disabled', !data.docPdfUrl);
            pdf.setAttribute('aria-disabled', data.docPdfUrl ? 'false' : 'true');
        }

        const edit = dialog.querySelector('#document-quick-edit');
        if (edit) edit.href = data.docEditUrl || '#';
        const duplicate = dialog.querySelector('#document-quick-duplicate');
        if (duplicate) duplicate.href = data.docDuplicateUrl || '#';
        const full = dialog.querySelector('#document-quick-full');
        if (full) full.href = data.docUrl || '#';

        openDialog(dialog);
        window.setTimeout(() => dialog.querySelector('.document-quick-modal')?.focus?.(), 80);
    }

    function initDocumentQuickView() {
        document.addEventListener('click', event => {
            const opener = event.target.closest('[data-doc-quick-trigger]');
            if (opener) {
                event.preventDefault();
                openDocumentQuickViewFrom(opener.closest('[data-doc-id]') || opener);
                return;
            }

            const closer = event.target.closest('[data-doc-modal-close]');
            if (closer) {
                event.preventDefault();
                closeDialog(closer.closest('.document-quick-view'));
            }
        });
    }

    function initDocumentFolderFilters() {
        document.querySelectorAll('[data-doc-filter-scope]').forEach(scope => {
            const search = scope.querySelector('[data-doc-filter-search]');
            const status = scope.querySelector('[data-doc-filter-status]');
            const rows = [...scope.querySelectorAll('[data-doc-filter-row]')];
            const empty = scope.querySelector('[data-doc-filter-empty]');
            const table = scope.querySelector('[data-ds-table]');

            if (!rows.length || (!search && !status)) {
                return;
            }

            const applyFilters = () => {
                const query = normalizeText(search?.value);
                const selectedStatus = status?.value || '';
                let visible = 0;

                rows.forEach(row => {
                    const matchesText = !query || normalizeText(row.dataset.docSearch).includes(query);
                    const matchesStatus = !selectedStatus || row.dataset.docStatusClass === selectedStatus;
                    const shouldShow = matchesText && matchesStatus;
                    row.classList.toggle('d-none', !shouldShow);
                    if (shouldShow) {
                        visible += 1;
                    }
                });

                empty?.classList.toggle('d-none', visible !== 0);
                if (table) {
                    normalizeTableRows(table);
                }
            };

            search?.addEventListener('input', applyFilters);
            status?.addEventListener('change', applyFilters);
            applyFilters();
        });
    }

    function initContractorFolderTabs() {
        const tabRoot = document.querySelector('[data-folder-tabs]');
        const panelRoot = document.querySelector('[data-folder-tab-panels]');
        if (!tabRoot || !panelRoot) {
            return;
        }

        const buttons = [...tabRoot.querySelectorAll('[data-folder-tab]')];
        const panels = [...panelRoot.querySelectorAll('[data-folder-panel]')];

        const setActiveTab = (tab) => {
            buttons.forEach(button => {
                const active = button.dataset.folderTab === tab;
                button.classList.toggle('is-active', active);
                button.setAttribute('aria-selected', active ? 'true' : 'false');
            });
            panels.forEach(panel => {
                panel.classList.toggle('is-active', panel.dataset.folderPanel === tab);
            });
        };

        buttons.forEach(button => {
            button.setAttribute('role', 'tab');
            button.addEventListener('click', () => setActiveTab(button.dataset.folderTab));
        });
        panelRoot.setAttribute('role', 'tabpanel');
        setActiveTab(buttons.find(button => button.classList.contains('is-active'))?.dataset.folderTab || buttons[0]?.dataset.folderTab || 'documentos');
    }

    function init() {
        initTheme();
        initSidebar();
        initThemeControls();
        initBootstrapHelpers();
        initSharedDialogs();
        initDesignTables();
        initProgressBars();
        initDocumentQuickView();
        initDocumentFolderFilters();
        initContractorFolderTabs();
    }

    window.ObraDesignSystem = {
        closeDialog,
        init,
        initProgressBars,
        openDocumentQuickViewFrom,
        normalizeTableRows,
        openDialog,
        syncThemeControls,
        setPalette,
        setTheme
    };

    initTheme();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init, { once: true });
    } else {
        init();
    }
})();
