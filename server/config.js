'use strict';

const path = require('path');

const root = __dirname;

module.exports = {
  // Port the store server listens on. Point your reverse proxy / static domain
  // (apps.jnetai.com) at this port.
  PORT: parseInt(process.env.PORT || '8090', 10),
  HOST: process.env.HOST || '0.0.0.0',

  // Admin panel credentials (HTTP Basic). Change these in production.
  ADMIN_USER: process.env.ADMIN_USER || 'admin',
  ADMIN_PASSWORD: process.env.ADMIN_PASSWORD || 'jnetai',

  // Optional GitHub token to raise API rate limits when resolving repos.
  GITHUB_TOKEN: process.env.GITHUB_TOKEN || '',

  STORE_NAME: process.env.STORE_NAME || 'J~Net App Store',
  PUBLIC_BASE_URL: process.env.PUBLIC_BASE_URL || 'https://apps.jnetai.com',

  DATA_DIR: path.join(root, 'data'),
  DATA_FILE: path.join(root, 'data', 'store.json'),
  SETTINGS_FILE: path.join(root, 'data', 'settings.json'),
  UPLOAD_DIR: path.join(root, 'uploads'),
  IMAGES_DIR: path.join(root, 'images'),
  LOG_DIR: path.join(root, 'logs'),
  PUBLIC_DIR: path.join(root, 'public'),
};
