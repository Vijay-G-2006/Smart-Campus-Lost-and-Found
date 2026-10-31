/* ==========================================================================
   FindIT - "my claims" list.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    function claimCard(claim) {
        const card = ui.el('div', 'card mb-3');
        const body = ui.el('div', 'card-body');

        const header = ui.el('div', 'd-flex flex-wrap justify-content-between align-items-start gap-2 mb-2');
        const left = ui.el('div', '');
        const link = ui.el('a', 'fw-semibold', claim.itemTitle);
        link.href = 'item.html?id=' + claim.itemId;
        left.appendChild(link);
        left.appendChild(ui.el('div', 'item-meta', 'Submitted ' + ui.formatDateTime(claim.createdAt)));
        header.appendChild(left);
        header.appendChild(ui.statusBadge(claim.status));
        body.appendChild(header);

        body.appendChild(ui.el('p', 'item-meta mb-1', 'Your explanation'));
        body.appendChild(ui.el('p', 'item-description', claim.explanation));

        if (claim.verificationAnswers) {
            body.appendChild(ui.el('p', 'item-meta mb-1 mt-2', 'Your verification answers (private)'));
            body.appendChild(ui.el('p', 'item-description', claim.verificationAnswers));
        }

        if (claim.reviewReason) {
            body.appendChild(ui.el('p', 'item-meta mb-1 mt-2', 'Administrator decision'));
            body.appendChild(ui.el('p', 'item-description', claim.reviewReason));
        }

        if (claim.reviewedAt) {
            body.appendChild(ui.el('p', 'item-meta mt-2',
                'Reviewed on ' + ui.formatDateTime(claim.reviewedAt) +
                (claim.reviewedByDisplayName ? ' by ' + claim.reviewedByDisplayName : '')));
        }

        if (claim.canWithdraw) {
            const withdraw = ui.el('button', 'btn btn-sm btn-outline-secondary mt-3', 'Withdraw claim');
            withdraw.type = 'button';
            withdraw.addEventListener('click', async function () {
                if (!window.confirm('Withdraw this claim? The administrator will no longer review it.')) {
                    return;
                }
                try {
                    await api.post('/api/claims/' + claim.id + '/withdraw', {});
                    ui.toast('The claim was withdrawn.', 'success');
                    load(0);
                } catch (error) {
                    ui.showError(error);
                }
            });
            body.appendChild(withdraw);
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
        nav.setAttribute('aria-label', 'Claim list pages');
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
        const loading = document.getElementById('claims-loading');
        const list = document.getElementById('claims-list');
        loading.classList.remove('d-none');
        try {
            const data = await api.get('/api/claims/mine?size=10&page=' + page);
            ui.clear(list);
            if (!data.content.length) {
                ui.showEmpty(list, 'You have not submitted any claims yet. Open a found item and use "Claim this item".');
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
        await ui.initPage({ requiresAuth: true, pageId: 'my-claims' });
        await load(0);

        document.getElementById('claims-pagination').addEventListener('click', function (event) {
            const link = event.target.closest('[data-page]');
            if (!link) {
                return;
            }
            event.preventDefault();
            load(Number(link.dataset.page));
        });
    });
})(window);
