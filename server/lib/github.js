'use strict';

const config = require('../config');
const logger = require('./logger');

/**
 * Parses a GitHub URL or "owner/repo" string into { owner, repo }.
 * Accepts:
 *   https://github.com/owner/repo
 *   https://github.com/owner/repo/releases/tag/v1.2.3
 *   github.com/owner/repo
 *   owner/repo
 */
function parseRepo(input) {
  if (!input) {
    return null;
  }
  let value = String(input).trim();
  value = value.replace(/^git\+/, '').replace(/\.git$/, '');
  value = value.replace(/^https?:\/\//i, '').replace(/^www\./i, '');

  if (value.startsWith('github.com/')) {
    value = value.slice('github.com/'.length);
  }
  const parts = value.split('/').filter(Boolean);
  if (parts.length < 2) {
    return null;
  }
  return { owner: parts[0], repo: parts[1] };
}

async function githubFetch(url) {
  const headers = {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'JNet-App-Store-Server',
  };
  if (config.GITHUB_TOKEN) {
    headers.Authorization = `Bearer ${config.GITHUB_TOKEN}`;
  }
  const res = await fetch(url, { headers });
  return res;
}

/**
 * Resolves the latest release for a repository and returns APK asset info.
 */
async function resolveLatestRelease(repoInput) {
  const parsed = parseRepo(repoInput);
  if (!parsed) {
    throw Object.assign(new Error('Could not parse GitHub repository from input'),
      { code: 'ERR-GH-001' });
  }
  const { owner, repo } = parsed;
  const url = `https://api.github.com/repos/${owner}/${repo}/releases/latest`;
  logger.info('GH-RESOLVE', `Resolving latest release for ${owner}/${repo}`);

  let res;
  try {
    res = await githubFetch(url);
  } catch (e) {
    throw Object.assign(new Error(`GitHub request failed: ${e.message}`),
      { code: 'ERR-GH-002' });
  }

  if (res.status === 404) {
    throw Object.assign(new Error(`No releases found for ${owner}/${repo}`),
      { code: 'ERR-GH-003' });
  }
  if (!res.ok) {
    throw Object.assign(new Error(`GitHub returned HTTP ${res.status}`),
      { code: 'ERR-GH-004' });
  }

  const release = await res.json();
  const assets = Array.isArray(release.assets) ? release.assets : [];
  const apkAsset = assets.find((a) => /\.apk$/i.test(a.name))
    || assets.find((a) => /apk/i.test(a.name));

  return {
    repo: `${owner}/${repo}`,
    tag: release.tag_name || '',
    releaseName: release.name || '',
    apkUrl: apkAsset ? apkAsset.browser_download_url : '',
    apkName: apkAsset ? apkAsset.name : '',
    size: apkAsset ? apkAsset.size : 0,
    publishedAt: release.published_at || '',
    htmlUrl: release.html_url || `https://github.com/${owner}/${repo}/releases`,
  };
}

module.exports = { parseRepo, resolveLatestRelease };
