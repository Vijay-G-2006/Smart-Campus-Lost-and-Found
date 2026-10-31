/* ==========================================================================
   FindIT - item detail page: report, matches and the claim form.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    let currentItem = null;

    function renderMatches(matches) {
        const container = document.getElementById('matches');
        ui.clear(container);
        if (!matches.length) {
            ui.showEmpty(container, 'No comparable reports were found.');
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
            left.appendChild(ui.el('div', 'item-meta',
                match.categoryName + ' - ' + match.locationName + ' - ' + ui.formatDate(match.eventDate)));
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

    function renderClaimState(item) {
        const formArea = document.getElementById('claim-form-area');
        const state = document.getElementById('claim-state');
        ui.clear(state);
        state.classList.remove('d-none');
        formArea.classList.add('d-none');

        const message = ui.el('p', 'lead-muted mb-0');
        if (item.type !== 'FOUND') {
            message.textContent = 'Only found items can be claimed. Use the search to find reports of your lost item.';
        } else if (item.status === 'RETURNED') {
            message.textContent = 'This item was already returned to its owner.';
        } else if (item.status === 'CLOSED' || item.status === 'REJECTED') {
            message.textContent = 'This report is closed and does not accept claims any more.';
        } else if (item.owner) {
            message.textContent = 'This is your own report, so you cannot claim it.';
        } else if (item.status === 'CLAIMED') {
            message.textContent = 'This item is being handed over and does not accept new claims.';
        } else {
            message.textContent = 'You already have an active claim for this report. You can follow it under "My claims".';
        }
        state.appendChild(message);
    }

    function renderOwnerActions(item) {
        const container = document.getElementById('owner-actions');
        ui.clear(container);
        if (!item.owner && !item.canClose) {
            container.classList.add('d-none');
            return;
        }
        container.classList.remove('d-none');

        if (item.canEdit) {
            const edit = ui.el('a', 'btn btn-outline-primary', 'Edit report');
            edit.href = 'report-form.html?id=' + item.id;
            container.appendChild(edit);
        }
        if (item.canClose) {
            const close = ui.el('button', 'btn btn-outline-secondary', 'Close report');
            close.type = 'button';
            close.addEventListener('click', async function () {
                if (!window.confirm('Close this report? It will no longer be highlighted in the search.')) {
                    return;
                }
                try {
                    await api.post('/api/items/' + item.id + '/close', {});
                    ui.toast('The report was closed.', 'success');
                    window.setTimeout(function () { window.location.reload(); }, 700);
                } catch (error) {
                    ui.showError(error);
                }
            });
            container.appendChild(close);
        }
    }

    async function loadPrivateDetails(item) {
        const card = document.getElementById('private-card');
        try {
            const details = await api.get('/api/items/' + item.id + '/private-details');
            card.classList.remove('d-none');
            document.getElementById('private-detail').textContent =
                details.privateVerificationDetail || 'Not provided';
            document.getElementById('private-contact').textContent =
                details.contactChannel || 'Not provided';
        } catch (error) {
            card.classList.add('d-none');
        }
    }

    function renderItem(item) {
        currentItem = item;

        const badges = document.getElementById('item-badges');
        ui.clear(badges);
        badges.appendChild(ui.typeBadge(item.type));
        badges.appendChild(ui.statusBadge(item.status));

        document.getElementById('item-title').textContent = item.title;
        document.getElementById('item-meta').textContent =
            'Reported by ' + item.createdByDisplayName + ' on ' + ui.formatDate(item.createdAt);
        document.getElementById('item-description').textContent = item.description;
        document.getElementById('item-category').textContent = item.categoryName;
        document.getElementById('item-location').textContent = item.locationName;
        document.getElementById('item-date').textContent = ui.formatDate(item.eventDate);
        document.getElementById('item-created').textContent = ui.formatDateTime(item.createdAt);

        const statusCell = document.getElementById('item-status');
        ui.clear(statusCell);
        statusCell.appendChild(ui.statusBadge(item.status));
        if (item.status === 'RETURNED') {
            statusCell.appendChild(ui.el('span', 'item-meta ms-2', 'Returned on ' + ui.formatDate(item.returnedAt)));
        }

        const image = document.getElementById('item-image');
        if (item.hasImage) {
            image.src = item.imageUrl;
            image.alt = 'Photo of ' + item.title;
            image.classList.remove('d-none');
        } else {
            image.classList.add('d-none');
        }

        renderOwnerActions(item);

        if (item.canClaim) {
            document.getElementById('claim-card').classList.remove('d-none');
        } else {
            renderClaimState(item);
        }
    }

    document.addEventListener('DOMContentLoaded', async function () {
        await ui.initPage({ requiresAuth: true, pageId: 'search' });

        const params = new URLSearchParams(window.location.search);
        const id = params.get('id');
        const errorBox = document.getElementById('item-error');
        if (!id) {
            errorBox.textContent = 'No report was requested.';
            errorBox.classList.remove('d-none');
            document.getElementById('item-loading').classList.add('d-none');
            return;
        }

        try {
            const item = await api.get('/api/items/' + id);
            renderItem(item);
            document.getElementById('item-loading').classList.add('d-none');
            document.getElementById('item-content').classList.remove('d-none');

            await loadPrivateDetails(item);

            try {
                const matches = await api.get('/api/items/' + id + '/matches');
                renderMatches(matches);
            } catch (error) {
                ui.showEmpty(document.getElementById('matches'), 'The match suggestions could not be loaded.');
            }
        } catch (error) {
            document.getElementById('item-loading').classList.add('d-none');
            errorBox.textContent = error.message || 'The report could not be loaded.';
            errorBox.classList.remove('d-none');
            return;
        }

        const claimForm = document.getElementById('claim-form');
        claimForm.addEventListener('submit', async function (event) {
            event.preventDefault();
            const errorBoxClaim = document.getElementById('claim-error');
            errorBoxClaim.classList.add('d-none');
            ui.clearFieldErrors(claimForm);
            const button = document.getElementById('claim-submit');
            button.disabled = true;
            button.textContent = 'Submitting...';
            try {
                await api.post('/api/items/' + currentItem.id + '/claims', {
                    explanation: document.getElementById('explanation').value.trim(),
                    verificationAnswers: document.getElementById('verificationAnswers').value.trim()
                });
                ui.toast('Your claim was submitted and is now in the administrator queue.', 'success');
                renderClaimState(currentItem);
            } catch (error) {
                if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
                    ui.showFieldErrors(claimForm, error.fieldErrors);
                }
                errorBoxClaim.textContent = error.message || 'The claim could not be submitted.';
                errorBoxClaim.classList.remove('d-none');
                button.disabled = false;
                button.textContent = 'Submit claim';
            }
        });
    });
})(window);
