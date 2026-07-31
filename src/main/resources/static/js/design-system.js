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
            const duration = isMainProgress ? 2600 : 1800;
            const delay = isMainProgress ? 260 : 420 + Math.min(index, 8) * 90;

            bar.dataset.progressReady = 'true';
            bar.style.setProperty('--progress-target', `${value}%`);
            bar.style.transition = 'none';
            bar.style.width = '0%';
            bar.setAttribute('aria-valuenow', String(value));
            bar.classList.toggle('is-empty', value <= 0);
            bar.classList.toggle('is-warning', value > 0 && value < 50);
            bar.classList.toggle('is-primary', value >= 50 && value < 100);
            bar.classList.toggle('is-complete', value >= 100);

            if (value <= 0) {
                return;
            }

            window.setTimeout(() => {
                bar.style.transition = `width ${duration}ms cubic-bezier(0.16, 1, 0.3, 1), background-color 180ms ease`;
                window.requestAnimationFrame(() => {
                    bar.style.width = `${value}%`;
                    bar.classList.add('is-loaded');
                });
            }, delay);
        });
    }

    function init() {
        initTheme();
        initSidebar();
        initThemeControls();
        initBootstrapHelpers();
        initSharedDialogs();
        initDesignTables();
        initProgressBars();
    }

    window.ObraDesignSystem = {
        closeDialog,
        init,
        initProgressBars,
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
