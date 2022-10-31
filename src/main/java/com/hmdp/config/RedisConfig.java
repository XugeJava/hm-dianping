package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * author: yjx
 * Date :2022/10/2811:55
 **/
@Configuration
public class RedisConfig {
  @Bean
  public RedissonClient redissonClient(){
    //配置类
    Config config = new Config();
    //添加redis地址，这里添加了单点地址，也可以使用config.useClusterServers()添加集群地址
    config.useSingleServer().setAddress("redis://www.xuge.site:6379").setPassword("609483");
    //创建客户端
    return Redisson.create(config);

  }
}
