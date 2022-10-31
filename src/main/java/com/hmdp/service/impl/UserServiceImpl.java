package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
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
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
  @Autowired
  StringRedisTemplate redisTemplate;
  @Override
  public Result sendCode(String phone, HttpSession session) {
    //1.校验手机号
    if(RegexUtils.isPhoneInvalid(phone)){
      //2.如果不符合，返回
      return Result.fail("手机号格式有误");
    }
    //3.符合，生成验证码
    String code = RandomUtil.randomNumbers(6);
    //4.将验证码保存到session中
//    session.setAttribute("code",code);
    //4.将验证码保存到redis中 ,设置过期时间为2分钟
    redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone, code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
    //5.发送验证码给用户
    log.info("发送短信验证码成功，验证码为:{}",code);
    return Result.ok();
  }
  //处理用户登录
  @Override
  public Result login(LoginFormDTO loginForm, HttpSession session) {
    //1.校验手机号
    String phone = loginForm.getPhone();
    if(RegexUtils.isPhoneInvalid(phone)){
      //2.如果不符合，返回
      return Result.fail("手机号格式有误");
    }
    //2.校验验证码
    //todo 从redis中获取验证码
    String cacheCode=redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY +phone);
    String code = loginForm.getCode();
    if(cacheCode==null||!cacheCode.equals(code)){
      //3.如果验证码不一致，返回
      return Result.fail("验证码错误");
    }
    //4.如果验证码一致，继续
    // 根据手机号查询用户
    User user = this.query().eq("phone", phone).one();
    if(user==null){
      //5.如果用户不存在，创建新用户
      user=createUser(phone);
    }
    //6.如果用户存在，保存到session中
    //保存到redis中
//    session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
    //7.生成一个token作为令牌
    String token = UUID.randomUUID().toString(true);
    //8.将对象转为hash存储
    //将一个bean转为Map
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    //注意由于是stringRedisTemplate必须所有的key和value都是子串串类型
    Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create()
    .setIgnoreNullValue(true)
    .setFieldValueEditor((fieldName,fieldValue)->
      fieldValue.toString()));
    //存储
    redisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,map);
    //设置有效时间
    redisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token, RedisConstants.LOGIN_USER_TTL,TimeUnit.SECONDS);
    //9.将token返回前端
    return Result.ok(token);
  }

  @Override
  public Result getUser() {
    return Result.ok(UserHolder.getUser());
  }

  private User createUser(String phone) {
    User user = new User();
    user.setPhone(phone);
    user.setNickName("ac_"+RandomUtil.randomString(6));
    //保存数据
    this.save(user);
    return user;
  }
}
