package com.yezi.service;

import com.yezi.dto.Result;
import com.yezi.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result isFollow(Long id);

    Result followChange(Long id, Boolean flag);

    Result commonFollow(Long id);
}
