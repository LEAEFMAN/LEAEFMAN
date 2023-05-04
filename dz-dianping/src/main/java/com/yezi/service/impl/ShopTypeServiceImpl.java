package com.yezi.service.impl;

import cn.hutool.json.JSONUtil;
import com.yezi.entity.ShopType;
import com.yezi.mapper.ShopTypeMapper;
import com.yezi.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yezi.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryList() {
        Long size = stringRedisTemplate.opsForList().size(RedisConstants.SHOP_TYPE_KEY);
        if(size == 0) {
            //redis中没数据,查询mysql
            List<ShopType> list = query().orderByDesc("sort").list();
            List<String> jsons = new ArrayList<>();
            for(ShopType sp : list) {
                jsons.add(JSONUtil.toJsonStr(sp));
            }
            //存入redis
            stringRedisTemplate.opsForList().leftPushAll(RedisConstants.SHOP_TYPE_KEY, jsons);
            return list;
        }
        List<String> jsons = stringRedisTemplate.opsForList().range(RedisConstants.SHOP_TYPE_KEY, 0, size - 1);
        List<ShopType> list = new ArrayList<>();
        for(String json : jsons) {
            list.add(JSONUtil.toBean(json, ShopType.class));
        }
        return list;
    }


}
