package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
  @Autowired
  IFollowService followService;
  @PutMapping("/{id}/{isFollow}")
  public Result tryFollow(@PathVariable("id")Long followId,@PathVariable("isFollow")boolean isFollow){

    //todo
    return followService.follow(followId,isFollow);
  }
  @GetMapping("/or/not/{id}")
  public Result checkFollow(@PathVariable("id") Long FollowId ){
    //todo
    return followService.isFollow(FollowId);
  }
  @GetMapping("/common/{id}")
  public Result commonList(@PathVariable("id") Long FollowId){
    return followService.commonFollowList(FollowId);
  }

}
