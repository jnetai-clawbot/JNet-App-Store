'use strict';

/* J~Net App Store — admin front-end.
 * Talks to the local server's admin API. All failures surface a clear error code. */

const el = (id) => document.getElementById(id);

let editingId = null;
let resolvedGithub = null;

function setStatus(message, kind) {
  const status = el('status');
  status.textContent = message || '';
  status.className = 'status' + (kind ? ' ' + kind : '');
}

function currentSource() {
  const checked = document.querySelector('input[name="source"]:checked');
  return checked ? checked.value : 'github';
}

function updateSourcePanels() {
  const source = currentSource();
  el('githubPanel').classList.toggle('hidden', source !== 'github');
  el('uploadPanel').classList.toggle('hidden', source !== 'upload');
  resolvedGithub = null;
  el('resolveInfo').textContent =
    'Paste a repo or release link and resolve to fetch the latest APK asset.';
}

async function api(path, options) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { /* non-JSON */ }
  if (!res.ok) {
    const code = (data && data.error) || `HTTP-${res.status}`;
    const message = (data && data.message) || text || res.statusText;
    throw new Error(`[${code}] ${message}`);
  }
  return data;
}

async function resolveGithub() {
  const input = el('githubUrl').value.trim();
  if (!input) {
    setStatus('[ERR-UI-001] Enter a GitHub link first.', 'err');
    return;
  }
  el('btnResolve').disabled = true;
  setStatus('Resolving latest release…');
  try {
    const data = await api('/api/admin/resolve-github', {
      method: 'POST',
      body: JSON.stringify({ githubUrl: input }),
    });
    resolvedGithub = data;
    if (!el('version').value && data.tag) {
      el('version').value = String(data.tag).replace(/^v/i, '');
    }
    el('resolveInfo').innerHTML =
      `Repo: <b>${data.repo}</b><br>` +
      `Latest tag: <b>${data.tag || 'n/a'}</b><br>` +
      `APK asset: <b>${data.apkName || 'none found'}</b><br>` +
      `<span class="muted small">${data.apkUrl || 'No .apk asset in latest release'}</span>`;
    setStatus('Resolved latest release.', 'ok');
  } catch (e) {
    setStatus(e.message, 'err');
    el('resolveInfo').textContent = 'Resolution failed — check the link.';
  } finally {
    el('btnResolve').disabled = false;
  }
}

async function uploadApk() {
  const fileInput = el('apkFile');
  if (!fileInput.files || !fileInput.files[0]) {
    return null;
  }
  const form = new FormData();
  form.append('apk', fileInput.files[0]);
  setStatus('Uploading APK…');
  const res = await fetch('/api/admin/upload', { method: 'POST', body: form });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { /* ignore */ }
  if (!res.ok) {
    const code = (data && data.error) || `HTTP-${res.status}`;
    throw new Error(`[${code}] ${(data && data.message) || text}`);
  }
  el('uploadInfo').textContent = `Uploaded: ${data.filename} (${data.size} bytes)`;
  return data;
}

async function uploadIcon() {
  const fileInput = el('iconFile');
  const name = el('name').value.trim();
  if (!name) {
    setStatus('[ERR-UI-005] Enter the app name before uploading an icon.', 'err');
    return;
  }
  if (!fileInput.files || !fileInput.files[0]) {
    setStatus('[ERR-UI-006] Choose a PNG file first.', 'err');
    return;
  }
  const file = fileInput.files[0];
  if (file.type !== 'image/png' && !/\.png$/i.test(file.name)) {
    setStatus('[ERR-UI-007] Only .png files are allowed.', 'err');
    return;
  }
  const form = new FormData();
  form.append('icon', file);
  form.append('appName', name);
  el('btnUploadIcon').disabled = true;
  setStatus('Uploading icon…');
  try {
    const res = await fetch('/api/admin/upload-icon', { method: 'POST', body: form });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { /* ignore */ }
    if (!res.ok) {
      const code = (data && data.error) || `HTTP-${res.status}`;
      throw new Error(`[${code}] ${(data && data.message) || text}`);
    }
    el('iconInfo').textContent = `Saved as ${data.filename}`;
    setStatus('Icon uploaded.', 'ok');
    await loadList();
  } catch (e) {
    setStatus(e.message, 'err');
  } finally {
    el('btnUploadIcon').disabled = false;
  }
}

