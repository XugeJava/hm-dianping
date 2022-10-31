package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
  @Autowired
  private IUserService userService;
  @Autowired
  private StringRedisTemplate redisTemplate;
  @Override
  public Result queryById(Long id) {
    Blog blog = this.getById(id);
    if(blog==null){
      return  Result.fail("博客不存在！！");
    }
    //获取用户Id
    this.getBlog(blog);
    //查询博客是否被点赞了
    this.isBlogLiked(blog);
    return Result.ok(blog);
  }

  private void isBlogLiked(Blog blog) {
    UserDTO user = UserHolder.getUser();
    if(user==null){
      log.info("当前用户未登陆!");
      return ;
    }
    Double score=redisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY+blog.getId(),user.getId().toString());
    blog.setIsLike(score!=null);
  }

  @Override
  public Result likeBlog(Long id) {
    //1.判断当前用户是否点赞了
    UserDTO user = UserHolder.getUser();
    if(user==null){
      return Result.ok("当前用户未登陆!!");
    }
    Double score = redisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, user.getId().toString());
    //2.如果未点赞，可以点赞
    if(score==null){
      //2.1数据库点赞数加1
      boolean success = this.update().setSql("liked=liked+1").eq("id", id).update();
      if(success){
        //2.2保存用户ID到redis的set集合中  zadd key val  score
        redisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY+id,user.getId().toString(),System.currentTimeMillis());
      }
      return Result.ok();
    }
    //3.如果已点赞，取消点赞
    //3.1数据库点赞数减1
    boolean success2 = this.update().setSql("liked=liked-1").eq("id", id).update();
    //3.2移除用户ID在redis的set集合
    if(success2){
      redisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY+id,user.getId().toString());
    }
    return Result.ok();
  }

  @Override
  public Result queryHotBlog(Integer current) {
    // 根据用户查询
    Page<Blog> page = this.query()
            .orderByDesc("liked")
            .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    // 获取当前页数据
    List<Blog> records = page.getRecords();
    // 查询用户
    records.forEach(blog ->{
      getBlog(blog);
      this.isBlogLiked(blog);

    });
    return Result.ok(records);
  }

  @Override
  public Result queryBlogLikes(Long id) {
    String key=RedisConstants.BLOG_LIKED_KEY+id;
    //1.查询top5中的点赞用户 zrange key 0 4
    Set<String> set = redisTemplate.opsForZSet().range(key, 0, 4);
    if(set==null){
      return Result.ok(Collections.emptyList());
    }
    //2.解析其中的用户Id
    List<Long> idS = set.stream().map(Long::valueOf).collect(Collectors.toList());
    //3.根据用户ID查询UserDto
    String idStr= StrUtil.join(",",idS);
    List<UserDTO> list = userService.query().in("id",idS).last("ORDER BY FIELD(id,"+idStr+")").list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
            .collect(Collectors.toList());
    //4.返回
    return Result.ok(list);
  }

  private void getBlog(Blog blog) {
    Long userId = blog.getUserId();
    User user = userService.getById(userId);
    blog.setName(user.getNickName());
    blog.setIcon(user.getIcon());
  }
}
