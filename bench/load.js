'use strict';

/**
 * M4-7 压测脚本。零依赖，Node 18+（用内置 fetch）。
 *
 * 用法：
 *   node bench/load.js <场景> [并发数] [时长秒数 | 请求数]
 *
 * 场景：
 *   seat-hit    查空座 · 固定 (日期,时段) 反复打 → 首次回源之后全走缓存
 *   seat-miss   查空座 · 每次换一个日期 → 每个 key 都是冷的，请求全部打到 MySQL
 *               （这个场景第三个参数是**请求数**，不是秒数：缓存 key 数量有限，
 *                 打满之后后面的请求又会命中，所以按条数跑，跑完即停）
 *   order       下单 · 每个并发一个用户、一个专属座位 → 真实写路径吞吐（无不必要冲突）
 *   same-seat   下单 · 所有并发抢同一个座位 → 验证防超卖，期望只有 1 单成功
 *
 * 环境变量：
 *   BASE_URL         默认 http://localhost:8080
 *   ROOM_ID          默认 900000000000000001（由 bench/prepare.sql 创建）
 *   BENCH_PASSWORD   默认 123456
 *   BENCH_USERS      默认 60（要和 prepare.sql 里的数量一致）
 */

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const ROOM_ID = process.env.ROOM_ID || '900000000000000001';
const PASSWORD = process.env.BENCH_PASSWORD || '123456';
const USER_COUNT = parseInt(process.env.BENCH_USERS || '60', 10);

// 座位 ID 是 19 位数字，超过 JS Number 的精度上限（2^53），必须用 BigInt 再转字符串。
// 这就是前面反复强调的「ID 一律当字符串传」。
const SEAT_BASE = 900000000000001000n;

const [scenario, concArg, durArg] = process.argv.slice(2);
const concurrency = parseInt(concArg || '20', 10);
const durationSec = parseInt(durArg || '15', 10);

const sleep = ms => new Promise(r => setTimeout(r, ms));

function dateStr(offsetDays) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function seatId(seatNo) {
  return String(SEAT_BASE + BigInt(seatNo));
}

async function postJson(path, body, headers = {}) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body)
  });
  return res.json();
}

async function loginAll() {
  const tokens = [];
  for (let i = 1; i <= USER_COUNT; i++) {
    const username = 'bench' + String(i).padStart(2, '0');
    const json = await postJson('/user/login', { username, password: PASSWORD });
    if (json.code !== 200) {
      throw new Error(`登录失败 ${username}: ${json.code} ${json.msg}`);
    }
    tokens.push(json.data.token);
  }
  return tokens;
}

/** 每个场景的一次请求。返回 { code, ms }；latency 覆盖整条业务链路 */
function makeOnce(tokens, scenarioName) {
  if (scenarioName === 'seat-hit' || scenarioName === 'seat-miss') {
    const fixedDate = dateStr(1);
    let seq = 0;
    return async () => {
      // seat-miss 每次都换日期 → 缓存 key 不同 → 必然回源
      // 日期池给到 4000 天（约 11 年），保证 1500 个请求里没有一次重复
      const date = scenarioName === 'seat-hit' ? fixedDate : dateStr(1 + (seq++ % 4000));
      const path = `/reservation/available?roomId=${ROOM_ID}&date=${date}&slotId=1`;
      const t0 = performance.now();
      const json = await fetch(BASE + path,
          { headers: { Authorization: tokens[0] } }).then(r => r.json());
      return { code: json.code, ms: performance.now() - t0 };
    };
  }

  if (scenarioName === 'order' || scenarioName === 'same-seat') {
    return async (workerId, k) => {
      const token = tokens[workerId % tokens.length];
      let body;
      if (scenarioName === 'same-seat') {
        // 全部打同一个座位：这是防超卖验收，不是吞吐测试
        body = { seatId: seatId(1), reserveDate: dateStr(1), slotId: 1 };
      } else {
        // 每个并发固定占 3 个座位，互不重叠；每 3 次换一天，保证 (用户,日期,时段) 不重复
        const seatNo = ((workerId * 3) + (k % 3)) % 60 + 1;
        body = {
          seatId: seatId(seatNo),
          reserveDate: dateStr(2 + (Math.floor(k / 3) % 28)),
          slotId: 1 + (k % 3)
        };
      }

      const t0 = performance.now();
      const tk = await fetch(BASE + '/reservation/token',
          { headers: { Authorization: token } }).then(r => r.json());
      if (tk.code !== 200) return { code: tk.code, ms: performance.now() - t0 };

      const json = await postJson('/reservation', body, {
        Authorization: token,
        'Idempotent-Token': tk.data
      });
      // 计时包含「取令牌 + 下单」整条链路，和 QPS 的统计口径保持一致
      return { code: json.code, ms: performance.now() - t0 };
    };
  }

  throw new Error(`未知场景: ${scenarioName}`);
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length));
  return sorted[idx];
}

