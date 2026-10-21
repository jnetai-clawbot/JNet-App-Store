'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const config = require('../config');
const logger = require('./logger');

/**
 * Persistent admin credentials.
 *
 * Stored in data/settings.json (file mode 0600). On first run a random
 * password is generated and logged once so the operator can copy it.
 */

const DEFAULT_USER = config.ADMIN_USER || 'admin';

function randomPassword(length = 16) {
  // URL-safe, unambiguous-ish random password.
  return crypto.randomBytes(24)
    .toString('base64')
    .replace(/[+/=]/g, '')
    .slice(0, length);
}

function load() {
  try {
    if (!fs.existsSync(config.SETTINGS_FILE)) {
      return null;
    }
    const raw = fs.readFileSync(config.SETTINGS_FILE, 'utf8');
    const parsed = JSON.parse(raw);
    if (!parsed.adminUser || !parsed.adminPassword) {
      return null;
    }
    return parsed;
  } catch (e) {
    logger.error('ERR-SET-001', 'Failed to load settings', e.message);
    return null;
  }
}

function save(settings) {
  try {
    fs.mkdirSync(path.dirname(config.SETTINGS_FILE), { recursive: true });
    const tmp = config.SETTINGS_FILE + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(settings, null, 2), { mode: 0o600 });
    fs.renameSync(tmp, config.SETTINGS_FILE);
    fs.chmodSync(config.SETTINGS_FILE, 0o600);
    logger.info('SET-SAVED', 'Admin settings saved');
    return settings;
  } catch (e) {
    logger.error('ERR-SET-002', 'Failed to save settings', e.message);
    throw e;
  }
}

/**
 * Loads existing credentials, or creates them (random password when no
 * ADMIN_PASSWORD env var is supplied). Returns { settings, generated }.
 */
function ensureCredentials() {
  const existing = load();
  if (existing) {
    return { settings: existing, generated: false };
  }

  const envPassword = process.env.ADMIN_PASSWORD;
  const settings = {
    adminUser: DEFAULT_USER,
    adminPassword: envPassword || randomPassword(),
  };
  save(settings);
  return { settings, generated: !envPassword };
}

function updatePassword(currentPassword, newPassword) {
  const settings = load();
  if (!settings) {
    return { ok: false, code: 'ERR-SET-003', message: 'Settings not initialised' };
  }
  if (settings.adminPassword !== currentPassword) {
    return { ok: false, code: 'ERR-SET-004', message: 'Current password is incorrect' };
  }
  if (!newPassword || String(newPassword).length < 6) {
    return { ok: false, code: 'ERR-SET-005', message: 'New password must be at least 6 characters' };
  }
  settings.adminPassword = String(newPassword);
  save(settings);
  return { ok: true, settings };
}

module.exports = { load, save, ensureCredentials, updatePassword, randomPassword };
