/* ==========================================================================
   FindIT - shared UI helpers: navigation, toasts, formatting and shared
   rendering helpers used by every page.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined && text !== null) {
            node.textContent = text;
        }
        return node;
    }

    function clear(node) {
        while (node && node.firstChild) {
            node.removeChild(node.firstChild);
        }
    }

    function formatDate(value) {
        if (!value) {
            return '-';
        }
        const date = new Date(value.length === 10 ? value + 'T00:00:00' : value);
        if (isNaN(date.getTime())) {
            return value;
        }
        return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
    }

    function formatDateTime(value) {
        if (!value) {
            return '-';
        }
        const date = new Date(value);
        if (isNaN(date.getTime())) {
            return value;
        }
        return date.toLocaleString(undefined, {
            year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
        });
    }

    function statusBadge(status) {
        const badge = el('span', 'badge-status status-' + status, status.replace(/_/g, ' '));
        badge.setAttribute('title', 'Status: ' + status.replace(/_/g, ' '));
        return badge;
    }

    function typeBadge(type) {
        return el('span', 'badge-status type-' + type, type === 'LOST' ? 'Lost item' : 'Found item');
    }

    function bandBadge(band) {
        return el('span', 'badge-status band-' + band, band + ' match');
    }

    /* --- toasts ------------------------------------------------------------ */

    function toast(message, variant) {
        let area = document.getElementById('toast-area');
        if (!area) {
            area = el('div', '');
            area.id = 'toast-area';
            area.setAttribute('aria-live', 'polite');
            document.body.appendChild(area);
        }
        const wrapper = el('div', 'toast-message alert alert-' + (variant || 'info') + ' alert-dismissible fade show');
        wrapper.setAttribute('role', variant === 'danger' ? 'alert' : 'status');
        wrapper.appendChild(el('span', '', message));
        const close = el('button', 'btn-close');
        close.type = 'button';
        close.setAttribute('aria-label', 'Dismiss message');
        close.addEventListener('click', function () { wrapper.remove(); });
        wrapper.appendChild(close);
        area.appendChild(wrapper);
        window.setTimeout(function () {
            if (wrapper.parentNode) {
                wrapper.remove();
            }
        }, 6000);
    }

    function showError(error) {
        const message = error && error.message ? error.message : 'Something went wrong.';
        toast(message, 'danger');
    }

    function showFieldErrors(form, fieldErrors) {
        if (!fieldErrors) {
            return;
        }
        Object.keys(fieldErrors).forEach(function (field) {
            const input = form.querySelector('[name="' + field + '"]');
            if (input) {
                input.classList.add('is-invalid');
                let feedback = input.parentNode.querySelector('.invalid-feedback');
                if (!feedback) {
                    feedback = el('div', 'invalid-feedback');
                    input.parentNode.appendChild(feedback);
                }
                feedback.textContent = fieldErrors[field];
            }
        });
    }

    function clearFieldErrors(form) {
        form.querySelectorAll('.is-invalid').forEach(function (node) { node.classList.remove('is-invalid'); });
        form.querySelectorAll('.invalid-feedback').forEach(function (node) { node.remove(); });
    }

    /* --- navigation -------------------------------------------------------- */

    function navLink(href, label, id) {
        const item = el('li', 'nav-item');
        const link = el('a', 'nav-link', label);
        link.href = href;
        if (id) {
            link.dataset.page = id;
        }
        item.appendChild(link);
        return item;
    }

    function setActiveNav(pageId) {
        document.querySelectorAll('[data-page]').forEach(function (link) {
            link.classList.toggle('active', link.dataset.page === pageId);
        });
    }

    function renderNavigation(user) {
        const container = document.getElementById('main-nav');
        if (!container) {
            return;
        }
        clear(container);

        if (!user) {
            container.appendChild(navLink('login.html', 'Sign in', 'login'));
            container.appendChild(navLink('register.html', 'Register', 'register'));
            return;
        }

        container.appendChild(navLink('dashboard.html', 'Dashboard', 'dashboard'));
        container.appendChild(navLink('search.html', 'Search items', 'search'));
        container.appendChild(navLink('report-form.html', 'Report an item', 'report'));
        container.appendChild(navLink('my-reports.html', 'My reports', 'my-reports'));
        container.appendChild(navLink('my-claims.html', 'My claims', 'my-claims'));
        if (user.role === 'ADMIN') {
            container.appendChild(navLink('admin.html', 'Admin', 'admin'));
        }

        const spacer = el('li', 'nav-item ms-auto');
        const badge = el('span', 'navbar-text me-3', user.displayName + ' (' + user.role.toLowerCase() + ')');
        spacer.appendChild(badge);
        container.appendChild(spacer);
        const logoutItem = el('li', 'nav-item');
        const logoutButton = el('button', 'btn btn-sm btn-outline-light', 'Sign out');
        logoutButton.type = 'button';
        logoutButton.addEventListener('click', async function () {
            try {
                await api.logout();
                window.location.href = 'index.html';
            } catch (error) {
                showError(error);
            }
        });
        logoutItem.appendChild(logoutButton);
        container.appendChild(logoutItem);
    }

    /**
     * Loads the signed in user and renders the navigation.
     * Pages call this on load; `options.requiresAuth` redirects to the login
     * page when nobody is signed in.
     *
     * The navigation is rendered immediately (guest entries) so that the page
     * never shows an empty navigation bar while the session is checked.
     */
    async function initPage(options) {
        const opts = options || {};
        let user = null;
        if (!opts.requiresAuth) {
            renderNavigation(null);
        }
        try {
            user = await api.get('/api/auth/me');
        } catch (error) {
            user = null;
        }
        if (!user && opts.requiresAuth) {
            window.location.href = 'login.html?next=' + encodeURIComponent(window.location.pathname.split('/').pop());
            return null;
        }
        renderNavigation(user);
        if (user) {
            setActiveNav(opts.pageId);
        }
        return user;
    }

    function requireElement(id) {
        const node = document.getElementById(id);
        if (!node) {
            throw new Error('Missing element #' + id);
        }
        return node;
    }

    function showLoading(node, message) {
        clear(node);
        node.appendChild(el('div', 'loading-state', message || 'Loading...'));
    }

    function showEmpty(node, message) {
        clear(node);
        const box = el('div', 'empty-state');
        box.appendChild(el('p', 'mb-0', message));
        node.appendChild(box);
    }

    global.finditUi = {
        el: el,
        clear: clear,
        formatDate: formatDate,
        formatDateTime: formatDateTime,
        statusBadge: statusBadge,
        typeBadge: typeBadge,
        bandBadge: bandBadge,
        toast: toast,
        showError: showError,
        showFieldErrors: showFieldErrors,
        clearFieldErrors: clearFieldErrors,
        initPage: initPage,
        requireElement: requireElement,
        showLoading: showLoading,
        showEmpty: showEmpty
    };
})(window);