async function toggleEnabled(app) {
  try {
    await api(`/api/admin/apps/${encodeURIComponent(app.id)}`, {
      method: 'PUT',
      body: JSON.stringify({ enabled: app.enabled === false }),
    });
    setStatus(app.enabled === false ? 'App enabled.' : 'App disabled.', 'ok');
    await loadList();
  } catch (e) {
    setStatus(e.message, 'err');
  }
}

async function changePassword() {
  const currentPassword = el('curPass').value;
  const newPassword = el('newPass').value;
  el('passStatus').textContent = '';
  el('passStatus').className = 'status';
  if (!currentPassword || !newPassword) {
    el('passStatus').textContent = '[ERR-UI-008] Enter both current and new password.';
    el('passStatus').className = 'status err';
    return;
  }
  el('btnChangePass').disabled = true;
  try {
    await api('/api/admin/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    });
    el('curPass').value = '';
    el('newPass').value = '';
    el('passStatus').textContent = 'Password updated. Use the new password next login.';
    el('passStatus').className = 'status ok';
  } catch (e) {
    el('passStatus').textContent = e.message;
    el('passStatus').className = 'status err';
  } finally {
    el('btnChangePass').disabled = false;
  }
}

async function saveApp() {
  const name = el('name').value.trim();
  if (!name) {
    setStatus('[ERR-UI-002] App name is required.', 'err');
    return;
  }
  const source = currentSource();
  const payload = {
    name,
    description: el('description').value.trim(),
    packageName: el('packageName').value.trim(),
    version: el('version').value.trim(),
    iconUrl: el('iconUrl').value.trim(),
    enabled: el('enabled').checked,
    source,
  };

  el('btnSave').disabled = true;
  try {
    if (source === 'github') {
      const link = el('githubUrl').value.trim();
      if (!link) {
        throw new Error('[ERR-UI-003] Enter a GitHub link or switch to upload.');
      }
      payload.githubUrl = link;
      payload.githubRepo = resolvedGithub ? resolvedGithub.repo : '';
      if (resolvedGithub && resolvedGithub.apkUrl) {
        payload.apkUrl = resolvedGithub.apkUrl;
        payload.size = resolvedGithub.size || 0;
      } else if (!resolvedGithub) {
        // Resolve on the fly so the store always stores a concrete APK URL.
        const data = await api('/api/admin/resolve-github', {
          method: 'POST',
          body: JSON.stringify({ githubUrl: link }),
        });
        resolvedGithub = data;
        payload.githubRepo = data.repo;
        payload.apkUrl = data.apkUrl;
        payload.size = data.size || 0;
        if (!payload.version && data.tag) {
          payload.version = String(data.tag).replace(/^v/i, '');
        }
      }
    } else {
      const uploaded = await uploadApk();
      if (uploaded) {
        payload.apkUrl = uploaded.apkUrl;
        payload.size = uploaded.size;
      } else if (!editingId) {
        throw new Error('[ERR-UI-004] Choose an APK file to upload.');
      }
    }

    if (editingId) {
      await api(`/api/admin/apps/${encodeURIComponent(editingId)}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      });
      setStatus('App updated.', 'ok');
    } else {
      await api('/api/admin/apps', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      setStatus('App added.', 'ok');
    }
    resetForm();
    await loadList();
  } catch (e) {
    setStatus(e.message, 'err');
  } finally {
    el('btnSave').disabled = false;
  }
}

function resetForm() {
  editingId = null;
  resolvedGithub = null;
  el('formTitle').textContent = 'Add an app';
  el('btnCancel').classList.add('hidden');
  ['name', 'description', 'packageName', 'version', 'iconUrl', 'githubUrl'].forEach((id) => {
    el(id).value = '';
  });
  el('apkFile').value = '';
  el('iconFile').value = '';
  el('iconInfo').textContent =
    'Saved as "<App-Name>.png" in the images/ folder. Apps without a custom image use the default icon.';
  el('enabled').checked = true;
  document.querySelector('input[name="source"][value="github"]').checked = true;
  updateSourcePanels();
}

function editApp(app) {
  editingId = app.id;
  el('formTitle').textContent = `Edit: ${app.name}`;
  el('btnCancel').classList.remove('hidden');
  el('name').value = app.name || '';
  el('description').value = app.description || '';
  el('packageName').value = app.packageName || '';
  el('version').value = app.version || '';
  el('iconUrl').value = app.iconUrl || '';
  el('githubUrl').value = app.githubUrl || '';
  el('enabled').checked = app.enabled !== false;
  document.querySelector(`input[name="source"][value="${app.source}"]`).checked = true;
  updateSourcePanels();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function deleteApp(app) {
  if (!window.confirm(`Delete "${app.name}" from the store?`)) {
    return;
  }
  try {
    await api(`/api/admin/apps/${encodeURIComponent(app.id)}`, { method: 'DELETE' });
    setStatus('App deleted.', 'ok');
    await loadList();
  } catch (e) {
    setStatus(e.message, 'err');
  }
}

function renderList(apps) {
  const list = el('list');
  list.innerHTML = '';
  if (!apps.length) {
    list.innerHTML = '<div class="empty">No apps yet. Add one above.</div>';
    return;
  }
  apps.forEach((app) => {
    const row = document.createElement('div');
    row.className = 'app-row';
    const icon = app.iconUrl || '/images/default-app.png';
    const enabled = app.enabled !== false;
    row.innerHTML = `
      <img class="app-icon-img" src="${escapeHtml(icon)}" alt=""
           onerror="this.onerror=null;this.src='/images/default-app.png'" />
      <div class="app-meta">
        <div class="name">${escapeHtml(app.name)}
          <span class="badge ${app.source}">${app.source === 'upload' ? 'Uploaded' : 'GitHub'}</span>
          <span class="badge ${enabled ? 'on' : 'off'}">${enabled ? 'Enabled' : 'Disabled'}</span>
        </div>
        <div class="sub">${escapeHtml(app.version ? 'v' + app.version : '')}
          ${app.packageName ? ' • ' + escapeHtml(app.packageName) : ''}</div>
        <div class="sub">${escapeHtml(app.apkUrl || 'no apk url')}</div>
      </div>
      <div class="app-actions">
        <button class="icon-btn" data-action="toggle">${enabled ? 'Disable' : 'Enable'}</button>
        <button class="icon-btn" data-action="edit">Edit</button>
        <button class="icon-btn danger" data-action="delete">Delete</button>
      </div>`;
    row.querySelector('[data-action="toggle"]').addEventListener('click', () => toggleEnabled(app));
    row.querySelector('[data-action="edit"]').addEventListener('click', () => editApp(app));
    row.querySelector('[data-action="delete"]').addEventListener('click', () => deleteApp(app));
    list.appendChild(row);
  });
}

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function loadList() {
  try {
    const data = await api('/api/admin/apps');
    renderList(data.apps || []);
  } catch (e) {
    setStatus(e.message, 'err');
  }
}

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('input[name="source"]').forEach((radio) => {
    radio.addEventListener('change', updateSourcePanels);
  });
  el('btnResolve').addEventListener('click', resolveGithub);
  el('btnSave').addEventListener('click', saveApp);
  el('btnCancel').addEventListener('click', resetForm);
  el('btnReload').addEventListener('click', loadList);
  el('btnUploadIcon').addEventListener('click', uploadIcon);
  el('btnChangePass').addEventListener('click', changePassword);
  updateSourcePanels();
  loadList();
});
