package com.yezi.service;

import com.yezi.dto.Result;
import com.yezi.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateByIdWithRedis(Shop shop);

    void saveShopRedis(Long id, Long expireSeconds);
}
