package com.yezi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yezi.dto.LoginFormDTO;
import com.yezi.dto.Result;
import com.yezi.dto.UserDTO;
import com.yezi.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);

    UserDTO queryById(Long userId);

}
