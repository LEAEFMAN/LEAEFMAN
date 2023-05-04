package com.yezi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yezi.dto.LoginFormDTO;
import com.yezi.dto.Result;
import com.yezi.dto.UserDTO;
import com.yezi.entity.User;
import com.yezi.mapper.UserMapper;
import com.yezi.service.IUserService;
import com.yezi.utils.RedisConstants;
import com.yezi.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yezi.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，直接返回错误消息
            return Result.fail("手机号格式错误");
        }
        //3.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码和手机号,保存到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("code is + " + code);
        //6.返回空
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        //1.检验手机号格式
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
//        //2.检验手机号是否相同
//        Object cachePhone = session.getAttribute("phone");
//        if(cachePhone == null || !cachePhone.toString().equals(phone)) {
//            return Result.fail("请获取验证码");
//        }
        //3.检验验证码是否相同
        String code = loginForm.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if(cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码有误");
        }
        //4.查询用户是否存在，不存在则先创建用户;
        User user = this.query().eq("phone", phone).one();
        if(user == null) {
            user =  createWithPhone(phone);
        }

        //5.保存用户信息到redis中
        //5.1生成token
        String token = UUID.randomUUID().toString(true);
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        //5.2将User对象Hash存储，键为token
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                new CopyOptions().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //5.3设置token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //5.4返回token给前端
        return Result.ok(token);

    }

    @Override
    public UserDTO queryById(Long userId) {
        User user = getById(userId);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return userDTO;
    }


    private User createWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
