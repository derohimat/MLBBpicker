#!/usr/bin/env node
/**
 * Crawl hero meta statistics (win rate, pick rate, ban rate) per rank from mlbb.io API.
 * Usage: node scripts/crawl_meta_stats.js [--patch 1.9.xx] [--ranks 1,2,3,4,5,6]
 *   --patch  Game patch the data belongs to (or env MLBB_PATCH). Kept from the
 *            previous data_version.json when omitted.
 *   --ranks  mlbb.io rankIds to try (default 1-9). The rank name is read from the
 *            API response, so unknown ids are simply skipped.
 * Output (app/src/main/assets):
 *   meta_stats_<rank>.json  per rank
 *   meta_stats.json         Mythic (rankId 4), kept for older app versions
 *   heroes.json             hero list in sync with the meta stats
 *   data_version.json       game patch, crawl time, timeframe and available ranks
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

const MYTHIC_RANK_ID = 4;
const TIMEFRAME_ID = 1; // Past 1 day
const ASSETS_DIR = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');

function argValue(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

const RANK_IDS = (argValue('--ranks') || '1,2,3,4,5,6,7,8,9')
  .split(',').map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n));

function apiUrl(rankId) {
  return `https://mlbb.io/api/hero/filtered-statistics?rankId=${rankId}&timeframeId=${TIMEFRAME_ID}`;
}

function slug(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
}

function readPreviousVersion() {
  try {
    return JSON.parse(fs.readFileSync(path.join(ASSETS_DIR, 'data_version.json'), 'utf8'));
  } catch (e) {
    return {};
  }
}

function fetch(url) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(url);
    const options = {
      hostname: parsedUrl.hostname,
      path: parsedUrl.pathname + parsedUrl.search,
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'en-US,en;q=0.9',
        'Referer': 'https://mlbb.io/statistics'
      }
    };
    https.get(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve(data));
      res.on('error', reject);
    }).on('error', reject);
  });
}

function toMetaStats(heroes) {
  return heroes.map(h => ({
    hero_id: h.hero_id,
    hero_name: h.hero_name,
    img_src: h.img_src,
    role: h.role,
    lane: h.lane,
    speciality: h.speciality,
    pick_rate: h.pick_rate,
    win_rate: h.win_rate,
    ban_rate: h.ban_rate,
    rank_name: h.rank_name,
    timeframe_name: h.timeframe_name
  }));
}

function writeJson(fileName, data) {
  const outputPath = path.join(ASSETS_DIR, fileName);
  fs.writeFileSync(outputPath, JSON.stringify(data, null, 2));
  return outputPath;
}

async function crawlRank(rankId) {
  const raw = await fetch(apiUrl(rankId));
  const json = JSON.parse(raw);
  if (!json.success || !json.data || !Array.isArray(json.data.heroes) || json.data.heroes.length === 0) {
    return null;
  }
  return json.data.heroes;
}

async function main() {
  const previous = readPreviousVersion();
  const gamePatch = argValue('--patch') || process.env.MLBB_PATCH || previous.game_patch || null;
  const ranks = [];
  const seenNames = new Set();
  let timeframe = null;
  let mythicHeroes = null;

  for (const rankId of RANK_IDS) {
    let heroes;
    try {
      console.log(`Fetching rankId=${rankId}: ${apiUrl(rankId)}`);
      heroes = await crawlRank(rankId);
    } catch (err) {
      console.warn(`  skipped rankId=${rankId}: ${err.message}`);
      continue;
    }
    if (!heroes) {
      console.warn(`  skipped rankId=${rankId}: no data`);
      continue;
    }

    const rankName = heroes[0].rank_name || `Rank ${rankId}`;
    if (seenNames.has(rankName)) {
      console.warn(`  skipped rankId=${rankId}: duplicate of ${rankName}`);
      continue;
    }
    seenNames.add(rankName);
    timeframe = timeframe || heroes[0].timeframe_name || null;

    const fileName = rankId === MYTHIC_RANK_ID ? 'meta_stats.json' : `meta_stats_${slug(rankName)}.json`;
    writeJson(fileName, toMetaStats(heroes));
    ranks.push({ id: rankId, name: rankName, file: fileName });
    console.log(`  ✅ ${rankName}: ${heroes.length} heroes -> ${fileName}`);

    if (rankId === MYTHIC_RANK_ID) mythicHeroes = heroes;
  }

  if (ranks.length === 0) {
    console.error('No rank data fetched; nothing written.');
    process.exit(1);
  }

  // Generate and save heroes.json in sync with meta stats (Mythic, or the first rank fetched)
  const baseHeroes = mythicHeroes || JSON.parse(fs.readFileSync(path.join(ASSETS_DIR, ranks[0].file), 'utf8'));
  const heroesList = baseHeroes.map(h => ({
    id: h.hero_id,
    hero_name: h.hero_name,
    role: Array.isArray(h.role) ? h.role.join(', ') : h.role,
    lane: Array.isArray(h.lane) ? h.lane.join(', ') : h.lane,
    speciality: Array.isArray(h.speciality) ? h.speciality.join(', ') : h.speciality,
    img_src: h.img_src
  })).sort((a, b) => a.hero_name.localeCompare(b.hero_name));
  writeJson('heroes.json', heroesList);
  console.log(`✅ Saved ${heroesList.length} heroes to heroes.json`);

  const version = {
    game_patch: gamePatch,
    generated_at: new Date().toISOString(),
    timeframe,
    ranks
  };
  writeJson('data_version.json', version);
  console.log(`✅ data_version.json: patch ${gamePatch || '?'}, ranks: ${ranks.map(r => r.name).join(', ')}`);
  if (!gamePatch) {
    console.warn('⚠️  Game patch unknown. Re-run with --patch <version> (e.g. --patch 1.9.20).');
  }
}

main();
