package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
  @Autowired
  ISeckillVoucherService seckillVoucherService;
  @Autowired
  RedisIdWorker idWorker;
  @Autowired
  StringRedisTemplate redisTemplate;
  @Autowired
  RedissonClient redissonClient;
  private static final DefaultRedisScript<Long> seckillScript;

  static {
    seckillScript = new DefaultRedisScript<>();
    //设置脚本位置
    seckillScript.setLocation(new ClassPathResource("seckill.lua"));
    //设置返回值类型
    seckillScript.setResultType(Long.class);
  }


  //线程池
  private static final ExecutorService EX = Executors.newSingleThreadExecutor();
  //代理对象
  private IVoucherOrderService proxy;
//  //阻塞队列
//  private static BlockingQueue<VoucherOrder> queue = new ArrayBlockingQueue<>(1024 * 1024);
//  //资源任务类
//  public  class VoucherOrderHandler implements Runnable {
//    @Override
//    public void run() {
//      //不断从队列中获取订单信息
//      while (true) {
//        try {
//          //1.获取队列中的订单信息
//          VoucherOrder order = queue.take();
//          //2.处理订单
//          handleVoucherOrder(order);
//        } catch (InterruptedException e) {
//          log.info("处理订单失败.." + e);
//        }
//      }
//    }
    //资源任务类
    public  class VoucherOrderHandler implements Runnable {
      @Override
      public void run() {
        //不断从队列中获取订单信息
        String queueName="stream.orders";
        while (true) {
          try {
            //1.获取消息队列中的订单信息  XREADGROUP GROUP g1  c1 count 1 BLOCK 2000  STREAMS  stream.orders >
            List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                    Consumer.from("g1", "c1"),
                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                    StreamOffset.create(queueName, ReadOffset.lastConsumed())
            );
            //2.判断消息是否存在，若不存在，继续下一次循环
            if(list==null||list.isEmpty()){
              //说明没有消息，继续下一次循环
              continue;
            }
            //3.如果获取成功，创建订单
            //解析list中的消息
            MapRecord<String, Object, Object> entries = list.get(0);
            Map<Object, Object> val = entries.getValue();
            VoucherOrder order = BeanUtil.fillBeanWithMap(val, new VoucherOrder(), true);
            //4.处理订单
            handleVoucherOrder(order);
            //5.ACK确认 SACK stream.orders g1  id
            redisTemplate.opsForStream().acknowledge("stream.orders", "g1", entries.getId());
          } catch (Exception e) {
            handlePendingList();
          }
        }
      }
  //处理未被确认的消息pending-list
  private void handlePendingList() {
    String queueName="stream.orders";
    while (true) {
      try {
        //1.获取pending-list中的订单信息  XREADGROUP GROUP g1  c1 count 1 BLOCK 2000  STREAMS  stream.orders 0
        List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                Consumer.from("g1", "c1"),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(queueName, ReadOffset.from("0"))
        );
        //2.判断消息是否存在，若不存在，继续下一次循环
        if(list==null||list.isEmpty()){
          //说明pending-list没有消息，结束循环
         break;
        }
        //3.如果获取成功，创建订单
        //解析list中的消息
        MapRecord<String, Object, Object> entries = list.get(0);
        Map<Object, Object> val = entries.getValue();
        VoucherOrder order = BeanUtil.fillBeanWithMap(val, new VoucherOrder(), true);
        //创建订单
        getResult(order);
        //4.处理消息
        handleVoucherOrder(order);
        //5.ACK确认 SACK stream.orders g1  id
        redisTemplate.opsForStream().acknowledge(queueName, "g1", entries.getId());
      } catch (Exception e) {
        log.error("处理pending-list异常!!");
        try {
          Thread.sleep(50);
        } catch (InterruptedException interruptedException) {
          interruptedException.printStackTrace();
        }
//        continue;
      }
    }
  }

  private void handleVoucherOrder(VoucherOrder order) {
      Long userId = order.getUserId();
      //通过Redisson解决线程安全问题
      RLock lock = redissonClient.getLock("lock:order" + userId);
      boolean isLock = lock.tryLock();
      //获取成功
      if (!isLock) {
        log.error("不允许重复下单!!");
        return ;
      }
      try {
         //调用插入订单操作
         proxy.getResult(order);
      } finally {
        //释放锁
        lock.unlock();
      }
    }
  }



  //在该类初始化后之后执行方法，此时执行的是voucherOrderHandler任务
  @PostConstruct
  public void init() {
    EX.submit(new VoucherOrderHandler());
  }

  @Override
  public Result seckillVoucher(Long voucherId) {
    //用户ID
    Long userId = UserHolder.getUser().getId();
    //订单id
    Long orderId = idWorker.nextId("order");
    //1.执行lua脚本
    Long res = redisTemplate.execute(seckillScript, Collections.emptyList(), voucherId.toString(), userId.toString(),orderId.toString());
    //2.判断结果是否为0
    int val = res.intValue();
    if (val != 0) {
      //3.不为0，没有购买资格
      return Result.fail(val == 1 ? "库存不足!!" : "重复下单!!");
    }
    //4.若为0，有购买资格,把下单信息保存到消息队列中
    //将订单信息保存到阻塞队列交给异步线程去解决任务
//    VoucherOrder order = new VoucherOrder();
//    order.setId(orderId);
//    order.setUserId(userId);
//    order.setVoucherId(voucherId);
//    //5.将订单加入阻塞队列
//    queue.add(order);
    //6.获取代理对象 这个是主线程才可以获取对应的代理对象
    //获取IVoucherOrderService 代理对象初始化
    proxy = (IVoucherOrderService) AopContext.currentProxy();
    //7.返回订单id
    return Result.ok(orderId);
  }

