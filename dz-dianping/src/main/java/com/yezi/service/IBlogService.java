package com.yezi.service;

import com.yezi.dto.Result;
import com.yezi.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result earliestLikedUsers(Long id);


    Result saveBlog(Blog blog);

    Result queryFollowBlog(Long max, Integer offset);
}
