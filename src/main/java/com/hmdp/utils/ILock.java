package com.hmdp.utils;

/**
 * author: yjx
 * Date :2022/10/2719:17
 **/
public interface ILock {
  //尝试获取锁  true 表示获取成功  false 表示获取失败
  boolean tryLock(long timeoutSec);
  //释放锁
  void unlock();
}
