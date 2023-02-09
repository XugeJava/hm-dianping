package com.hmdp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // TODO 实现登录功能
        //校验验证码
        if (ObjectUtil.isEmpty(loginForm)) {
            return Result.fail("参数不可为空");
        }
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if (ObjectUtil.isEmpty(phone)) {
            return Result.fail("手机号不可为空");
        }
        if (ObjectUtil.isEmpty(code)) {
            return Result.fail("验证码不可为空");
        }
        String saveCode = (String) session.getAttribute("code");
        if (!code.equals(saveCode)) {
            return Result.fail("验证码不正确");
        }
        User user = this.getOne(Wrappers.<User>query().lambda().eq(User::getPhone, phone));
        if (ObjectUtil.isEmpty(user)) {
            //创建新用户
            user = new User();
            user.setPhone(phone);
            user.setIcon("/imgs/icons/kkjtbcr.jpg");
            user.setNickName("徐哥");
            boolean save = this.save(user);
            log.info("执行新增user=>{}", save);
        }
        session.setAttribute("user", user);
        return Result.ok();
    }

    @Override
    public Result me() {

        return null;
    }
}
