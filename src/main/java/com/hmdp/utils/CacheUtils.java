package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * author: yjx
 * Date :2022/10/1712:57
 **/
@Slf4j
@Component
public class CacheUtils {
  @Autowired
  StringRedisTemplate redisTemplate;

  //  public CacheUtils(StringRedisTemplate redisTemplate){
//     this.redisTemplate=redisTemplate;
//  }
  //key 为string ,value为任意对象 ，但是以jsonStr存储
  public void set(String key, Object val, Long time, TimeUnit unit) {
    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(val), time, unit);
  }

  //逻辑过期设置数据
  public void setWithLogicExpire(String key, Object val, TimeUnit unit, Long expireSeconds) {
    //设置逻辑过期
    RedisData data = new RedisData();
    //设置数据
    data.setData(val);
    //设置过期时间
    data.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(expireSeconds)));
    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
  }


  //缓存穿透解决
  public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> typeClass, Function<ID, R> dbCallBack, Long time, TimeUnit unit) {
    String key = keyPrefix + id;
    //1.从redis中查询商品缓存
    String shopJson = redisTemplate.opsForValue().get(key);
    //2.判断是否存在 isNotBlank 只有有字符串才为true
    if (StringUtils.isNotBlank(shopJson)) {
      //3.存在，转为对象
      return JSONUtil.toBean(shopJson, typeClass);
    }
    //判断命中的是否是空值
    if (shopJson != null) {
      //直接返回
      return null;
    }
    //4.若未命中
    //5.根据id查询数据库
    R r = dbCallBack.apply(id);
    //6.判断商铺是否存在
    //7.不存在，返回错误
    if (r == null) {
      //缓存空值
      redisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }
    //8.存在,写缓存
    this.set(key, r, time, unit);
    return r;
  }


  //使用线程池获取线程
  private static final ExecutorService CACHE_POOL = Executors.newFixedThreadPool(10);

  //获取互斥锁
  private boolean tryLock(String key) {
    Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  //释放互斥锁
  private void delLock(String key) {
    redisTemplate.delete(key);
  }


  //逻辑过期解决缓存击穿问题
  public <R, ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> typeClass, Function<ID, R> dbCallBack, TimeUnit unit,Long expireSeconds) {
    //1.从redis中查询商品缓存
    String key = keyPrefix + id;
    String shopJson = redisTemplate.opsForValue().get(key);
    //2.判断是否存在 isNotBlank 只有有字符串才为true
    if (StringUtils.isBlank(shopJson)) {
      //3.不存在，直接返回
      return null;
    }
    //4.若命中,需要将json转为对象
    //5判断缓存是否过期
    RedisData data = JSONUtil.toBean(shopJson, RedisData.class);
    R r = JSONUtil.toBean((JSONObject) data.getData(), typeClass);
    LocalDateTime expireTime = data.getExpireTime();
    if (expireTime.isAfter(LocalDateTime.now())) {
      //5.1未过期,直接返回店铺信息
      return r;
    }
    //5.2过期,尝试先获取互斥锁
    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    boolean isLock = tryLock(lockKey);
    //6.缓存重建
    //6.1是否获取到互斥锁
    if (!isLock) {
      //6.2.获取失败,直接返回信息
      return r;
    }
    //6.3.获取成功，开启独立线程，根据id查询数据库，将数据写入到redis中，并设置逻辑过期时间
    CACHE_POOL.submit(() -> {
      try {
        //查询数据库
        R query = dbCallBack.apply(id);
        //写入redis
        setWithLogicExpire(key, query,unit,expireSeconds);
      } catch (Exception exception) {
        exception.printStackTrace();
      } finally {
        //释放锁
        delLock(lockKey);
      }
    });
    //6.4返回过期信息
    return r;
  }

}