//  //基于lua脚本判断用户资格，同时实现基于阻塞队列加线程池实现异步下单
//  @Override
//  public Result seckillVoucher(Long voucherId) {
//    //用户ID
//    Long userId = UserHolder.getUser().getId();
//    //订单id
//    Long orderId = idWorker.nextId("order");
//    //1.执行lua脚本
//    Long res = redisTemplate.execute(seckillScript, Collections.emptyList(), voucherId.toString(), userId.toString(),orderId.toString());
//    //2.判断结果是否为0
//    int val = res.intValue();
//    if (val != 0) {
//      //3.不为0，没有购买资格
//      return Result.fail(val == 1 ? "库存不足!!" : "重复下单!!");
//    }
//    //4.若为0，有购买资格,把下单信息保存到消息队列中
//    //将订单信息保存到阻塞队列交给异步线程去解决任务
//    VoucherOrder order = new VoucherOrder();
//    order.setId(orderId);
//    order.setUserId(userId);
//    order.setVoucherId(voucherId);
//    //5.将订单加入阻塞队列
//    queue.add(order);
//    //6.获取代理对象 这个是主线程才可以获取对应的代理对象
//    //获取IVoucherOrderService 代理对象初始化
//    proxy = (IVoucherOrderService) AopContext.currentProxy();
//    //7.返回订单id
//    return Result.ok(orderId);
//  }
//  public Result seckillVoucher(Long id) {
//    //1.查询秒杀优惠劵信息
//    SeckillVoucher seckillVoucher = seckillVoucherService.getById(id);
//
//    //2.判断秒杀是否开始
//    if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//      //尚未开始秒杀
//      return Result.fail("秒杀尚未开始!!!");
//    }
//    //3.判断秒杀是否已经结束
//    if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//      return Result.fail("秒杀已经结束!!");
//    }
//    //4.判断库存是否充足
//    if (seckillVoucher.getStock() <= 0) {
//      return Result.fail("库存不足!!");
//    }
//
//    //Long userId = UserHolder.getUser().getId();
////    //锁当前用户id转为字符串值  先获取锁，等事务提交后，在释放锁
////    synchronized(userId.toString().intern()) {
////      //获取IVoucherOrderService 代理对象
////      IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
////      return proxy.getResult(id);
////    }
//
//    Long userId = UserHolder.getUser().getId();
//    //通过Redisson解决线程安全问题
//    RLock lock = redissonClient.getLock("lock:order" + userId);
//
//    boolean isLock = lock.tryLock();
//    //获取成功
//    if(!isLock){
//      return Result.fail("不允许重复下单!!!");
//    }
//    try {
//      //获取IVoucherOrderService 代理对象
//      IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//      return proxy.getResult(id);
//    } finally {
//      //释放锁
//      lock.unlock();
//    }
////    //通过自定义分布式锁对象实现一人一单线程安全问题
////    SimpleRedisLock lock = new SimpleRedisLock(redisTemplate, "order" + userId);
////    //获取锁
////    boolean isLock = lock.tryLock(1200);
////    //获取成功
////    if(!isLock){
////      return Result.fail("不允许重复下单!!!");
////    }
////    try {
////      //获取IVoucherOrderService 代理对象
////      IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////      return proxy.getResult(id);
////    } finally {
////      //释放锁
////      lock.unlock();
////    }
//  }
@Transactional
public void  getResult(VoucherOrder voucherOrder) {
  Long userId = voucherOrder.getId();
  //5.一人一单判断
  Integer count = this.query().eq("voucher_id", voucherOrder.getVoucherId()).eq("user_id", userId).count();
  if (count >= 1) {
    //用户已购买
    log.error("用户已购买！！");
    return ;
  }

  //6.扣减库存
  boolean flag = seckillVoucherService.update().setSql("stock=stock-1").//set stock=stock-1
          eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
  //where voucher_id=? and  stock=?
  if (!flag) {
    log.error("库存不足！！");
    return ;
  }
  //执行插入订单操作
  this.save(voucherOrder);
  return ;
}
//  @Transactional
//  public Result getResult(Long voucherId) {
//    Long userId = UserHolder.getUser().getId();
//    //5.一人一单判断
//    Integer count = this.query().eq("voucher_id", voucherId).eq("user_id", userId).count();
//    if (count >= 1) {
//      //用户已购买
//      return Result.fail("用户已经购买过一次!!");
//    }
//
//    //6.扣减库存
//    boolean flag = seckillVoucherService.update().setSql("stock=stock-1").//set stock=stock-1
//            eq("voucher_id", voucherId).gt("stock", 0).update();
//    //where voucher_id=? and  stock=?
//    if (!flag) {
//      return Result.fail("库存不足!!");
//    }
//
//
//    //7.创建订单
//    VoucherOrder order = new VoucherOrder();
//    //7.1.订单Id
//    long orderId = idWorker.nextId("order");
//    //7.2.用户Id
//    //上面已经获取过
//    //7.3.代金券id
//    order.setId(orderId);
//    order.setUserId(userId);
//    order.setVoucherId(voucherId);
//    this.save(order);
//    //7.返回订单Id
//    return Result.ok(orderId);
//  }
}
