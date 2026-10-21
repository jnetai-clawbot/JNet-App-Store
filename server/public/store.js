'use strict';

/* J~Net App Store — public store page.
 * Lists all enabled apps with a live inline search and clear (reset) control. */

const el = (id) => document.getElementById(id);
const DEFAULT_ICON = '/images/default-app.png';

let allApps = [];

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function downloadHref(app) {
  return app.apkUrl || app.githubUrl || '#';
}

function render(apps) {
  const grid = el('grid');
  grid.innerHTML = '';

  if (!apps.length) {
    el('storeEmpty').classList.remove('hidden');
    return;
  }
  el('storeEmpty').classList.add('hidden');

  apps.forEach((app) => {
    const icon = app.iconUrl || DEFAULT_ICON;
    const card = document.createElement('div');
    card.className = 'store-card';
    card.innerHTML = `
      <img class="store-icon" src="${escapeHtml(icon)}"
           alt="${escapeHtml(app.name)} icon"
           onerror="this.onerror=null;this.src='${DEFAULT_ICON}'" />
      <div class="store-info">
        <div class="store-name">${escapeHtml(app.name)}</div>
        <div class="store-meta">
          ${app.version ? '<span class="store-ver">v' + escapeHtml(app.version) + '</span>' : ''}
          <span class="badge ${app.source}">${app.source === 'upload' ? 'Uploaded' : 'GitHub'}</span>
        </div>
        ${app.description ? '<div class="store-desc">' + escapeHtml(app.description) + '</div>' : ''}
      </div>
      <a class="btn primary store-get" href="${escapeHtml(downloadHref(app))}"
         target="_blank" rel="noopener">Get</a>`;
    grid.appendChild(card);
  });
}

function applyFilter() {
  const input = el('search');
  const q = input.value.trim().toLowerCase();
  el('clearSearch').classList.toggle('hidden', q.length === 0);

  const filtered = !q
    ? allApps
    : allApps.filter((a) => {
        const haystack = [a.name, a.description, a.packageName, a.version]
          .join(' ')
          .toLowerCase();
        return haystack.includes(q);
      });

  render(filtered);
  el('storeStatus').textContent = q
    ? `${filtered.length} of ${allApps.length} app(s) matching “${input.value.trim()}”`
    : `${allApps.length} app(s) available`;
}

function clearSearch() {
  const input = el('search');
  input.value = '';
  input.focus();
  applyFilter();
}

async function loadApps() {
  try {
    const res = await fetch('/api/apps');
    if (!res.ok) {
      throw new Error(`HTTP-${res.status}`);
    }
    const data = await res.json();
    allApps = Array.isArray(data.apps) ? data.apps : [];
    applyFilter();
  } catch (e) {
    el('storeStatus').textContent = `[ERR-STORE-001] Could not load apps: ${e.message}`;
    el('storeEmpty').classList.remove('hidden');
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const input = el('search');
  input.addEventListener('input', applyFilter);
  input.addEventListener('search', applyFilter);
  el('clearSearch').addEventListener('click', clearSearch);
  loadApps();
});
