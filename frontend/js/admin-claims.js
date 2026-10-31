/* ==========================================================================
   FindIT - administrator claim review queue.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    function decide(claim, action) {
        const isApprove = action === 'approve';
        const reason = window.prompt(isApprove
            ? 'Optional note for approving this claim (the claimant sees it):'
            : 'Reason for rejecting this claim (required, the claimant sees it):');
        if (reason === null) {
            return;
        }
        if (!isApprove && !reason.trim()) {
            ui.toast('A reason is required when rejecting a claim.', 'danger');
            return;
        }
        if (!window.confirm(isApprove
            ? 'Approve this claim and mark the item as claimed?'
            : 'Reject this claim?')) {
            return;
        }
        api.post('/api/admin/claims/' + claim.id + '/' + action, { reason: reason.trim() })
            .then(function () {
                ui.toast(isApprove ? 'The claim was approved.' : 'The claim was rejected.', 'success');
                load(currentPage);
            })
            .catch(function (error) { ui.showError(error); });
    }

    function claimCard(claim) {
        const card = ui.el('div', 'card mb-3');
        const body = ui.el('div', 'card-body');

        const header = ui.el('div', 'd-flex flex-wrap justify-content-between align-items-start gap-2 mb-2');
        const left = ui.el('div', '');
        const link = ui.el('a', 'fw-semibold', claim.itemTitle);
        link.href = 'item.html?id=' + claim.itemId;
        left.appendChild(link);
        left.appendChild(ui.el('div', 'item-meta',
            'Claim #' + claim.id + ' by ' + claim.claimantDisplayName + ' - ' + ui.formatDateTime(claim.createdAt)));
        header.appendChild(left);
        header.appendChild(ui.statusBadge(claim.status));
        body.appendChild(header);

        body.appendChild(ui.el('p', 'item-meta mb-1', 'Explanation from the claimant'));
        body.appendChild(ui.el('p', 'item-description', claim.explanation));

        body.appendChild(ui.el('p', 'item-meta mb-1 mt-2', 'Verification answers'));
        body.appendChild(ui.el('p', 'item-description', claim.verificationAnswers || 'Not provided'));

        if (claim.reviewReason) {
            body.appendChild(ui.el('p', 'item-meta mb-1 mt-2', 'Recorded decision'));
            body.appendChild(ui.el('p', 'item-description', claim.reviewReason));
        }

        if (claim.status === 'PENDING') {
            const actions = ui.el('div', 'd-flex flex-wrap gap-2 mt-3');
            const approve = ui.el('button', 'btn btn-success', 'Approve claim');
            approve.type = 'button';
            approve.addEventListener('click', function () { decide(claim, 'approve'); });
            const reject = ui.el('button', 'btn btn-outline-danger', 'Reject claim');
            reject.type = 'button';
            reject.addEventListener('click', function () { decide(claim, 'reject'); });
            const markReturned = ui.el('button', 'btn btn-outline-primary', 'Mark item as returned');
            markReturned.type = 'button';
            markReturned.addEventListener('click', async function () {
                if (!window.confirm('Mark the found item as returned to its owner?')) {
                    return;
                }
                try {
                    await api.post('/api/admin/items/' + claim.itemId + '/mark-returned', {});
                    ui.toast('The item was marked as returned.', 'success');
                    load(currentPage);
                } catch (error) {
                    ui.showError(error);
                }
            });
            actions.appendChild(approve);
            actions.appendChild(reject);
            actions.appendChild(markReturned);
            body.appendChild(actions);
        }

        card.appendChild(body);
        return card;
    }

    function renderPagination(page) {
        const container = document.getElementById('claims-pagination');
        ui.clear(container);
        if (!page.totalPages || page.totalPages <= 1) {
            return;
        }
        const nav = ui.el('nav', '', null);
        nav.setAttribute('aria-label', 'Claim queue pages');
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

    let currentPage = 0;

    async function load(page) {
        currentPage = page;
        const loading = document.getElementById('claims-loading');
        const list = document.getElementById('claims-list');
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
        try {
            const data = await api.get('/api/admin/claims?' + params.toString());
            ui.clear(list);
            if (!data.content.length) {
                ui.showEmpty(list, 'No claims match this filter.');
            } else {
                data.content.forEach(function (claim) {
                    list.appendChild(claimCard(claim));
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
            const list = document.getElementById('claims-list');
            ui.showEmpty(list, 'Administrator privileges are required for this page.');
            document.getElementById('claims-loading').classList.add('d-none');
            return;
        }

        document.getElementById('filter-form').addEventListener('submit', function (event) {
            event.preventDefault();
            load(0);
        });
        document.getElementById('claims-pagination').addEventListener('click', function (event) {
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
