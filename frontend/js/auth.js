/* ==========================================================================
   FindIT - authentication pages (sign in and registration).
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    function nextPage() {
        const params = new URLSearchParams(window.location.search);
        const next = params.get('next');
        return next && next.indexOf('..') === -1 ? next : 'dashboard.html';
    }

    function showError(container, message) {
        container.textContent = message;
        container.classList.remove('d-none');
    }

    function hideError(container) {
        container.classList.add('d-none');
        container.textContent = '';
    }

    async function initLogin() {
        const form = document.getElementById('login-form');
        const errorBox = document.getElementById('login-error');
        const submit = document.getElementById('login-submit');
        if (!form) {
            return;
        }

        const user = await ui.initPage({ pageId: 'login' });
        if (user) {
            window.location.href = nextPage();
            return;
        }

        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            hideError(errorBox);
            ui.clearFieldErrors(form);
            submit.disabled = true;
            submit.textContent = 'Signing in...';
            try {
                const email = document.getElementById('email').value.trim();
                const password = document.getElementById('password').value;
                if (!email || !password) {
                    throw new api.ApiError(400, 'validation', 'Please enter your email address and password.');
                }
                await api.login(email, password);
                window.location.href = nextPage();
            } catch (error) {
                showError(errorBox, error.message || 'Sign in failed.');
                submit.disabled = false;
                submit.textContent = 'Sign in';
            }
        });
    }

    async function initRegister() {
        const form = document.getElementById('register-form');
        const errorBox = document.getElementById('register-error');
        const submit = document.getElementById('register-submit');
        if (!form) {
            return;
        }

        const user = await ui.initPage({ pageId: 'register' });
        if (user) {
            window.location.href = 'dashboard.html';
            return;
        }

        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            hideError(errorBox);
            ui.clearFieldErrors(form);

            const email = document.getElementById('email').value.trim();
            const displayName = document.getElementById('displayName').value.trim();
            const password = document.getElementById('password').value;
            const confirm = document.getElementById('confirmPassword').value;

            if (password !== confirm) {
                showError(errorBox, 'The two passwords do not match.');
                return;
            }

            submit.disabled = true;
            submit.textContent = 'Creating account...';
            try {
                await api.post('/api/auth/register', {
                    email: email,
                    displayName: displayName,
                    password: password
                });
                await api.login(email, password);
                window.location.href = 'dashboard.html';
            } catch (error) {
                if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
                    ui.showFieldErrors(form, error.fieldErrors);
                }
                showError(errorBox, error.message || 'The account could not be created.');
                submit.disabled = false;
                submit.textContent = 'Create account';
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        initLogin();
        initRegister();
    });
})(window);
