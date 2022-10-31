package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * author: yjx
 * Date :2022/10/2719:19
 * 简答的分布式锁实现
 **/
public class SimpleRedisLock implements ILock{
  private String name ;
  private StringRedisTemplate redisTemplate;
  private static final String prefix="lock:";
  private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
  private static final DefaultRedisScript<Long> unlockScript;
  static {
    unlockScript=new DefaultRedisScript<>();
    //设置脚本位置
    unlockScript.setLocation(new ClassPathResource("unlock.lua"));
    //设置返回值类型
    unlockScript.setResultType(Long.class);
  }
  public SimpleRedisLock(StringRedisTemplate redisTemplate,String name ){
    this.redisTemplate=redisTemplate;
    this.name=name ;
  }
  /*
    nx:是互斥
    ex:设置过期时间
   */
  @Override
  public boolean tryLock(long timeoutSec) {
    //获取当前线程
    String threadId= ID_PREFIX+Thread.currentThread().getId();
    //如果不存在Key才设置
    boolean flag=redisTemplate.opsForValue().setIfAbsent(prefix+name , threadId,timeoutSec, TimeUnit.SECONDS);
    //避免空指针
    return Boolean.TRUE.equals(flag);
  }
  @Override
  public void unlock() {
    String threadId= ID_PREFIX+Thread.currentThread().getId();
    //调用脚本去执行lua 释放锁
    redisTemplate.execute(unlockScript, Collections.singletonList(prefix+name),threadId);
  }
//  @Override
//  public void unlock() {
//    //先获取线程标识
//    String threadId= ID_PREFIX+Thread.currentThread().getId();
//    //获取当前锁线程标识，即value
//    String lockId = redisTemplate.opsForValue().get(prefix + name);
//    if(threadId.equals(lockId)){
//      //释放锁
//      redisTemplate.delete(prefix+name);
//    }
//  }
}
