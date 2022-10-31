package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
  @Autowired
  StringRedisTemplate redisTemplate;
  @Autowired
  IUserService userService;
  @Override
  public Result follow(Long followId, boolean isFollow) {
    UserDTO user = UserHolder.getUser();
    if(user==null){
      return Result.ok("请先登录!!");
    }
    String key= RedisConstants.FOLLOW_KEY+user.getId();
    //1.判断到底是关注还是取关
    if(isFollow){//关注
      //插入一条数据
      Follow follow = new Follow();
      follow.setFollowUserId(followId);
      follow.setUserId(user.getId());
      boolean isSuccess = this.save(follow);
      if(isSuccess){
        //同时将该用户的关注列表加入redis  sadd userId followUserIds
        redisTemplate.opsForSet().add(key,followId.toString());
      }
    }else{//取关
      boolean isSuccess = this.remove(new QueryWrapper<Follow>().eq("follow_user_id", followId).eq("user_id", user.getId()));
      if(isSuccess){
        //从redis中删除
        redisTemplate.opsForSet().remove(key,followId.toString());
      }
    }
    return Result.ok();
  }

  @Override
  public Result isFollow(Long followId) {
    UserDTO user = UserHolder.getUser();
    if(user==null){
      return Result.ok("用户未登陆!!");
    }

    //1.查询是否关注
    Integer count = this.query().eq("follow_user_id", followId).eq("user_id", user.getId()).count();
    return Result.ok(count>0?"已关注!!":"未关注");
  }

  @Override
  public Result commonFollowList(Long followId) {
    //1.获取当前用户关注列表
    UserDTO user = UserHolder.getUser();
    String key1="follow:"+user.getId();
    String key2="follow:"+followId;

    //2.求其交集
    Set<String> set = redisTemplate.opsForSet().intersect(key1, key2);
    if(set==null||set.isEmpty()){
      return Result.ok("抱歉，你两无交集哦!!");
    }
    //3.解析出id
    List<Long> list = set.stream().map(Long::valueOf).collect(Collectors.toList());
    List<UserDTO> res = userService.listByIds(list).stream().map(u1 ->
            BeanUtil.copyProperties(u1, UserDTO.class)).collect(Collectors.toList());
    //3.返回结果
    return Result.ok(res);
  }
}
