package com.sr.constant;

public class RedisKeyConstant {
    public static final String LOGIN_TOKEN = "login:token:";
    public static final String LOCK_RESERVE = "lock:reserve:";
    public static final String DELAY_CHECKIN = "delay:checkin";
    public static final String CACHE_AVAILABLE = "cache:available:";
    public static final String LOCK_CACHE_REBUILD = "lock:cache_rebuild:";
    public static final String RANK_HOT_SEAT = "rank:hot:seat:";
    public static final String RATE_LIMIT_RESERVE_USER = "rate:reserve:user:%s";

    public static final String IDEMPOTENT_TOKEN = "idempotent:token:%s";

    /** 签发凭证：记录「这个一次性令牌是服务端发给谁的」。下单时用来校验令牌归属 */
    public static final String IDEMPOTENT_TOKEN_ISSUED = "idempotent:issued:%s";

    public static final String ROLE_PERMISSION = "role:permission:";

    /** 报表缓存。报表是慢变数据，TTL 用 60 秒 —— 和座位可用性缓存不是一回事（那边是强一致场景） */
    public static final String STATS_OCCUPANCY = "stats:occupancy:";
    public static final String STATS_TREND = "stats:trend:";

}
