package com.yezi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yezi.dto.Result;
import com.yezi.dto.UserDTO;
import com.yezi.entity.Follow;
import com.yezi.mapper.FollowMapper;
import com.yezi.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yezi.service.IUserService;
import com.yezi.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    //判断当前用户是否关注了该用户
    public Result isFollow(Long id) {
        //获取自己id
        Long myId = UserHolder.getUser().getId();
        //组合key
        String key = "follows:" + myId;
        //查询redis
        Boolean flag = stringRedisTemplate.opsForSet().isMember(key, id.toString());
        return Result.ok(flag);
    }

    @Override
    public Result followChange(Long id, Boolean flag) {
        //获取自己id
        Long myId = UserHolder.getUser().getId();
        //组合key
        String key = "follows:" + myId;
        if(BooleanUtil.isFalse(flag)) {
            //如果传来false,则取消关注
            boolean remove = remove(new QueryWrapper<Follow>()
                    .eq("user_id", myId)
                    .eq("follow_user_id", id));

            if(remove) {
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
            return Result.ok();
        }
        //未关注，进行关注逻辑
        Follow follow = new Follow();
        follow.setUserId(myId);
        follow.setFollowUserId(id);
        boolean insert = this.save(follow);
        if(insert) {
            stringRedisTemplate.opsForSet().add(key, id.toString());
        }
        return Result.ok();
    }

    @Override
    public Result commonFollow(Long id) {
        Long myId = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("follows:" + id, "follows:" + myId);
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids).stream()
                .map(o -> BeanUtil.copyProperties(o, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
