#!/usr/bin/env node
/**
 * Crawl hero meta statistics (win rate, pick rate, ban rate) from mlbb.io API.
 * Usage: node scripts/crawl_meta_stats.js
 * Output: app/src/main/assets/meta_stats.json
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

const RANK_ID = 4; // Mythic
const TIMEFRAME_ID = 1; // Past 1 day

const API_URL = `https://mlbb.io/api/hero/filtered-statistics?rankId=${RANK_ID}&timeframeId=${TIMEFRAME_ID}`;

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

async function main() {
  console.log(`Fetching meta stats from: ${API_URL}`);

  try {
    const raw = await fetch(API_URL);
    const json = JSON.parse(raw);

    if (!json.success) {
      console.error('API returned error:', json.message);
      process.exit(1);
    }

    const heroes = json.data.heroes.map(h => ({
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

    const outputPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'meta_stats.json');
    fs.writeFileSync(outputPath, JSON.stringify(heroes, null, 2));
    console.log(`✅ Saved ${heroes.length} heroes to ${outputPath}`);

    // Print top 10 by win rate
    const sorted = [...heroes].sort((a, b) => b.win_rate - a.win_rate);
    console.log('\nTop 10 Win Rate (Mythic):');
    sorted.slice(0, 10).forEach((h, i) => {
      console.log(`  ${i + 1}. ${h.hero_name} — WR: ${h.win_rate}% | PR: ${h.pick_rate}% | BR: ${h.ban_rate}%`);
    });

  } catch (err) {
    console.error('Failed to fetch:', err.message);
    process.exit(1);
  }
}

main();
