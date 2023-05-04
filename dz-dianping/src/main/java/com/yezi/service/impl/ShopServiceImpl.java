package com.yezi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.yezi.dto.Result;
import com.yezi.entity.Shop;
import com.yezi.mapper.ShopMapper;
import com.yezi.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yezi.utils.CacheClient;
import com.yezi.utils.RedisConstants;
import com.yezi.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        Shop shop = queryByIdWithPassThrough(id);
        //解决缓存击穿
        //Shop shop1 = queryByIdWithMutex(id);
        //逻辑删除
//        Shop shop1 = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    //解决缓存穿透
    public Shop queryByIdWithPassThrough(Long id) {
        //先查redis
        String idKey = RedisConstants.CACHE_SHOP_KEY + id;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(idKey);
        //判断是否有数据,有数据直接封装成Shop,没数据则查询Mysql;
        if(!entries.isEmpty()) {
            if(entries.containsKey("NULL")) {
                return null;
            }
            //redis中存在,则直接转换为Shop对象并返回
            Shop shop = BeanUtil.fillBeanWithMap(entries, new Shop(), false);
            return shop;
        }
        //redis中没数据,查询mysql
        Shop shop = getById(id);
        //如果为空,直接返回错误
        if(shop == null) {
            //将空值写入redis
            HashMap<Object, Object> tempMap = new HashMap<>();
            tempMap.put("NULL", "NULL");
            stringRedisTemplate.opsForHash().putAll(idKey, tempMap);
            stringRedisTemplate.expire(idKey, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        shop.setDistance(123.0);
        //将Shop转为Hash并存入redis
        Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(),
                new CopyOptions().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(idKey, shopMap);
        stringRedisTemplate.expire(idKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回对象
        return shop;
    }

    //解决高并发下的缓存击穿
    public Shop queryByIdWithMutex(Long id) {
        //先查redis
        String idKey = RedisConstants.CACHE_SHOP_KEY + id;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(idKey);
        //判断是否有数据,有数据直接封装成Shop,没数据则查询Mysql;
        if(!entries.isEmpty()) {
            if(entries.containsKey("NULL")) {
                return null;
            }
            //redis中存在,则直接转换为Shop对象并返回
            Shop shop = BeanUtil.fillBeanWithMap(entries, new Shop(), false);
            return shop;
        }
        //redis中没数据,查询mysql并重建redis
        //尝试获取锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            if(!tryLock(lockKey)) {
                //获取失败
                Thread.sleep(200);
                return queryByIdWithMutex(id);
            }
            shop = getById(id);
            //如果为空,直接返回错误
            if(shop == null) {
                //将空值写入redis
                HashMap<Object, Object> tempMap = new HashMap<>();
                tempMap.put("NULL", "NULL");
                stringRedisTemplate.opsForHash().putAll(idKey, tempMap);
                stringRedisTemplate.expire(idKey, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            shop.setDistance(123.0);
            //将Shop转为Hash并存入redis
            Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(),
                    new CopyOptions().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            stringRedisTemplate.opsForHash().putAll(idKey, shopMap);
            stringRedisTemplate.expire(idKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unlock(lockKey);
        }
        return shop;
    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);




    //尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    //模拟事先载入热点数据到redis缓存中;
    public void saveShopRedis(Long id, Long expireSeconds) {

        log.info("haihaihai");
        //查询对象
        Shop shop = getById(id);
        shop.setDistance(123.0);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        LocalDateTime localDateTime = LocalDateTime.now().plusSeconds(expireSeconds);
        redisData.setExpireTime(localDateTime);
        redisData.setData(shop);
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result updateByIdWithRedis(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("商户id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除redis缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
