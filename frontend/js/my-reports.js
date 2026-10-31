/* ==========================================================================
   FindIT - "my reports" list.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    function renderRows(items) {
        const body = document.getElementById('reports-body');
        ui.clear(body);
        items.forEach(function (item) {
            const row = ui.el('tr', '');

            const titleCell = ui.el('td', '');
            const link = ui.el('a', 'fw-semibold', item.title);
            link.href = 'item.html?id=' + item.id;
            titleCell.appendChild(link);
            titleCell.appendChild(ui.el('div', 'item-meta', 'Created ' + ui.formatDate(item.createdAt)));
            row.appendChild(titleCell);

            const typeCell = ui.el('td', '');
            typeCell.appendChild(ui.typeBadge(item.type));
            row.appendChild(typeCell);

            row.appendChild(ui.el('td', '', item.categoryName));
            row.appendChild(ui.el('td', '', item.locationName));
            row.appendChild(ui.el('td', '', ui.formatDate(item.eventDate)));

            const statusCell = ui.el('td', '');
            statusCell.appendChild(ui.statusBadge(item.status));
            row.appendChild(statusCell);

            const actionCell = ui.el('td', 'text-end');
            const open = ui.el('a', 'btn btn-sm btn-outline-primary me-1', 'Open');
            open.href = 'item.html?id=' + item.id;
            actionCell.appendChild(open);
            if (item.canEdit) {
                const edit = ui.el('a', 'btn btn-sm btn-outline-secondary', 'Edit');
                edit.href = 'report-form.html?id=' + item.id;
                actionCell.appendChild(edit);
            }
            row.appendChild(actionCell);

            body.appendChild(row);
        });
    }

    function renderPagination(page) {
        const container = document.getElementById('reports-pagination');
        ui.clear(container);
        if (!page.totalPages || page.totalPages <= 1) {
            return;
        }
        const nav = ui.el('nav', '', null);
        nav.setAttribute('aria-label', 'Report list pages');
        const list = ui.el('ul', 'pagination');
        for (let index = 0; index < page.totalPages; index++) {
            const item = ui.el('li', 'page-item' + (index === page.page ? ' active' : ''));
            const link = ui.el('a', 'page-link', String(index + 1));
            link.href = '#';
            link.dataset.page = String(index);
            item.appendChild(link);
            list.appendChild(item);
        }
        nav.appendChild(list);
        container.appendChild(nav);
    }

    async function load(page) {
        const loading = document.getElementById('reports-loading');
        const table = document.getElementById('reports-table');
        const empty = document.getElementById('reports-empty');
        loading.classList.remove('d-none');
        try {
            const data = await api.get('/api/items?mine=true&size=10&page=' + page);
            renderRows(data.content);
            if (!data.content.length) {
                table.classList.add('d-none');
                empty.classList.remove('d-none');
                ui.showEmpty(empty, 'You have not created any reports yet. Use the buttons above to add one.');
            } else {
                table.classList.remove('d-none');
                empty.classList.add('d-none');
            }
            renderPagination(data);
        } catch (error) {
            ui.showError(error);
        } finally {
            loading.classList.add('d-none');
        }
    }

    document.addEventListener('DOMContentLoaded', async function () {
        await ui.initPage({ requiresAuth: true, pageId: 'my-reports' });
        await load(0);

        document.getElementById('reports-pagination').addEventListener('click', function (event) {
            const link = event.target.closest('[data-page]');
            if (!link) {
                return;
            }
            event.preventDefault();
            load(Number(link.dataset.page));
        });
    });
})(window);
