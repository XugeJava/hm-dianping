package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * author: yjx
 * Date :2022/10/1716:54
 **/
@Component
public class RedisIdWorker {
  private final int BIT_COUNT=32;
  @Autowired
  StringRedisTemplate redisTemplate;
  private final long BEGIN_TIMESTAMP=LocalDateTime.of(2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
  public long nextId(String prefix){
    //1.生成时间戳
    LocalDateTime now = LocalDateTime.now();
    long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
    long timeStamp=nowSecond-BEGIN_TIMESTAMP;

    //2.生成序列号
    //获取当前日期
    String str = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
    long count = redisTemplate.opsForValue().increment("incr:" + prefix + ":" + str);
    //3.拼接并返回
    return timeStamp<<BIT_COUNT|count;
  }


}
