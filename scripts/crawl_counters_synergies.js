#!/usr/bin/env node
/**
 * Crawl hero counters and synergies from mlbb.io API.
 * Usage: node scripts/crawl_counters_synergies.js
 * Output: 
 *   - app/src/main/assets/counters.json
 *   - app/src/main/assets/synergies.json
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'https://mlbb.io';

function post(urlPath, body) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(urlPath, BASE_URL);
    const postData = JSON.stringify(body);
    const options = {
      hostname: parsedUrl.hostname,
      path: parsedUrl.pathname + parsedUrl.search,
      method: 'POST',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'en-US,en;q=0.9',
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData),
        'Referer': 'https://mlbb.io/counter-pick'
      }
    };
    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          reject(new Error(`Failed to parse JSON from ${urlPath}: ${data.substring(0, 200)}`));
        }
      });
    });
    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function main() {
  const heroesPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'heroes.json');
  const heroes = JSON.parse(fs.readFileSync(heroesPath, 'utf8'));
  console.log(`🦸 Processing ${heroes.length} heroes for counters and synergies...`);

  const allCounters = {};
  const allSynergies = {};
  let successCount = 0;
  let failCount = 0;

  for (const hero of heroes) {
    const heroId = hero.id;
    const heroName = hero.hero_name;
    try {
      console.log(`🔄 Fetching data for ${heroName} (${heroId})...`);
      
      // Fetch counters
      const countersResponse = await post('/api/hero/counter-pick-suggestions', { enemyHeroes: [heroId] });
      if (countersResponse.success && countersResponse.data) {
        allCounters[heroId.toString()] = countersResponse.data;
      } else {
        console.warn(`  ⚠️ Failed to get counters for ${heroName}: ${countersResponse.message}`);
      }

      // Fetch synergies
      const synergiesResponse = await post('/api/hero/hero-synergy-suggestions', { allyHeroes: [heroId] });
      if (synergiesResponse.success && synergiesResponse.data) {
        allSynergies[heroId.toString()] = synergiesResponse.data;
      } else {
        console.warn(`  ⚠️ Failed to get synergies for ${heroName}: ${synergiesResponse.message}`);
      }

      successCount++;
    } catch (err) {
      console.error(`  ❌ Error processing ${heroName}:`, err.message);
      failCount++;
    }

    // Rate limit
    await sleep(250);
  }

  const countersOutputPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'counters.json');
  fs.writeFileSync(countersOutputPath, JSON.stringify(allCounters, null, 2));
  console.log(`✅ Saved counters to ${countersOutputPath}`);

  const synergiesOutputPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'synergies.json');
  fs.writeFileSync(synergiesOutputPath, JSON.stringify(allSynergies, null, 2));
  console.log(`✅ Saved synergies to ${synergiesOutputPath}`);

  console.log(`\n🎉 Completed! Success: ${successCount}, Fail: ${failCount}`);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
