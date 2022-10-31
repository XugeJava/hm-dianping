package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
  @Autowired
  StringRedisTemplate redisTemplate;

  @Override
  public Result listQuery() {
    String key = "cache:typelist";
    //1.在redis中间查询
    List<String> shopTypeList = null;
    shopTypeList = redisTemplate.opsForList().range(key,0,-1);
    //2.判断是否缓存中了
    //3.中了返回,但是需要封装成List<ShopType>类型的数据
    if(!shopTypeList.isEmpty()){
      List<ShopType> typeList = new ArrayList<>();
      for (String s:shopTypeList) {
        //参数1 序列化的字符串，参数 2 需要转换的类型
        //作用：将一个字符串转为bean
        ShopType shopType = JSONUtil.toBean(s,ShopType.class);
        typeList.add(shopType);
      }
      return Result.ok(typeList);
    }
    //4.数据库查数据
    List<ShopType> typeList = query().orderByAsc("sort").list();
    //5.不存在直接返回错误
    if(typeList.isEmpty()){
      return Result.fail("不存在店铺分类");
    }
    for(ShopType shopType : typeList){
      //将一个对象转为json字符串
      String s = JSONUtil.toJsonStr(shopType);

      shopTypeList.add(s);
    }
    //6.存在直接添加进缓存
    redisTemplate.opsForList().rightPushAll(key, shopTypeList);
    return Result.ok(typeList);
  }
}
