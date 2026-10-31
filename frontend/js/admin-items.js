/* ==========================================================================
   FindIT - administrator report moderation.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    let currentPage = 0;

    function moderate(item, action) {
        let reason = '';
        if (action === 'REJECT') {
            const entered = window.prompt('Reason for rejecting this report (required):');
            if (entered === null) {
                return;
            }
            reason = entered.trim();
            if (!reason) {
                ui.toast('A reason is required when rejecting a report.', 'danger');
                return;
            }
        }
        const labels = { CLOSE: 'close', REJECT: 'reject', REOPEN: 're-open' };
        if (!window.confirm('Do you really want to ' + labels[action] + ' the report "' + item.title + '"?')) {
            return;
        }
        api.post('/api/admin/items/' + item.id + '/moderate', { action: action, reason: reason })
            .then(function () {
                ui.toast('The report was updated.', 'success');
                load(currentPage);
            })
            .catch(function (error) { ui.showError(error); });
    }

    function itemCard(item) {
        const card = ui.el('div', 'card mb-3');
        const body = ui.el('div', 'card-body');

        const header = ui.el('div', 'd-flex flex-wrap justify-content-between align-items-start gap-2 mb-2');
        const left = ui.el('div', '');
        const link = ui.el('a', 'fw-semibold', item.title);
        link.href = 'item.html?id=' + item.id;
        left.appendChild(link);
        left.appendChild(ui.el('div', 'item-meta',
            (item.type === 'LOST' ? 'Lost' : 'Found') + ' - ' + item.categoryName + ' - ' + item.locationName +
            ' - ' + ui.formatDate(item.eventDate) + ' - by ' + item.createdByDisplayName));
        header.appendChild(left);

        const badges = ui.el('div', 'd-flex gap-2');
        badges.appendChild(ui.statusBadge(item.status));
        header.appendChild(badges);
        body.appendChild(header);

        body.appendChild(ui.el('p', 'item-description', item.description));

        const actions = ui.el('div', 'd-flex flex-wrap gap-2 mt-3');
        if (item.status === 'OPEN' || item.status === 'UNDER_REVIEW' || item.status === 'CLAIMED') {
            const close = ui.el('button', 'btn btn-sm btn-outline-secondary', 'Close report');
            close.type = 'button';
            close.addEventListener('click', function () { moderate(item, 'CLOSE'); });
            actions.appendChild(close);

            const reject = ui.el('button', 'btn btn-sm btn-outline-danger', 'Reject report');
            reject.type = 'button';
            reject.addEventListener('click', function () { moderate(item, 'REJECT'); });
            actions.appendChild(reject);
        }
        if (item.status === 'CLOSED' || item.status === 'REJECTED') {
            const reopen = ui.el('button', 'btn btn-sm btn-outline-primary', 'Re-open report');
            reopen.type = 'button';
            reopen.addEventListener('click', function () { moderate(item, 'REOPEN'); });
            actions.appendChild(reopen);
        }
        if (item.type === 'FOUND' && item.status !== 'RETURNED') {
            const markReturned = ui.el('button', 'btn btn-sm btn-outline-success', 'Mark as returned');
            markReturned.type = 'button';
            markReturned.addEventListener('click', async function () {
                if (!window.confirm('Mark this item as returned to its owner?')) {
                    return;
                }
                try {
                    await api.post('/api/admin/items/' + item.id + '/mark-returned', {});
                    ui.toast('The item was marked as returned.', 'success');
                    load(currentPage);
                } catch (error) {
                    ui.showError(error);
                }
            });
            actions.appendChild(markReturned);
        }

        body.appendChild(actions);
        card.appendChild(body);
        return card;
    }

    function renderPagination(page) {
        const container = document.getElementById('items-pagination');
        ui.clear(container);
        if (!page.totalPages || page.totalPages <= 1) {
            return;
        }
        const nav = ui.el('nav', '', null);
        nav.setAttribute('aria-label', 'Report pages');
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
        currentPage = page;
        const loading = document.getElementById('items-loading');
        const list = document.getElementById('items-list');
        loading.classList.remove('d-none');
        const params = new URLSearchParams();
        const status = document.getElementById('status').value;
        const keyword = document.getElementById('q').value.trim();
        if (status) {
            params.set('status', status);
        }
        if (keyword) {
            params.set('q', keyword);
        }
        params.set('page', String(page));
        params.set('size', '10');
        params.set('sort', 'newest');
        try {
            const data = await api.get('/api/items?' + params.toString());
            ui.clear(list);
            if (!data.content.length) {
                ui.showEmpty(list, 'No reports match this filter.');
            } else {
                data.content.forEach(function (item) {
                    list.appendChild(itemCard(item));
                });
            }
            renderPagination(data);
        } catch (error) {
            ui.showError(error);
        } finally {
            loading.classList.add('d-none');
            list.classList.remove('d-none');
        }
    }

    document.addEventListener('DOMContentLoaded', async function () {
        const user = await ui.initPage({ requiresAuth: true, pageId: 'admin' });
        if (!user) {
            return;
        }
        if (user.role !== 'ADMIN') {
            const list = document.getElementById('items-list');
            ui.showEmpty(list, 'Administrator privileges are required for this page.');
            document.getElementById('items-loading').classList.add('d-none');
            return;
        }

        document.getElementById('filter-form').addEventListener('submit', function (event) {
            event.preventDefault();
            load(0);
        });
        document.getElementById('items-pagination').addEventListener('click', function (event) {
            const link = event.target.closest('[data-page]');
            if (!link) {
                return;
            }
            event.preventDefault();
            load(Number(link.dataset.page));
        });

        await load(0);
    });
})(window);
