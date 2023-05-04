package com.yezi.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //将对象封装成JSON并存入redis
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //逻辑删除的插入
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //逻辑删除的查询
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbQuery, Long time, TimeUnit unit) {
        //先查redis
        String idKey = RedisConstants.CACHE_SHOP_KEY + id;
        String s = stringRedisTemplate.opsForValue().get(idKey);
        if(StrUtil.isBlank(s)) {
            return null;
        }
        //解析json获取过去时间和对象实体
        RedisData redisData = JSONUtil.toBean(s, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        Object data = redisData.getData();
        R r = JSONUtil.toBean((JSONObject) data, type);

        if(LocalDateTime.now().isBefore(expireTime)) {
            //未过期则直接返回
            return r;
        }
        //redis数据过期,重新获取
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if(tryLock(lockKey)) {
            //获取成功，开启缓存重建线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库获取对象
                    R r1 = dbQuery.apply(id);
                    this.setWithLogicalExpire(idKey, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return r;
    }

    //尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    //缓存穿透
    public <R, T> R queryWithPassThrough(String keyPrefix , T id, Class<R> type, Function<T, R> dbQuery, Long time, TimeUnit unit) {
        String keyId = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(keyId);
        //如果存在直接返回
        if(StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //不存在的null值
        if(json != null) {
            return null;
        }
        //查询数据库
        R r = dbQuery.apply(id);
        //如果不存在，将空直写入redis
        if(r == null) {
            stringRedisTemplate.opsForValue().set(keyId, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，存入redis
        this.set(keyId, r, time, unit);
        return r;
    }


}
