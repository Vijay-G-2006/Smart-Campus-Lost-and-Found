/* ==========================================================================
   FindIT - create and edit reports.
   ========================================================================== */
(function (global) {
    'use strict';

    const api = global.finditApi;
    const ui = global.finditUi;

    function selectedType() {
        const checked = document.querySelector('input[name="type"]:checked');
        return checked ? checked.value : 'LOST';
    }

    function showError(message) {
        const box = document.getElementById('form-error');
        box.textContent = message;
        box.classList.remove('d-none');
        document.getElementById('form-success').classList.add('d-none');
    }

    function showSuccess(message) {
        const box = document.getElementById('form-success');
        box.textContent = message;
        box.classList.remove('d-none');
        document.getElementById('form-error').classList.add('d-none');
    }

    function fillSelect(select, entries, keepValue) {
        const previous = keepValue !== undefined ? keepValue : select.value;
        ui.clear(select);
        select.appendChild(ui.el('option', '', 'Please choose...'));
        select.firstChild.value = '';
        entries.forEach(function (entry) {
            const option = ui.el('option', '', entry.name);
            option.value = entry.id;
            select.appendChild(option);
        });
        if (previous) {
            select.value = previous;
        }
    }

    function updateTypeUi() {
        const type = selectedType();
        const dateHelp = document.getElementById('eventDate-help');
        if (type === 'LOST') {
            dateHelp.textContent = 'When did you lose the item?';
        } else {
            dateHelp.textContent = 'When did you find the item?';
        }
    }

    document.addEventListener('DOMContentLoaded', async function () {
        const user = await ui.initPage({ requiresAuth: true, pageId: 'report' });
        if (!user) {
            return;
        }

        const form = document.getElementById('item-form');
        const submitButton = document.getElementById('submit-button');
        const params = new URLSearchParams(window.location.search);
        const editId = params.get('id');
        const requestedType = params.get('type');

        if (requestedType === 'LOST' || requestedType === 'FOUND') {
            document.getElementById(requestedType === 'LOST' ? 'typeLOST' : 'typeFOUND').checked = true;
        }
        document.querySelectorAll('input[name="type"]').forEach(function (radio) {
            radio.addEventListener('change', updateTypeUi);
        });
        updateTypeUi();

        try {
            const [categories, locations] = await Promise.all([
                api.get('/api/categories'),
                api.get('/api/locations')
            ]);
            fillSelect(document.getElementById('categoryId'), categories);
            fillSelect(document.getElementById('locationId'), locations);
        } catch (error) {
            showError('The categories and locations could not be loaded: ' + error.message);
            return;
        }

        if (editId) {
            // ---- edit an existing report -----------------------------------
            document.getElementById('form-heading').textContent = 'Edit report';
            document.getElementById('form-intro').textContent =
                'You can change your report while it is still open.';
            submitButton.textContent = 'Save changes';
            try {
                const item = await api.get('/api/items/' + editId);
                if (!item.canEdit) {
                    showError('This report can no longer be edited. Only your own open reports can be changed.');
                    submitButton.disabled = true;
                    return;
                }
                document.getElementById('title').value = item.title;
                document.getElementById('description').value = item.description;
                document.getElementById('eventDate').value = item.eventDate;
                document.getElementById('categoryId').value = item.categoryId;
                document.getElementById('locationId').value = item.locationId;
                document.querySelectorAll('input[name="type"]').forEach(function (radio) {
                    radio.disabled = true;
                });
                try {
                    const details = await api.get('/api/items/' + editId + '/private-details');
                    document.getElementById('privateVerificationDetail').value =
                        details.privateVerificationDetail || '';
                } catch (error) {
                    /* the private detail is optional */
                }
            } catch (error) {
                showError(error.message);
                submitButton.disabled = true;
            }
        }

        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            ui.clearFieldErrors(form);
            document.getElementById('form-error').classList.add('d-none');

            const payload = {
                title: document.getElementById('title').value.trim(),
                description: document.getElementById('description').value.trim(),
                eventDate: document.getElementById('eventDate').value,
                categoryId: Number(document.getElementById('categoryId').value),
                locationId: Number(document.getElementById('locationId').value),
                privateVerificationDetail: document.getElementById('privateVerificationDetail').value.trim()
            };

            submitButton.disabled = true;
            submitButton.textContent = editId ? 'Saving...' : 'Publishing...';
            try {
                let saved;
                if (editId) {
                    saved = await api.put('/api/items/' + editId, payload);
                } else {
                    saved = await api.post('/api/items', Object.assign({ type: selectedType() }, payload));
                }

                // Optional image upload (multipart, only for new reports).
                const fileInput = document.getElementById('image');
                if (fileInput && fileInput.files && fileInput.files.length) {
                    const formData = new FormData();
                    formData.append('file', fileInput.files[0]);
                    try {
                        saved = await api.postForm('/api/items/' + saved.id + '/image', formData);
                    } catch (error) {
                        showSuccess('The report was saved, but the image could not be uploaded: ' + error.message);
                        window.setTimeout(function () {
                            window.location.href = 'item.html?id=' + saved.id;
                        }, 1500);
                        return;
                    }
                }

                showSuccess('Report saved.');
                window.setTimeout(function () {
                    window.location.href = 'item.html?id=' + saved.id;
                }, 600);
            } catch (error) {
                if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
                    ui.showFieldErrors(form, error.fieldErrors);
                }
                showError(error.message || 'The report could not be saved.');
                submitButton.disabled = false;
                submitButton.textContent = editId ? 'Save changes' : 'Publish report';
            }
        });
    });
})(window);
