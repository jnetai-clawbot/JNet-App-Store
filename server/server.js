'use strict';

const path = require('path');
const fs = require('fs');
const express = require('express');
const multer = require('multer');

const config = require('./config');
const logger = require('./lib/logger');
const store = require('./lib/store');
const github = require('./lib/github');

const app = express();
app.disable('x-powered-by');

store.ensureDirs();

app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true }));

// ---------------------------------------------------------------------------
// Request logging (permanent diagnostics).
// ---------------------------------------------------------------------------
app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    logger.info('REQ', `${req.method} ${req.originalUrl} -> ${res.statusCode} (${Date.now() - start}ms)`);
  });
  next();
});

// ---------------------------------------------------------------------------
// Admin auth (HTTP Basic) — protects the editor UI and admin API only.
// ---------------------------------------------------------------------------
function safeEqual(a, b) {
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ba.length !== bb.length) {
    return false;
  }
  return require('crypto').timingSafeEqual(ba, bb);
}

function requireAdmin(req, res, next) {
  const header = req.headers.authorization || '';
  if (header.startsWith('Basic ')) {
    try {
      const decoded = Buffer.from(header.slice(6), 'base64').toString('utf8');
      const sep = decoded.indexOf(':');
      const user = decoded.slice(0, sep);
      const pass = decoded.slice(sep + 1);
      if (safeEqual(user, config.ADMIN_USER) && safeEqual(pass, config.ADMIN_PASSWORD)) {
        return next();
      }
    } catch (e) {
      logger.warn('ERR-AUTH-001', 'Failed to decode basic auth header', e.message);
    }
  }
  res.set('WWW-Authenticate', 'Basic realm="J~Net App Store Admin"');
  logger.warn('ERR-AUTH-002', `Unauthorised admin access to ${req.originalUrl}`);
  return res.status(401).send('Authentication required');
}

// ---------------------------------------------------------------------------
// Multer: uploaded APK files land in /uploads.
// ---------------------------------------------------------------------------
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, config.UPLOAD_DIR),
  filename: (req, file, cb) => {
    const safe = file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_');
    cb(null, `${Date.now()}-${safe}`);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 250 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (!/\.apk$/i.test(file.originalname)) {
      logger.warn('ERR-UPLOAD-001', `Rejected non-APK upload: ${file.originalname}`);
      return cb(Object.assign(new Error('Only .apk files may be uploaded'),
        { code: 'ERR-UPLOAD-001' }));
    }
    return cb(null, true);
  },
});

// ---------------------------------------------------------------------------
// Public endpoints used by the Android app.
// ---------------------------------------------------------------------------
app.get('/api/health', (req, res) => {
  res.json({ ok: true, name: config.STORE_NAME, time: new Date().toISOString() });
});

app.get('/api/apps', (req, res) => {
  try {
    res.json(store.publicCatalog());
  } catch (e) {
    logger.error('ERR-API-001', 'Failed to build catalog', e.message);
    res.status(500).json({ error: 'ERR-API-001', message: e.message });
  }
});

// Serve uploaded APKs.
app.get('/apk/:file', (req, res) => {
  const file = path.basename(req.params.file);
  const full = path.join(config.UPLOAD_DIR, file);
  if (!fs.existsSync(full)) {
    logger.warn('ERR-APK-001', `Requested missing APK ${file}`);
    return res.status(404).json({ error: 'ERR-APK-001', message: 'APK not found' });
  }
  return res.download(full, file);
});

// ---------------------------------------------------------------------------
// Admin API.
// ---------------------------------------------------------------------------
app.get('/api/admin/apps', requireAdmin, (req, res) => {
  res.json({ name: config.STORE_NAME, apps: store.listApps() });
});

app.post('/api/admin/apps', requireAdmin, (req, res) => {
  try {
    const body = req.body || {};
    if (!body.name) {
      return res.status(400).json({ error: 'ERR-API-002', message: 'name is required' });
    }
    if (body.source === 'upload') {
      if (!body.apkUrl) {
        return res.status(400).json({ error: 'ERR-API-003', message: 'apkUrl is required for uploaded apps' });
      }
    } else if (!body.apkUrl && !body.githubUrl && !body.githubRepo) {
      return res.status(400).json({
        error: 'ERR-API-004',
        message: 'Provide a GitHub link or resolve an APK URL first',
      });
    }
    const created = store.addApp(body);
    return res.status(201).json(created);
  } catch (e) {
    logger.error('ERR-API-005', 'Failed to add app', e.message);
    return res.status(500).json({ error: 'ERR-API-005', message: e.message });
  }
});

