package com.yezi;

import com.yezi.service.IShopService;
import com.yezi.utils.CacheClient;
import com.yezi.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class DzDianPingApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;


    @Test
    void testSaveShop() {
        for(int i = 0; i < 100; i++) {
            System.out.println(redisIdWorker.nextId("order"));
        }
    }

}
