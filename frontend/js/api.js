/* ==========================================================================
   FindIT - shared API client.
   Handles the JSON envelope, the CSRF token and the error shape so that the
   page scripts stay small. Nothing here ever stores a secret in localStorage:
   the session lives in an HttpOnly cookie owned by the backend.
   ========================================================================== */
(function (global) {
    'use strict';

    const API_BASE = global.FINDIT_API_BASE || '';

    function readCookie(name) {
        const target = name + '=';
        const parts = document.cookie.split(';');
        for (let i = 0; i < parts.length; i++) {
            const part = parts[i].trim();
            if (part.indexOf(target) === 0) {
                return decodeURIComponent(part.substring(target.length));
            }
        }
        return null;
    }

    async function request(path, options) {
        const opts = Object.assign({ method: 'GET', headers: {} }, options || {});
        opts.headers = Object.assign({ 'Accept': 'application/json' }, opts.headers || {});

        if (opts.body && typeof opts.body !== 'string') {
            opts.headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(opts.body);
        }

        if (opts.method !== 'GET' && opts.method !== 'HEAD' && opts.method !== 'OPTIONS') {
            const token = readCookie('XSRF-TOKEN');
            if (token) {
                opts.headers['X-XSRF-TOKEN'] = token;
            }
        }

        let response;
        try {
            response = await fetch(API_BASE + path, {
                method: opts.method,
                headers: opts.headers,
                body: opts.body,
                credentials: 'same-origin'
            });
        } catch (error) {
            throw new ApiError(0, 'network_error', 'Could not reach the server. Please check your connection.');
        }

        if (response.status === 204) {
            return null;
        }

        const contentType = response.headers.get('Content-Type') || '';
        let payload = null;
        if (contentType.indexOf('application/json') !== -1) {
            payload = await response.json().catch(function () { return null; });
        }

        if (!response.ok) {
            const error = new ApiError(
                response.status,
                (payload && payload.error) || 'error',
                (payload && payload.message) || 'The request could not be completed.',
                payload && payload.fieldErrors
            );
            throw error;
        }
        return payload;
    }

    class ApiError extends Error {
        constructor(status, code, message, fieldErrors) {
            super(message);
            this.name = 'ApiError';
            this.status = status;
            this.code = code;
            this.fieldErrors = fieldErrors || {};
        }
    }

    const api = {
        ApiError: ApiError,
        readCookie: readCookie,
        get: function (path) { return request(path, { method: 'GET' }); },
        post: function (path, body) { return request(path, { method: 'POST', body: body }); },
        postForm: function (path, formData) {
            return request(path, { method: 'POST', body: formData });
        },
        put: function (path, body) { return request(path, { method: 'PUT', body: body }); },
        delete: function (path) { return request(path, { method: 'DELETE' }); },
        login: function (email, password) {
            const form = new URLSearchParams();
            form.append('email', email);
            form.append('password', password);
            return request('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: form.toString()
            });
        },
        logout: function () { return request('/api/auth/logout', { method: 'POST' }); }
    };

    global.finditApi = api;
})(window);
