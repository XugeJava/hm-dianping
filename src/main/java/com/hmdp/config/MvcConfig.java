package com.hmdp.config;

import com.hmdp.Interceptor.LoginInterceptor;
import com.hmdp.Interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * author: yjx
 * Date :2022/10/1614:20
 **/
@Configuration
public class MvcConfig implements WebMvcConfigurer {
  @Autowired
  StringRedisTemplate redisTemplate;
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    //这个拦截器拦截部分请求
    //注意order值越低，优先级越高，必须先让拦截所有请求那个拦截器先执行，再让LoginInterceptor执行
    registry.addInterceptor(new LoginInterceptor())
            .excludePathPatterns(
                    "/user/code","/user/login","/blog/hot","/shop/**","/shop-type/**","/upload/**","/voucher/**"
            ).order(1);
    //拦截所有请求
    registry.addInterceptor(new RefreshTokenInterceptor(redisTemplate)).addPathPatterns("/**").order(0);
  }
}
