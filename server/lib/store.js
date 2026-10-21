'use strict';

const fs = require('fs');
const path = require('path');
const config = require('../config');
const logger = require('./logger');

const DEFAULT_STORE = {
  name: config.STORE_NAME,
  updated: null,
  apps: [],
};

function ensureDirs() {
  fs.mkdirSync(config.DATA_DIR, { recursive: true });
  fs.mkdirSync(config.UPLOAD_DIR, { recursive: true });
  fs.mkdirSync(config.IMAGES_DIR, { recursive: true });
}

/** Sanitises an app name into a safe filename stem (e.g. "J~Net Keyboard" -> "J-Net-Keyboard"). */
function sanitizeName(name) {
  return String(name || 'app')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/[^A-Za-z0-9._-]/g, '')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80) || 'app';
}

/** The PNG filename an app's custom icon is stored under, e.g. "J-Net-Keyboard.png". */
function iconFilenameFor(name) {
  return `${sanitizeName(name)}.png`;
}

/**
 * Resolves an app's icon URL: an explicit iconUrl wins, otherwise a file named
 * "<App-Name>.png" in the images folder is used if present, else '' (default icon).
 */
function iconUrlFor(app) {
  if (app.iconUrl) {
    return app.iconUrl;
  }
  const file = iconFilenameFor(app.name);
  if (fs.existsSync(path.join(config.IMAGES_DIR, file))) {
    return `/images/${file}`;
  }
  return '';
}

function load() {
  try {
    ensureDirs();
    if (!fs.existsSync(config.DATA_FILE)) {
      save(DEFAULT_STORE);
      return JSON.parse(JSON.stringify(DEFAULT_STORE));
    }
    const raw = fs.readFileSync(config.DATA_FILE, 'utf8');
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed.apps)) {
      parsed.apps = [];
    }
    return parsed;
  } catch (e) {
    logger.error('ERR-STORE-001', 'Failed to load store data', e.message);
    return JSON.parse(JSON.stringify(DEFAULT_STORE));
  }
}

function save(store) {
  try {
    ensureDirs();
    store.updated = new Date().toISOString();
    const tmp = config.DATA_FILE + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(store, null, 2));
    fs.renameSync(tmp, config.DATA_FILE);
    logger.info('STORE-SAVED', `Store saved (${store.apps.length} app(s))`);
    return store;
  } catch (e) {
    logger.error('ERR-STORE-002', 'Failed to save store data', e.message);
    throw e;
  }
}

function slugify(name) {
  return String(name || 'app')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40) || 'app';
}

function newId(store, name) {
  const base = slugify(name);
  let id = base;
  let n = 1;
  while (store.apps.some((a) => a.id === id)) {
    id = `${base}-${n++}`;
  }
  return id;
}

function listApps() {
  return load().apps;
}

function getApp(id) {
  return load().apps.find((a) => a.id === id) || null;
}

function addApp(data) {
  const store = load();
  const now = new Date().toISOString();
  const app = {
    id: newId(store, data.name),
    name: String(data.name || 'Untitled').trim(),
    description: String(data.description || '').trim(),
    packageName: String(data.packageName || '').trim(),
    version: String(data.version || '').trim(),
    source: data.source === 'upload' ? 'upload' : 'github',
    githubRepo: String(data.githubRepo || '').trim(),
    githubUrl: String(data.githubUrl || '').trim(),
    apkUrl: String(data.apkUrl || '').trim(),
    iconUrl: String(data.iconUrl || '').trim(),
    size: Number(data.size) || 0,
    enabled: data.enabled !== false,
    updated: now,
  };
  store.apps.push(app);
  save(store);
  logger.info('APP-ADDED', `Added app ${app.id}`, { source: app.source });
  return app;
}

function updateApp(id, data) {
  const store = load();
  const idx = store.apps.findIndex((a) => a.id === id);
  if (idx === -1) {
    return null;
  }
  const existing = store.apps[idx];
  const merged = { ...existing };
  ['name', 'description', 'packageName', 'version', 'source',
    'githubRepo', 'githubUrl', 'apkUrl', 'iconUrl'].forEach((k) => {
    if (data[k] !== undefined) {
      merged[k] = String(data[k]).trim();
    }
  });
  if (data.size !== undefined) {
    merged.size = Number(data.size) || 0;
  }
  if (data.enabled !== undefined) {
    merged.enabled = data.enabled !== false;
  }
  if (merged.source !== 'upload') {
    merged.source = 'github';
  }
  merged.updated = new Date().toISOString();
  store.apps[idx] = merged;
  save(store);
  logger.info('APP-UPDATED', `Updated app ${id}`);
  return merged;
}

function deleteApp(id) {
  const store = load();
  const idx = store.apps.findIndex((a) => a.id === id);
  if (idx === -1) {
    return false;
  }
  const [removed] = store.apps.splice(idx, 1);
  save(store);
  logger.info('APP-DELETED', `Deleted app ${id}`);
  // Best-effort removal of an uploaded APK.
  if (removed.source === 'upload' && removed.apkUrl && removed.apkUrl.startsWith('/apk/')) {
    const file = path.join(config.UPLOAD_DIR, path.basename(removed.apkUrl));
    fs.rm(file, { force: true }, (err) => {
      if (err) {
        logger.warn('ERR-STORE-003', `Could not remove uploaded APK ${file}`, err.message);
      }
    });
  }
  return true;
}

function publicCatalog() {
  const store = load();
  return {
    name: store.name || config.STORE_NAME,
    updated: store.updated,
    apps: store.apps
      .filter((a) => a.enabled !== false)
      .map((a) => ({
        id: a.id,
        name: a.name,
        description: a.description,
        packageName: a.packageName,
        version: a.version,
        source: a.source,
        githubRepo: a.githubRepo,
        githubUrl: a.githubUrl,
        apkUrl: a.apkUrl,
        iconUrl: iconUrlFor(a),
        size: a.size,
        enabled: a.enabled !== false,
        updated: a.updated,
      })),
  };
}

module.exports = {
  ensureDirs,
  load,
  save,
  listApps,
  getApp,
  addApp,
  updateApp,
  deleteApp,
  publicCatalog,
  sanitizeName,
  iconFilenameFor,
  iconUrlFor,
};
