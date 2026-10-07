-- ============================================================================
-- 03_verify_unique_index.sql
--
-- 目的：用最少的 SQL，证明 reservation 表的这条唯一索引真的能防住重复预约，
--       以及「取消后座位能被释放」这个关键性质真的成立。
--
--   UNIQUE KEY uk_seat_date_slot (seat_id, reserve_date, slot_id, occupy_flag)
--
-- 执行：mysql -uroot -p seat_reservation < sql/03_verify_unique_index.sql
--
-- 必须【逐条】执行，不要整脚本一次性跑完。
-- 实验 1 和实验 2 是故意要报错的——"报了错"才算验收通过。
-- 一口气跑完的话，报错信息会淹没在输出里。
--
-- 约定：测试数据统一用 9 开头的 ID，脚本结尾会全部清理，可以反复执行。
-- ============================================================================

USE seat_reservation;

-- ---------------------------------------------------------------------------
-- 准备：清理上次残留 + 造测试数据
-- ---------------------------------------------------------------------------
DELETE FROM `reservation` WHERE `id` BETWEEN 9000 AND 9999;
DELETE FROM `seat`        WHERE `id` BETWEEN 9000 AND 9999;
DELETE FROM `study_room`  WHERE `id` BETWEEN 9000 AND 9999;
DELETE FROM `sys_user`    WHERE `id` BETWEEN 9000 AND 9999;

INSERT INTO `sys_user` (`id`, `username`, `password`, `name`)
VALUES (9001, 'verify_u1', 'irrelevant-for-this-test', '张三'),
       (9002, 'verify_u2', 'irrelevant-for-this-test', '李四');

INSERT INTO `study_room` (`id`, `name`, `location`)
VALUES (9101, '验收测试自习室', '不对外');

INSERT INTO `seat` (`id`, `room_id`, `seat_no`)
VALUES (9201, 9101, 'T-01');

SELECT '准备完成：2 个用户 / 1 间自习室 / 1 个座位' AS `step`;


-- ===========================================================================
-- 实验 1｜唯一索引拦截并发抢座
--   预期：第 1 条 INSERT 成功；第 2 条报
--         ERROR 1062 (23000): Duplicate entry '9201-2026-10-06-1-1' for key 'reservation.uk_seat_date_slot'
--   意义：两个用户同一座位同一天同一时段，数据库层面只允许存在一条「占用中」的记录。
-- ===========================================================================

INSERT INTO `reservation` (`id`, `user_id`, `seat_id`, `room_id`, `reserve_date`, `slot_id`, `deadline`)
VALUES (9301, 9001, 9201, 9101, '2026-10-06', 1, '2026-10-06 08:30:00');
-- ↑ 预期：Query OK, 1 row affected

INSERT INTO `reservation` (`id`, `user_id`, `seat_id`, `room_id`, `reserve_date`, `slot_id`, `deadline`)
VALUES (9302, 9002, 9201, 9101, '2026-10-06', 1, '2026-10-06 08:30:00');
-- ↑ 预期：ERROR 1062 Duplicate entry —— 报错才是对的！


-- ===========================================================================
-- 实验 2｜反向实验：只改 status、不改 occupy_flag，座位不会被释放
--   预期：下面 UPDATE 成功，但紧接着的 INSERT 仍然报 1062。
--   意义：这是最容易踩的坑——以为"状态改成已取消"就等于"座位空出来了"。
--         唯一索引只看 occupy_flag，不看 status。两者必须一起维护。
-- ===========================================================================

UPDATE `reservation` SET `status` = 4, `cancel_time` = NOW() WHERE `id` = 9301;
-- ↑ 预期：Query OK, 1 row affected（注意：occupy_flag 还是 1）

INSERT INTO `reservation` (`id`, `user_id`, `seat_id`, `room_id`, `reserve_date`, `slot_id`, `deadline`)
VALUES (9303, 9002, 9201, 9101, '2026-10-06', 1, '2026-10-06 08:30:00');
-- ↑ 预期：仍然 ERROR 1062 —— 报错才是对的！座位还没真正释放。


-- ===========================================================================
-- 实验 3｜把 occupy_flag 置 NULL，座位立刻可以被重新预约
--   预期：UPDATE 成功 → 紧接着的 INSERT 成功。
--   意义：这就是 occupy_flag 设计成「1 / NULL」而不是「1 / 0」的全部原因。
-- ===========================================================================

UPDATE `reservation` SET `occupy_flag` = NULL WHERE `id` = 9301;
-- ↑ 预期：Query OK, 1 row affected

INSERT INTO `reservation` (`id`, `user_id`, `seat_id`, `room_id`, `reserve_date`, `slot_id`, `deadline`)
VALUES (9304, 9002, 9201, 9101, '2026-10-06', 1, '2026-10-06 08:30:00');
-- ↑ 预期：Query OK, 1 row affected —— 座位被成功让出来了


-- ===========================================================================
-- 实验 4｜NULL 可以重复：同一座位同一时段允许存在多条已释放记录
--   预期：把 9304 也置 NULL，不报错；此时该座位该时段有 2 条 occupy_flag = NULL 的记录。
--   意义：如果当初把 occupy_flag 设计成 1 和 0，这一步会直接报 1062 ——
--         即"一个座位一个时段只能被取消一次"，第二次取消永远写不进去。
-- ===========================================================================

UPDATE `reservation` SET `occupy_flag` = NULL, `status` = 4 WHERE `id` = 9304;
-- ↑ 预期：Query OK, 1 row affected —— 不报错

SELECT `id`, `user_id`, `seat_id`, `reserve_date`, `slot_id`, `status`, `occupy_flag`
FROM `reservation`
WHERE `seat_id` = 9201 AND `reserve_date` = '2026-10-06' AND `slot_id` = 1
ORDER BY `id`;
-- ↑ 预期：2 行，occupy_flag 均为 NULL。这就是"可重复释放"的证据。


-- ---------------------------------------------------------------------------
-- 清理：测试数据全部删除
-- ---------------------------------------------------------------------------
DELETE FROM `reservation` WHERE `id` BETWEEN 9000 AND 9999;
DELETE FROM `seat`        WHERE `id` BETWEEN 9000 AND 9999;
DELETE FROM `study_room`  WHERE `id` BETWEEN 9000 AND 9999;
DELETE FROM `sys_user`    WHERE `id` BETWEEN 9000 AND 9999;

SELECT '验收完成，测试数据已清理' AS `step`;


-- ===========================================================================
-- 结论
--
--   1. 唯一索引能在数据库层面兜住「同一座位同一时段重复占用」，
--      应用层就算并发写漏了也拦得住。
--   2. 座位是否被占用，判据是 occupy_flag，不是 status。两者要由 Service 层一起维护。
--   3. 释放动作（置 NULL）可以重复执行 —— 这是唯一索引对 NULL 不生效带来的好处。
--   4. 唯一性的粒度是「座位 + 日期 + 时段」，所以时段必须是固定槽位。
--
--   还没覆盖的（留给 M3）：唯一索引只管"写进去的那一条不重复"，
--   管不住「查空闲 → 判信用分 → 写单」这串多步操作之间的时间缝隙。
-- ============================================================================
