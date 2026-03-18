package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Object Sendcode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号不符合");
        }
        //3.符合，生成验证码
        String CacheCode = RandomUtil.randomNumbers(6);

        //4.保存验证码到session

        session.setAttribute("CacheCode", CacheCode);
        //5.发送验证码

        log.info("验证码消息：{}",CacheCode);
        log.info("session详细id{}",session.getId());
        return Result.ok();
    }

    @Override
    public Object login(LoginFormDTO loginForm, HttpSession session) {
        // 1、校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式不正确");
        }
        // 2、校验验证码,验证码不一致
        if (session.getAttribute("CacheCode") == null || !session.getAttribute("CacheCode").equals(loginForm.getCode())) {
            return Result.fail("验证码不一致");
        }
        // 3、验证码一致，查询手机号用户 select * from tb_user where phone = ?
        // query() 相当于 select * from tb_user，eq是where的条件语句， one 的意思是获取到一个user
        User user = query().eq("phone",loginForm.getPhone()).one();
        // 4、用户是否存在，不存在就创建新用户，保存到数据库
        if (user == null) {
            user = createWithPhone(loginForm.getPhone());

        }
        // 5、用户存在，保存用户到session
        session.setAttribute("user",user);
        return null;
    }



    private User createWithPhone(String phone) {
        //1、创建新用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(6));
        //2、保存
        save(user);
        return user;
    }
}
