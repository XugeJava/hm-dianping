package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.User;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
  @Autowired
  ShopServiceImpl shopService;
  @Autowired
  RedisIdWorker redisIdWorker;
  @Test
  public void testSave(){
    shopService.saveToRedis(1L, 50L);
  }
  //注入线程池，并发测试
  ExecutorService ex= Executors.newFixedThreadPool(50);
  @Test
  public void testIdWorker() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(500);
    Runnable task=()->{
        for(int i=1;i<=100;i++) {
          long id = redisIdWorker.nextId("shop:buy");
//          System.out.println(id);
        }
        latch.countDown();
      };
    long start = System.currentTimeMillis();
    for(int i=1;i<=500;i++) {
      ex.submit(task);
    }
    latch.await();
    long end = System.currentTimeMillis();
    System.out.println((end-start));
    }



}


