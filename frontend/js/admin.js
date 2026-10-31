/* ==========================================================================
   FindIT - administrator dashboard.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    function renderPendingClaims(claims) {
        const container = document.getElementById('pending-claims');
        ui.clear(container);
        if (!claims.length) {
            ui.showEmpty(container, 'There are no claims waiting for review.');
            return;
        }
        const table = ui.el('table', 'table table-sm align-middle');
        const head = ui.el('thead', '');
        const headRow = ui.el('tr', '');
        ['Item', 'Claimant', 'Submitted'].forEach(function (label) {
            const th = ui.el('th', '', label);
            th.scope = 'col';
            headRow.appendChild(th);
        });
        head.appendChild(headRow);
        table.appendChild(head);

        const body = ui.el('tbody', '');
        claims.forEach(function (claim) {
            const row = ui.el('tr', '');
            const itemCell = ui.el('td', '');
            const link = ui.el('a', '', claim.itemTitle);
            link.href = 'item.html?id=' + claim.itemId;
            itemCell.appendChild(link);
            row.appendChild(itemCell);
            row.appendChild(ui.el('td', '', claim.claimantDisplayName));
            row.appendChild(ui.el('td', '', ui.formatDate(claim.createdAt)));
            body.appendChild(row);
        });
        table.appendChild(body);
        container.appendChild(table);
    }

    function renderRecentItems(items) {
        const container = document.getElementById('recent-items');
        ui.clear(container);
        if (!items.length) {
            ui.showEmpty(container, 'No reports have been created yet.');
            return;
        }
        const list = ui.el('div', 'list-group list-group-flush');
        items.forEach(function (item) {
            const row = ui.el('a', 'list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-2');
            row.href = 'item.html?id=' + item.id;
            const left = ui.el('div', '');
            left.appendChild(ui.el('div', 'fw-semibold', item.title));
            left.appendChild(ui.el('div', 'item-meta',
                item.type === 'LOST' ? 'Lost' : 'Found' + ' - ' + item.locationName + ' - ' +
                item.createdByDisplayName));
            row.appendChild(left);
            row.appendChild(ui.statusBadge(item.status));
            list.appendChild(row);
        });
        container.appendChild(list);
    }

    function renderAudit(entries) {
        const body = document.getElementById('audit-body');
        ui.clear(body);
        if (!entries.length) {
            const row = ui.el('tr', '');
            const cell = ui.el('td', 'lead-muted', 'No audit events yet.');
            cell.colSpan = 5;
            row.appendChild(cell);
            body.appendChild(row);
            return;
        }
        entries.forEach(function (entry) {
            const row = ui.el('tr', '');
            row.appendChild(ui.el('td', '', ui.formatDateTime(entry.createdAt)));
            row.appendChild(ui.el('td', '', entry.action));
            row.appendChild(ui.el('td', '', entry.entityType + ' #' + (entry.entityId || '-')));
            row.appendChild(ui.el('td', '', entry.actorId ? 'User #' + entry.actorId : 'system'));
            row.appendChild(ui.el('td', 'item-meta', entry.metadata || ''));
            body.appendChild(row);
        });
    }

    document.addEventListener('DOMContentLoaded', async function () {
        const user = await ui.initPage({ requiresAuth: true, pageId: 'admin' });
        if (!user) {
            return;
        }
        if (user.role !== 'ADMIN') {
            const errorBox = document.getElementById('admin-error');
            errorBox.textContent = 'Administrator privileges are required for this page.';
            errorBox.classList.remove('d-none');
            document.getElementById('admin-loading').classList.add('d-none');
            return;
        }

        try {
            const [dashboard, audit] = await Promise.all([
                api.get('/api/admin/dashboard'),
                api.get('/api/admin/audit-logs?size=15')
            ]);

            const counts = dashboard.counts;
            document.getElementById('count-lost').textContent = counts.openLostReports;
            document.getElementById('count-found').textContent = counts.openFoundReports;
            document.getElementById('count-pending').textContent = counts.pendingClaims;
            document.getElementById('count-approved').textContent = counts.approvedClaims;
            document.getElementById('count-returned').textContent = counts.returnedItems;
            document.getElementById('count-closed').textContent = counts.closedReports;
            document.getElementById('count-reports').textContent = counts.totalReports;
            document.getElementById('count-users').textContent = counts.totalUsers;

            renderPendingClaims(dashboard.pendingClaims);
            renderRecentItems(dashboard.recentlyUpdatedReports);
            renderAudit(audit.content);

            document.getElementById('admin-loading').classList.add('d-none');
            document.getElementById('admin-content').classList.remove('d-none');
        } catch (error) {
            document.getElementById('admin-loading').classList.add('d-none');
            const errorBox = document.getElementById('admin-error');
            errorBox.textContent = error.message || 'The dashboard could not be loaded.';
            errorBox.classList.remove('d-none');
        }
    });
})(window);
