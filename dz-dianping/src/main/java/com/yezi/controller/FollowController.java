package com.yezi.controller;


import com.yezi.dto.Result;
import com.yezi.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return followService.isFollow(id);
    }

    @PutMapping("/{id}/{flag}")
    public Result followChange(@PathVariable("id") Long id, @PathVariable("flag") Boolean flag) {
        return followService.followChange(id, flag);
    }

    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id) {
        return followService.commonFollow(id);
    }


}
