#!/usr/bin/env node
/**
 * Crawl hero build data from mlbb.io API.
 * APIs discovered:
 *   - /api/item/item-build-summary/hero/{HeroName} — builds per hero
 *   - /api/item/all-items — item ID → name mapping
 *   - /api/emblem/main-emblems — emblem ID → name mapping
 *   - /api/emblem/ability-emblems — emblem ability ID → name mapping
 *
 * Usage: node scripts/crawl_builds.js
 * Output: app/src/main/assets/builds.json
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'https://mlbb.io';

function fetch(urlPath) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(urlPath, BASE_URL);
    const options = {
      hostname: parsedUrl.hostname,
      path: parsedUrl.pathname + parsedUrl.search,
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'en-US,en;q=0.9',
        'Referer': 'https://mlbb.io/item-build'
      }
    };
    https.get(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          reject(new Error(`Failed to parse JSON from ${urlPath}: ${data.substring(0, 200)}`));
        }
      });
      res.on('error', reject);
    }).on('error', reject);
  });
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function main() {
  console.log('🔄 Fetching item and emblem mappings...');

  // Fetch item mapping
  const itemsResponse = await fetch('/api/item/all-items');
  const itemMap = {};
  itemsResponse.data.forEach(item => {
    itemMap[item.id] = { name: item.name, img_src: item.img_src || '' };
  });
  console.log(`  ✅ Loaded ${Object.keys(itemMap).length} items`);

  // Fetch emblem mapping
  const mainEmblemsResponse = await fetch('/api/emblem/main-emblems');
  const mainEmblemMap = {};
  mainEmblemsResponse.data.forEach(emb => {
    mainEmblemMap[emb.id] = emb.name;
  });
  console.log(`  ✅ Loaded ${Object.keys(mainEmblemMap).length} main emblems`);

  const abilityEmblemsResponse = await fetch('/api/emblem/ability-emblems');
  const abilityEmblemMap = {};
  abilityEmblemsResponse.data.forEach(emb => {
    abilityEmblemMap[emb.id] = emb.name;
  });
  console.log(`  ✅ Loaded ${Object.keys(abilityEmblemMap).length} ability emblems`);

  // Load hero list
  const heroesPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'heroes.json');
  const heroes = JSON.parse(fs.readFileSync(heroesPath, 'utf8'));
  console.log(`\n🦸 Processing ${heroes.length} heroes...`);

  const allBuilds = {};
  let successCount = 0;
  let failCount = 0;

  for (const hero of heroes) {
    const heroName = hero.hero_name;
    try {
      const buildResponse = await fetch(`/api/item/item-build-summary/hero/${encodeURIComponent(heroName)}`);

      if (!buildResponse.success || !buildResponse.data) {
        console.log(`  ⚠️ No build data for ${heroName}`);
        failCount++;
        continue;
      }

      // Combine topLiked and latest, prefer topLiked
      const topLiked = buildResponse.data.topLiked || [];
      const latest = buildResponse.data.latest || [];
      const allHeroBuilds = [...topLiked, ...latest];

      // Deduplicate by build_id and take top 3
      const seen = new Set();
      const uniqueBuilds = allHeroBuilds.filter(b => {
        if (seen.has(b.build_id)) return false;
        seen.add(b.build_id);
        return true;
      }).slice(0, 3);

      const resolvedBuilds = uniqueBuilds.map(build => {
        const items = (build.items || []).map(itemId => {
          const item = itemMap[itemId];
          return item ? item.name : `Unknown(${itemId})`;
        });

        const mainEmblem = build.emblems?.main_id
          ? mainEmblemMap[build.emblems.main_id] || `Unknown(${build.emblems.main_id})`
          : '';

        const abilityEmblems = (build.emblems?.ability_ids || []).map(id =>
          abilityEmblemMap[id] || `Unknown(${id})`
        );

        return {
          title: build.description || `${heroName} Build`,
          author: build.username || 'Anonymous',
          spell: build.battle_spell || '',
          emblem: mainEmblem,
          emblem_talents: abilityEmblems,
          items: items,
          likes: build.likes_count || 0
        };
      });

      if (resolvedBuilds.length > 0) {
        allBuilds[hero.id.toString()] = resolvedBuilds;
        successCount++;
        console.log(`  ✅ ${heroName}: ${resolvedBuilds.length} builds`);
      } else {
        console.log(`  ⚠️ ${heroName}: 0 builds`);
        failCount++;
      }

    } catch (err) {
      console.log(`  ❌ ${heroName}: ${err.message}`);
      failCount++;
    }

    // Rate limit: 200ms between requests
    await sleep(200);
  }

  const outputPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'builds.json');
  fs.writeFileSync(outputPath, JSON.stringify(allBuilds, null, 2));

  console.log(`\n✅ Done! ${successCount} heroes with builds, ${failCount} failed/empty.`);
  console.log(`📁 Saved to ${outputPath}`);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