app.put('/api/admin/apps/:id', requireAdmin, (req, res) => {
  try {
    const updated = store.updateApp(req.params.id, req.body || {});
    if (!updated) {
      return res.status(404).json({ error: 'ERR-API-006', message: 'App not found' });
    }
    return res.json(updated);
  } catch (e) {
    logger.error('ERR-API-007', 'Failed to update app', e.message);
    return res.status(500).json({ error: 'ERR-API-007', message: e.message });
  }
});

app.delete('/api/admin/apps/:id', requireAdmin, (req, res) => {
  try {
    const ok = store.deleteApp(req.params.id);
    if (!ok) {
      return res.status(404).json({ error: 'ERR-API-008', message: 'App not found' });
    }
    return res.json({ ok: true });
  } catch (e) {
    logger.error('ERR-API-009', 'Failed to delete app', e.message);
    return res.status(500).json({ error: 'ERR-API-009', message: e.message });
  }
});

// Resolve a GitHub link into tag + APK asset URL.
app.post('/api/admin/resolve-github', requireAdmin, async (req, res) => {
  try {
    const input = (req.body && (req.body.githubUrl || req.body.repo)) || '';
    const result = await github.resolveLatestRelease(input);
    return res.json(result);
  } catch (e) {
    logger.error(e.code || 'ERR-GH-000', 'Failed to resolve GitHub release', e.message);
    return res.status(502).json({ error: e.code || 'ERR-GH-000', message: e.message });
  }
});

// Upload an APK file.
app.post('/api/admin/upload', requireAdmin, (req, res) => {
  upload.single('apk')(req, res, (err) => {
    if (err) {
      logger.error(err.code || 'ERR-UPLOAD-002', 'APK upload failed', err.message);
      return res.status(400).json({ error: err.code || 'ERR-UPLOAD-002', message: err.message });
    }
    if (!req.file) {
      return res.status(400).json({ error: 'ERR-UPLOAD-003', message: 'No file received' });
    }
    const apkUrl = `/apk/${req.file.filename}`;
    logger.info('UPLOAD-OK', `Stored ${req.file.filename} (${req.file.size} bytes)`);
    return res.json({ apkUrl, size: req.file.size, filename: req.file.filename });
  });
});

// ---------------------------------------------------------------------------
// Admin UI (static assets + protected index).
// ---------------------------------------------------------------------------
app.use('/admin-assets', express.static(config.PUBLIC_DIR));
app.get('/', requireAdmin, (req, res) => {
  res.sendFile(path.join(config.PUBLIC_DIR, 'index.html'));
});
app.get('/admin', requireAdmin, (req, res) => {
  res.sendFile(path.join(config.PUBLIC_DIR, 'index.html'));
});

// ---------------------------------------------------------------------------
// 404 + global error handler (permanent diagnostics).
// ---------------------------------------------------------------------------
app.use((req, res) => {
  logger.warn('ERR-404', `No route for ${req.method} ${req.originalUrl}`);
  res.status(404).json({ error: 'ERR-404', message: 'Not found' });
});

// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  logger.error('ERR-500', `Unhandled error on ${req.method} ${req.originalUrl}`, err.stack || err.message);
  res.status(500).json({ error: 'ERR-500', message: err.message });
});

const server = app.listen(config.PORT, config.HOST, () => {
  logger.info('START', `${config.STORE_NAME} server listening on ${config.HOST}:${config.PORT}`);
  logger.info('START', `Public catalog: http://localhost:${config.PORT}/api/apps`);
  logger.info('START', `Admin panel:    http://localhost:${config.PORT}/ (user: ${config.ADMIN_USER})`);
});

process.on('unhandledRejection', (reason) => {
  logger.error('ERR-UNHANDLED-REJECTION', 'Unhandled promise rejection', String(reason));
});
process.on('uncaughtException', (err) => {
  logger.error('ERR-UNCAUGHT-EXCEPTION', 'Uncaught exception', err.stack || err.message);
});
process.on('SIGTERM', () => {
  logger.info('STOP', 'SIGTERM received, shutting down');
  server.close(() => process.exit(0));
});

module.exports = app;
