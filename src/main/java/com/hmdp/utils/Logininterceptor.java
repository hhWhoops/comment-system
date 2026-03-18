package com.hmdp.utils;


import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
public class Logininterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("拦截到{}",request.getRequestURI());
        // 1、获取session
        HttpSession session = request.getSession();
        //2、获取session中获取到用户信息
        Object user = session.getAttribute("user");
        //3、判断用户是否存在
        if (user == null) {
            //4、不存在，拦截
            response.setStatus(401);
            return false;
        }
        //5存在保存用户信息到ThreadLocal
        UserHolder.saveUser((User)user);
        //6、放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
