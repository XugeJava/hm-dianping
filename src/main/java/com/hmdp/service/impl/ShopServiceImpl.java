package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtils;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.sun.org.apache.regexp.internal.RE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
  //根据id查询商品缓存的流程
  @Autowired
  StringRedisTemplate redisTemplate;
  @Autowired
  CacheUtils cacheUtils;
  @Override
  public Result queryById(Long id) {
    //缓存穿透解决
//    Shop shop = cacheUtils.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class, this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

    //互斥锁解决缓存击穿
    //Shop shop = queryWithMutex(id);
    //逻辑过期解决缓存击穿
    Shop shop = cacheUtils.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,TimeUnit.SECONDS,RedisConstants.CACHE_SHOP_TTL);
    if (shop == null) {
      return Result.fail("店铺不存在!!");
    }
    //9.返回数据
    return Result.ok(shop);
  }
  //使用线程池获取线程
  private static final ExecutorService CACHE_POOL= Executors.newFixedThreadPool(10);
  //使用逻辑过期解决缓存击穿问题
  public Shop queryWithLogicExpire(Long id) {
    //1.从redis中查询商品缓存
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    String shopJson = redisTemplate.opsForValue().get(key);
    //2.判断是否存在 isNotBlank 只有有字符串才为true
    if (StringUtils.isBlank(shopJson)) {
      //3.不存在，直接返回
      return null;
    }
    //4.若命中,需要将json转为对象
    //5判断缓存是否过期
    RedisData data = JSONUtil.toBean(shopJson, RedisData.class);
    Shop cacheShop = JSONUtil.toBean((JSONObject) data.getData(), Shop.class);
    LocalDateTime time = data.getExpireTime();
    if (time.isAfter(LocalDateTime.now())) {
      //5.1未过期,直接返回店铺信息
      return cacheShop;
    }
    //5.2过期,尝试先获取互斥锁
    String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
    boolean isLock = tryLock(lockKey);
    //6.缓存重建
    //6.1是否获取到互斥锁
    if(!isLock){
      //6.2.获取失败,直接返回信息
      return cacheShop;
    }
    //6.3.获取成功，开启独立线程，根据id查询数据库，将数据写入到redis中，并设置逻辑过期时间
    CACHE_POOL.submit(()->{
      try {
        this.saveToRedis(id,20L);
      } catch (Exception exception) {
        exception.printStackTrace();
      } finally {
        //释放锁
        delLock(lockKey);
      }
    });
    //6.4返回过期信息
    return cacheShop;
  }

  //缓存预热
  public void saveToRedis(Long id, Long expireSeconds) {
    //休眠200ms
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    //1.查询数据
    Shop shop = this.getById(id);
    //2.封装逻辑过期时间
    RedisData redisData = new RedisData();
    redisData.setData(shop);
    //设置过期时间
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

    //3.写入redis
    redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
  }

  //互斥锁实现缓存击穿问题
  public Shop queryWithMutex(Long id) {
    //1.从redis中查询商品缓存
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    String shopJson = redisTemplate.opsForValue().get(key);
    //2.判断是否存在 isNotBlank 只有有字符串才为true
    if (StringUtils.isNotBlank(shopJson)) {
      //3.存在，转为对象
      return JSONUtil.toBean(shopJson, Shop.class);
    }
    //判断命中的是否是空值
    if (shopJson != null) {
      //直接返回
      return null;
    }
    //4.若未命中
    //实现缓存重建
    //4.1先获取互斥锁
    Shop shop = null;
    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    try {
      boolean lock = tryLock(lockKey);
      //4.2判断是否获取成功
      if (!lock) {
        //4.3获取失败，休眠一段时间，等会再去查缓存
        Thread.sleep(50);
        return queryWithMutex(id);
      }
      //4.4获取成功
      //5.根据id查询数据库
      Thread.sleep(300);
      shop = this.getById(id);
      //6.判断商铺是否存在
      //7.不存在，返回错误
      if (shop == null) {
        //写入redis
        redisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
      }
      //8.存在,写缓存
      redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);

    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      //9.释放互斥锁
      delLock(lockKey);
    }
    return shop;
  }

  //缓存穿透解决
  public Shop queryWithPassThrough(Long id) {
    //1.从redis中查询商品缓存
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    String shopJson = redisTemplate.opsForValue().get(key);
    //2.判断是否存在 isNotBlank 只有有字符串才为true
    if (StringUtils.isNotBlank(shopJson)) {
      //3.存在，转为对象
      return JSONUtil.toBean(shopJson, Shop.class);
    }
    //判断命中的是否是空值
    if ("".equals(shopJson)) {
      //直接返回
      return null;
    }
    //4.若未命中
    //5.根据id查询数据库
    Shop shop = this.getById(id);
    //6.判断商铺是否存在
    //7.不存在，返回错误
    if (shop == null) {
      //写入redis
      redisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }
    //8.存在,写缓存
    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
    return shop;
  }

  //获取互斥锁
  private boolean tryLock(String key) {
    Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  //释放互斥锁
  private void delLock(String key) {
    redisTemplate.delete(key);
  }

  @Override
  @Transactional
  public Result updateShop(Shop shop) {
    //1.校验数据
    Long id = shop.getId();
    if (id == null) {
      return Result.fail("ID不能为空!!");
    }
    //2.先更新数据库
    this.updateById(shop);

    //2.删除缓存
    redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

    return Result.ok("更新数据成功!!");
  }
}
