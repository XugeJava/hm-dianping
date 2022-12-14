package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
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
  //尝试去关注博主
  Result follow(Long followId, boolean isFollow);
  //校验当前用户是否关注了博主
  Result isFollow(Long followId);

  Result commonFollowList(Long followId);
}
