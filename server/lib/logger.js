'use strict';

/**
 * Permanent structured logger for the J~Net App Store server.
 * Kept in the project so any future failure can be traced (see PROMPT.md).
 */

const fs = require('fs');
const path = require('path');
const config = require('../config');

fs.mkdirSync(config.LOG_DIR, { recursive: true });
const LOG_FILE = path.join(config.LOG_DIR, 'store.log');

function write(level, code, message, meta) {
  const ts = new Date().toISOString();
  let line = `[${ts}] [${level}] [${code}] ${message}`;
  if (meta !== undefined) {
    try {
      line += ` ${typeof meta === 'string' ? meta : JSON.stringify(meta)}`;
    } catch (e) {
      line += ' [meta-unserialisable]';
    }
  }
  if (level === 'ERROR') {
    console.error(line);
  } else {
    console.log(line);
  }
  try {
    fs.appendFileSync(LOG_FILE, line + '\n');
  } catch (e) {
    // Logging must never crash the server.
    console.error(`[${ts}] [ERROR] [ERR-LOG] failed to write log file: ${e.message}`);
  }
}

module.exports = {
  info: (code, message, meta) => write('INFO', code, message, meta),
  warn: (code, message, meta) => write('WARN', code, message, meta),
  error: (code, message, meta) => write('ERROR', code, message, meta),
};