(async () => {
  if (!['seat-hit', 'seat-miss', 'order', 'same-seat'].includes(scenario)) {
    console.error('用法: node bench/load.js <seat-hit|seat-miss|order|same-seat> [并发] [秒]');
    process.exit(1);
  }

  console.log(`场景 ${scenario}  并发 ${concurrency}  BASE_URL ${BASE}`);
  console.log(`正在登录 ${USER_COUNT} 个压测用户（BCrypt 校验，约 ${Math.ceil(USER_COUNT * 0.08)} 秒）...`);
  const tokens = await loginAll();
  console.log('登录完成。开始压测...\n');

  const once = makeOnce(tokens, scenario);
  const latencies = [];
  const okLatencies = [];
  const codes = {};

  // seat-miss 按条数跑：缓存 key 是有限的，打满之后请求会开始命中缓存，混在一起就没意义了
  const countMode = scenario === 'seat-miss';
  const targetCount = countMode ? parseInt(durArg || '1500', 10) : 0;
  const deadlineAt = Date.now() + durationSec * 1000;
  let issued = 0;

  if (countMode) {
    console.log(`按条数模式：共 ${targetCount} 个请求（每个请求一个全新的缓存 key）\n`);
  } else {
    console.log(`按时间模式：持续 ${durationSec} 秒\n`);
  }

  const workers = Array.from({ length: concurrency }, async (_, w) => {
    let k = 0;
    while (true) {
      if (countMode) {
        if (issued >= targetCount) break;
        issued++;
      } else if (Date.now() >= deadlineAt) {
        break;
      }
      try {
        // 被限流打回（1017）的请求不消耗业务组合，否则 k 涨得比成功数快得多，
        // 很快就把 84 个组合跑完、开始重复，测出来一堆 1015 干扰判断。
        const { code, ms } = await once(w, k);
        if (code !== 1017) k++;
        latencies.push(ms);
        if (code === 200) okLatencies.push(ms);
        codes[code] = (codes[code] || 0) + 1;
      } catch (e) {
        codes['ERR'] = (codes['ERR'] || 0) + 1;
      }
    }
  });

  const wallStart = Date.now();
  await Promise.all(workers);
  const wallMs = Date.now() - wallStart;

  latencies.sort((a, b) => a - b);
  okLatencies.sort((a, b) => a - b);
  const total = latencies.length;
  const okTotal = okLatencies.length;
  const wallSec = wallMs / 1000;

  console.log('─'.repeat(56));
  console.log(`总请求 ${total}   耗时 ${wallSec.toFixed(2)}s   QPS ${(total / wallSec).toFixed(1)}`);
  console.log(`成功   ${okTotal}   成功 QPS ${(okTotal / wallSec).toFixed(1)}` +
              `   成功率 ${total ? ((okTotal / total) * 100).toFixed(1) : 0}%`);
  console.log(`延迟 ms（全部）  p50 ${percentile(latencies, 50).toFixed(1)}` +
              `   p95 ${percentile(latencies, 95).toFixed(1)}` +
              `   p99 ${percentile(latencies, 99).toFixed(1)}` +
              `   max ${(latencies[total - 1] || 0).toFixed(1)}`);
  if (okTotal !== total) {
    console.log(`延迟 ms（仅成功）p50 ${percentile(okLatencies, 50).toFixed(1)}` +
                `   p95 ${percentile(okLatencies, 95).toFixed(1)}` +
                `   p99 ${percentile(okLatencies, 99).toFixed(1)}`);
  }
  console.log('返回码   ' + Object.entries(codes)
      .sort((a, b) => b[1] - a[1])
      .map(([k, v]) => `${k}:${v}`).join('   '));
  console.log('─'.repeat(56));

  // 下单场景最常见的误读：把「被限流拒绝」当成吞吐。
  // 令牌桶是 10 容量 + 5/秒/用户，N 个并发用户的写入上限就是 N×5 QPS，
  // 超出的请求会被立刻打回 1017 —— 它们很快，但不算成功。
  if (scenario === 'order' || scenario === 'same-seat') {
    const theoretical = concurrency * 5;
    console.log(`提示：令牌桶 10 容量 / 5 每秒每用户。${concurrency} 个并发用户的写入理论上限 ≈ ${theoretical} QPS，`);
    console.log(`      超过部分返回 1017（很快，但不算成功）。上面「成功 QPS」才是真实写入吞吐。`);
  }
})().catch(e => { console.error('脚本出错:', e); process.exit(1); });
