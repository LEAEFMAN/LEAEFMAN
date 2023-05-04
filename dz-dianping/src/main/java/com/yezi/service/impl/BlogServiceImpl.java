package com.yezi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yezi.dto.Result;
import com.yezi.dto.ScrollResult;
import com.yezi.dto.UserDTO;
import com.yezi.entity.Blog;
import com.yezi.entity.Follow;
import com.yezi.entity.User;
import com.yezi.mapper.BlogMapper;
import com.yezi.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yezi.service.IFollowService;
import com.yezi.service.IUserService;
import com.yezi.utils.SystemConstants;
import com.yezi.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;


    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            blogChange(blog);
        });
        return Result.ok(records);
    }

    public Boolean isLiked(Long id) {
        //判断当前用户是否点赞过该线程
        UserDTO user = UserHolder.getUser();
        if(user == null) {
            return false;
        }
        Long userId = user.getId();
        return stringRedisTemplate.opsForZSet().score("blog:liked:" + id, userId.toString()) != null;
    }

    //使博客信息更详细
    public void blogChange(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        blog.setIsLike(isLiked(blog.getId()));
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null)return Result.fail("博客不存在");

        blogChange(blog);
        return Result.ok(blog);

    }

    @Override
    public Result likeBlog(Long id) {
        String key = "blog:liked:" + id;
        Long userId = UserHolder.getUser().getId();
        if(BooleanUtil.isFalse(isLiked(id))) {
            //不存在,说明未点赞过，执行点赞逻辑
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess) {
                //redis添加当前userId到set集合
                stringRedisTemplate.opsForZSet().add(key,userId.toString(), System.currentTimeMillis());
            }
            return Result.ok();
        }
        //执行取消点赞逻辑
        boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
        if(isSuccess) {
            //redis从set集合中移除当前userId
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result earliestLikedUsers(Long id) {
        String key = "blog:liked:" + id;
        //获取最早点名的用户ID
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据Ids查询用户集合并转换成UserDtoList
        String idsStr = StrUtil.join(",", userIds);
        Stream<UserDTO> userList = userService
                .query().in("id", userIds).last("order by field(id," + idsStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class));
        //UserList封装成UserDtoList

        return Result.ok(userList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = this.save(blog);
        if(!save) {
            return Result.fail("上传失败");
        }
        //查询出该用户的所有粉丝
        List<Follow> followList = followService.list(new QueryWrapper<Follow>().eq("follow_user_id", blog.getUserId()));
        for (Follow follow : followList) {
            Long fanId = follow.getUserId();
            String key = "feed:" + fanId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    //实现显示关注的博客分页功能
    public Result queryFollowBlog(Long max, Integer offset) {
        Long id = UserHolder.getUser().getId();
        String key = "feed:" + id;
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if(tuples == null || tuples.isEmpty()) {
            return Result.fail("暂无推送");
        }
        List<Long> ids = new ArrayList<>(tuples.size());
        int cnt = 0;
        long minTime = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            long time = tuple.getScore().longValue();
            ids.add(Long.valueOf(tuple.getValue()));
            cnt = (minTime == time) ? cnt + 1 : 1;
            minTime = time;
        }
        String idsStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idsStr + ")").list();
        for (Blog blog : blogs) {
            blogChange(blog);
        }
        //准备返回参数
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(cnt);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
