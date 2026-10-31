/* ==========================================================================
   FindIT - search page.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    let currentPage = 0;

    function buildQuery(page) {
        const params = new URLSearchParams();
        const value = function (id) {
            const node = document.getElementById(id);
            return node && node.value ? node.value.trim() : '';
        };
        ['q', 'type', 'status', 'categoryId', 'locationId', 'dateFrom', 'dateTo', 'sort'].forEach(function (key) {
            const found = value(key);
            if (found) {
                params.set(key, found);
            }
        });
        params.set('page', String(page));
        params.set('size', '12');
        return params.toString();
    }

    function itemCard(item) {
        const col = ui.el('div', 'col-md-6 col-xl-4');
        const card = ui.el('div', 'card item-card');
        const body = ui.el('div', 'card-body d-flex flex-column');

        if (item.hasImage) {
            const img = ui.el('img', 'item-image mb-3');
            img.src = item.imageUrl;
            img.alt = 'Photo of ' + item.title;
            img.loading = 'lazy';
            body.appendChild(img);
        }

        const badges = ui.el('div', 'd-flex gap-2 mb-2 flex-wrap');
        badges.appendChild(ui.typeBadge(item.type));
        badges.appendChild(ui.statusBadge(item.status));
        body.appendChild(badges);

        const title = ui.el('h2', 'item-title');
        const link = ui.el('a', '', item.title);
        link.href = 'item.html?id=' + item.id;
        title.appendChild(link);
        body.appendChild(title);

        body.appendChild(ui.el('div', 'item-meta mb-2',
            item.categoryName + ' - ' + item.locationName + ' - ' + ui.formatDate(item.eventDate)));
        body.appendChild(ui.el('p', 'item-description flex-grow-1', item.description));

        const footer = ui.el('div', 'd-flex justify-content-between align-items-center mt-2');
        footer.appendChild(ui.el('span', 'item-meta', 'by ' + item.createdByDisplayName));
        const open = ui.el('a', 'btn btn-sm btn-outline-primary', 'Open');
        open.href = 'item.html?id=' + item.id;
        footer.appendChild(open);
        body.appendChild(footer);

        card.appendChild(body);
        col.appendChild(card);
        return col;
    }

    function renderPagination(page) {
        const container = document.getElementById('pagination');
        ui.clear(container);
        if (!page.totalPages || page.totalPages <= 1) {
            return;
        }
        const nav = ui.el('nav', '', null);
        nav.setAttribute('aria-label', 'Search results pages');
        const list = ui.el('ul', 'pagination');

        const previous = ui.el('li', 'page-item' + (page.first ? ' disabled' : ''));
        const previousLink = ui.el('a', 'page-link', 'Previous');
        previousLink.href = '#';
        previousLink.dataset.page = String(page.page - 1);
        previous.appendChild(previousLink);
        list.appendChild(previous);

        for (let index = 0; index < page.totalPages; index++) {
            const item = ui.el('li', 'page-item' + (index === page.page ? ' active' : ''));
            const link = ui.el('a', 'page-link', String(index + 1));
            link.href = '#';
            link.dataset.page = String(index);
            item.appendChild(link);
            list.appendChild(item);
        }

        const next = ui.el('li', 'page-item' + (page.last ? ' disabled' : ''));
        const nextLink = ui.el('a', 'page-link', 'Next');
        nextLink.href = '#';
        nextLink.dataset.page = String(page.page + 1);
        next.appendChild(nextLink);
        list.appendChild(next);

        nav.appendChild(list);
        container.appendChild(nav);
    }

    async function runSearch(page) {
        currentPage = page;
        const results = document.getElementById('results');
        const summary = document.getElementById('result-summary');
        ui.showLoading(results, 'Searching...');
        try {
            const data = await api.get('/api/items?' + buildQuery(page));
            ui.clear(results);
            const summaryText = data.totalElements === 0
                ? 'No reports matched your filters.'
                : 'Showing ' + (data.totalElements === 1 ? '1 report' : data.totalElements + ' reports') +
                  ' (page ' + (data.page + 1) + ' of ' + data.totalPages + ')';
            summary.textContent = summaryText;
            if (!data.content.length) {
                ui.showEmpty(results, 'No reports matched your filters. Try a different keyword or clear the filters.');
            } else {
                data.content.forEach(function (item) {
                    results.appendChild(itemCard(item));
                });
            }
            renderPagination(data);
        } catch (error) {
            ui.clear(results);
            summary.textContent = 'The search could not be completed.';
            ui.showError(error);
        }
    }

    document.addEventListener('DOMContentLoaded', async function () {
        await ui.initPage({ pageId: 'search' });

        try {
            const [categories, locations] = await Promise.all([
                api.get('/api/categories'),
                api.get('/api/locations')
            ]);
            categories.forEach(function (entry) {
                const option = ui.el('option', '', entry.name);
                option.value = entry.id;
                document.getElementById('categoryId').appendChild(option);
            });
            locations.forEach(function (entry) {
                const option = ui.el('option', '', entry.name);
                option.value = entry.id;
                document.getElementById('locationId').appendChild(option);
            });
        } catch (error) {
            ui.showError(error);
        }

        document.getElementById('search-form').addEventListener('submit', function (event) {
            event.preventDefault();
            runSearch(0);
        });

        document.getElementById('reset-filters').addEventListener('click', function () {
            document.getElementById('search-form').reset();
            runSearch(0);
        });

        document.getElementById('pagination').addEventListener('click', function (event) {
            const link = event.target.closest('[data-page]');
            if (!link || link.parentNode.classList.contains('disabled')) {
                return;
            }
            event.preventDefault();
            runSearch(Number(link.dataset.page));
        });

        await runSearch(0);
    });
})(window);
