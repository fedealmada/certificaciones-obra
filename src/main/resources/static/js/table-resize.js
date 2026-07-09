(function () {
    const STORAGE_PREFIX = 'obraControl:table-widths:v2:';
    const MIN_WIDTH = 56;

    function tableKey(table, index) {
        return STORAGE_PREFIX + location.pathname + ':' + (table.id || 'table-' + index);
    }

    function buildColgroup(table) {
        const headers = Array.from(table.querySelectorAll('thead th'));
        if (!headers.length) {
            return null;
        }

        let colgroup = table.querySelector('colgroup[data-resizable-cols]');
        if (!colgroup) {
            colgroup = document.createElement('colgroup');
            colgroup.dataset.resizableCols = 'true';
            headers.forEach(() => colgroup.appendChild(document.createElement('col')));
            table.insertBefore(colgroup, table.firstChild);
        }
        return colgroup;
    }

    function applyStoredWidths(table, key) {
        const stored = localStorage.getItem(key);
        if (!stored) {
            return;
        }
        const widths = JSON.parse(stored);
        const cols = Array.from(table.querySelectorAll('colgroup[data-resizable-cols] col'));
        const container = table.closest('.table-responsive') || table.parentElement;
        const containerWidth = Math.floor(container.getBoundingClientRect().width);
        const totalWidth = widths.reduce((total, width) => total + width, 0);
        const scale = totalWidth > containerWidth && containerWidth > 0 ? containerWidth / totalWidth : 1;
        widths.forEach((width, index) => {
            if (cols[index] && width >= MIN_WIDTH) {
                cols[index].style.width = Math.max(MIN_WIDTH, Math.floor(width * scale)) + 'px';
            }
        });
    }

    function saveWidths(table, key) {
        const cols = Array.from(table.querySelectorAll('colgroup[data-resizable-cols] col'));
        const widths = cols.map(col => Math.round(col.getBoundingClientRect().width));
        localStorage.setItem(key, JSON.stringify(widths));
    }

    function initialWidth(header, col) {
        const current = col.getBoundingClientRect().width || header.getBoundingClientRect().width;
        return Math.max(MIN_WIDTH, Math.round(current));
    }

    function makeResizable(table, tableIndex) {
        if (table.dataset.noResize === 'true' || table.classList.contains('itemizado-excel')) {
            return;
        }
        const headers = Array.from(table.querySelectorAll('thead th'));
        if (!headers.length || table.dataset.resizableReady === 'true') {
            return;
        }
        const colgroup = buildColgroup(table);
        if (!colgroup) {
            return;
        }

        const key = tableKey(table, tableIndex);
        const cols = Array.from(colgroup.children);
        headers.forEach((header, index) => {
            header.classList.add('resizable-th');
            if (!cols[index].style.width) {
                cols[index].style.width = initialWidth(header, cols[index]) + 'px';
            }

            const handle = document.createElement('span');
            handle.className = 'column-resize-handle';
            handle.setAttribute('aria-hidden', 'true');
            header.appendChild(handle);

            handle.addEventListener('mousedown', event => {
                event.preventDefault();
                event.stopPropagation();
                const startX = event.clientX;
                const startWidth = cols[index].getBoundingClientRect().width;
                table.classList.add('is-resizing-column');
                document.body.classList.add('is-resizing-table-column');

                function onMove(moveEvent) {
                    const nextWidth = Math.max(MIN_WIDTH, Math.round(startWidth + moveEvent.clientX - startX));
                    cols[index].style.width = nextWidth + 'px';
                }

                function onUp() {
                    table.classList.remove('is-resizing-column');
                    document.body.classList.remove('is-resizing-table-column');
                    saveWidths(table, key);
                    document.removeEventListener('mousemove', onMove);
                    document.removeEventListener('mouseup', onUp);
                }

                document.addEventListener('mousemove', onMove);
                document.addEventListener('mouseup', onUp);
            });
        });
        table.dataset.resizableReady = 'true';
        table.classList.add('resizable-table');
        applyStoredWidths(table, key);
    }

    function init() {
        document.querySelectorAll('table').forEach(makeResizable);
    }

    document.addEventListener('DOMContentLoaded', init);
})();
