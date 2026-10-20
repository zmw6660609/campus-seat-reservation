-- ============================================================================
-- M4-7 压测数据准备
--
-- 用法（在项目根的虚拟机里）：
--   docker compose exec -T mysql mysql -uroot -p<密码> < bench/prepare.sql
--
-- 幂等：先按 ID 前缀删掉上一次生成的数据，可以反复执行。
-- 所有 ID 都硬编码成 9 开头的 19 位数字，和雪花 ID 不会撞。
-- ============================================================================

USE seat_reservation;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 清理
-- 先按自习室删：压测期间通过接口下的单，ID 是雪花值（约 2.1e18），
-- 不在下面那个 9.2e18 的区间里，只按 ID 删会残留，导致重跑时冒出莫名其妙的 1015。
DELETE FROM reservation WHERE room_id = 900000000000000001;
DELETE FROM reservation WHERE id >= 920000000000000000;
DELETE FROM seat        WHERE id >= 900000000000001000;
DELETE FROM sys_user    WHERE id >= 910000000000000000;
DELETE FROM study_room  WHERE id  = 900000000000000001;

-- ------------------------------------------------------------ 数字辅助表
-- MySQL 没有 generate_series，用 5 层 0-9 交叉拼出 0~99999
DROP TEMPORARY TABLE IF EXISTS nums;
CREATE TEMPORARY TABLE nums (n INT PRIMARY KEY);

INSERT INTO nums (n)
SELECT a.i + b.i * 10 + c.i * 100 + d.i * 1000 + e.i * 10000
FROM (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e;

-- ------------------------------------------------------------ 1) 一间自习室
INSERT INTO study_room (id, name, location, capacity, status, deleted)
VALUES (900000000000000001, '压测自习室', '综合楼 3 层', 60, 1, 0);

-- ------------------------------------------------------------ 2) 60 个座位
INSERT INTO seat (id, room_id, seat_no, row_num, col_num, status, deleted)
SELECT 900000000000001000 + n,
       900000000000000001,
       CONCAT('A-', LPAD(n, 2, '0')),
       CEIL(n / 10),
       MOD(n, 10) + 1,
       1, 0
FROM nums WHERE n BETWEEN 1 AND 60;

-- ------------------------------------------------------------ 3) 60 个压测用户
-- 密码统一是 123456 的 BCrypt 密文（cost=10）。
-- 直接用密文插入，省掉 60 次注册接口调用；登录时仍走真实的 BCrypt 校验。
INSERT INTO sys_user (id, username, password, name, credit_score, role, status, version)
SELECT 910000000000000000 + n,
       CONCAT('bench', LPAD(n, 2, '0')),
       '$2a$10$bh6qw1w4NeIako8l05hPj.qjuKA9YtCBwozvPdLryAKWLJFBUQrK.',
       CONCAT('压测', n),
       100, 0, 1, 0
FROM nums WHERE n BETWEEN 1 AND 60;

-- ------------------------------------------------------------ 4) 12000 条历史预约
-- 覆盖近 120 天，每天固定 100 条（n DIV 100 = 第几天，MOD(n,100) = 当天第几条）。
-- 每天 100 条摊到 3 个时段，每个时段约占 34 个不同座位 —— 上座率 57% 左右，
-- 报表里既不是空表、也不是一条直线。
--
-- ★ occupy_flag 全部写 NULL：
--   历史上已结束的单子本来就该释放占用，而且唯一索引对 NULL 不生效，
--   这样才能造出「同一座位同一天同一时段有多条历史记录」的真实形态
--   （也是报表必须 COUNT(DISTINCT seat_id) 的原因）。
INSERT INTO reservation
    (id, user_id, seat_id, room_id, reserve_date, slot_id, status, occupy_flag, deadline)
SELECT 920000000000000000 + n,
       910000000000000000 + (MOD(MOD(n, 100) * 7 + (n DIV 100), 60) + 1),
       900000000000001000 + ((MOD(n, 100) DIV 3) + 1),
       900000000000000001,
       DATE_SUB(CURDATE(), INTERVAL (n DIV 100) DAY),
       MOD(MOD(n, 100), 3) + 1,
       CASE WHEN MOD(n, 13) = 0 THEN 5      -- 违规未签到
            WHEN MOD(n, 3)  = 0 THEN 2      -- 已签到
            ELSE 3 END,                     -- 已完成
       NULL,
       DATE_ADD(DATE_SUB(CURDATE(), INTERVAL (n DIV 100) DAY), INTERVAL 8 HOUR)
FROM nums WHERE n < 12000;

-- ------------------------------------------------------------ 校验
SELECT 'user'        AS tbl, COUNT(*) AS rows_created FROM sys_user    WHERE id >= 910000000000000000
UNION ALL SELECT 'seat',         COUNT(*) FROM seat         WHERE id >= 900000000000001000
UNION ALL SELECT 'room',         COUNT(*) FROM study_room   WHERE id  = 900000000000000001
UNION ALL SELECT 'reservation',  COUNT(*) FROM reservation WHERE id >= 920000000000000000;
