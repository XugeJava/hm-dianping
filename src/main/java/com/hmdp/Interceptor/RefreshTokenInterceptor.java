package com.hmdp.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * author: yjx
 * Date :2022/10/1615:58
 * 专门为处理所有请求
 **/
public class RefreshTokenInterceptor implements HandlerInterceptor {
  StringRedisTemplate redisTemplate;
  public RefreshTokenInterceptor(StringRedisTemplate redisTemplate){
    this.redisTemplate=redisTemplate;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //1.todo 获取请求头中的token
    String token = request.getHeader("authorization");
    if(StringUtils.isBlank(token)){
      //不存在，不拦截 未授权
//      response.setStatus(401);
      return true;
    }
    //2.todo 基于token获取redis中的用户
    String key=RedisConstants.LOGIN_USER_KEY + token;
    Map<Object, Object> map = redisTemplate.opsForHash().entries(key);

    //3.判断用户是否存在
    if(map.size()==0){
      //放行
      return true;
    }
    //5.todo 将查询的map对象转为userDto对象
    UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
    //6.保存用户信息到ThreadLocal
    UserHolder.saveUser(userDTO);
    //7.刷新token有效期
    redisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
    //8.放行
    return true;

  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    UserHolder.removeUser();
  }
}
