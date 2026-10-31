/* ==========================================================================
   FindIT - user dashboard.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    function renderReports(items) {
        const container = document.getElementById('recent-reports');
        ui.clear(container);
        if (!items.length) {
            ui.showEmpty(container, 'You have not created any reports yet.');
            return;
        }
        const list = ui.el('div', 'list-group list-group-flush');
        items.forEach(function (item) {
            const row = ui.el('a', 'list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-2');
            row.href = 'item.html?id=' + item.id;
            const left = ui.el('div', '');
            left.appendChild(ui.el('div', 'fw-semibold', item.title));
            const meta = ui.el('div', 'item-meta',
                item.type === 'LOST' ? 'Lost' : 'Found' + ' - ' + item.categoryName + ' - ' + item.locationName);
            left.appendChild(meta);
            row.appendChild(left);
            row.appendChild(ui.statusBadge(item.status));
            list.appendChild(row);
        });
        container.appendChild(list);
    }

    function renderClaims(items) {
        const container = document.getElementById('recent-claims');
        ui.clear(container);
        if (!items.length) {
            ui.showEmpty(container, 'You have not submitted any claims yet.');
            return;
        }
        const list = ui.el('div', 'list-group list-group-flush');
        items.forEach(function (claim) {
            const row = ui.el('div', 'list-group-item d-flex justify-content-between align-items-center gap-2');
            const left = ui.el('div', '');
            const link = ui.el('a', 'fw-semibold', claim.itemTitle);
            link.href = 'item.html?id=' + claim.itemId;
            left.appendChild(link);
            left.appendChild(ui.el('div', 'item-meta', 'Submitted ' + ui.formatDate(claim.createdAt)));
            row.appendChild(left);
            row.appendChild(ui.statusBadge(claim.status));
            list.appendChild(row);
        });
        container.appendChild(list);
    }

    function renderMatches(matches) {
        const container = document.getElementById('recent-matches');
        ui.clear(container);
        if (!matches.length) {
            ui.showEmpty(container, 'No match suggestions yet. Add more detail to your lost reports.');
            return;
        }
        matches.forEach(function (match) {
            const card = ui.el('div', 'card mb-2');
            const body = ui.el('div', 'card-body py-2');
            const row = ui.el('div', 'd-flex justify-content-between align-items-start gap-2');
            const left = ui.el('div', '');
            const link = ui.el('a', 'fw-semibold', match.title);
            link.href = 'item.html?id=' + match.itemId;
            left.appendChild(link);
            left.appendChild(ui.el('div', 'item-meta', match.categoryName + ' - ' + match.locationName));
            row.appendChild(left);
            const score = ui.el('div', 'text-end');
            score.appendChild(ui.el('div', 'match-score', match.score));
            score.appendChild(ui.bandBadge(match.band));
            row.appendChild(score);
            body.appendChild(row);
            body.appendChild(ui.el('div', 'item-meta mt-1', match.breakdown.explanation));
            card.appendChild(body);
            container.appendChild(card);
        });
    }

    document.addEventListener('DOMContentLoaded', async function () {
        const user = await ui.initPage({ requiresAuth: true, pageId: 'dashboard' });
        if (!user) {
            return;
        }
        document.getElementById('dashboard-greeting').textContent = 'Hello ' + user.displayName;

        const loading = document.getElementById('dashboard-loading');
        const content = document.getElementById('dashboard-content');

        try {
            const [reports, claims] = await Promise.all([
                api.get('/api/items?mine=true&size=5&sort=newest'),
                api.get('/api/claims/mine?size=5')
            ]);

            document.getElementById('stat-reports').textContent = reports.totalElements;
            document.getElementById('stat-open').textContent =
                reports.content.filter(function (item) { return item.status === 'OPEN'; }).length;
            document.getElementById('stat-claims').textContent = claims.totalElements;
            document.getElementById('stat-pending').textContent =
                claims.content.filter(function (claim) { return claim.status === 'PENDING'; }).length;

            renderReports(reports.content);
            renderClaims(claims.content);

            // Suggested matches for the user's own open lost reports.
            const lostReports = reports.content.filter(function (item) {
                return item.type === 'LOST' && item.status === 'OPEN';
            });
            const matchGroups = await Promise.all(lostReports.map(function (item) {
                return api.get('/api/items/' + item.id + '/matches');
            }));
            const flat = [];
            matchGroups.forEach(function (group) { flat.push.apply(flat, group); });
            flat.sort(function (a, b) { return b.score - a.score; });
            renderMatches(flat.slice(0, 5));

            loading.classList.add('d-none');
            content.classList.remove('d-none');
        } catch (error) {
            loading.classList.add('d-none');
            ui.showError(error);
        }
    });
})(window);
