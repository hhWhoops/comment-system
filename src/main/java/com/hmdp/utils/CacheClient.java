package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate  stringRedisTemplate;


    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time,  TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //设空值防止穿透
    public <R,ID> R queryWithPassthrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time,  TimeUnit unit){
        String key = keyPrefix + id;
        //1、从redis中查信息
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2、若存在 直接返回店铺信息。
        // StrUtil.isNotBlank(str)这个函数只有在str有值是返回ture
        if (StrUtil.isNotBlank(Json)) {
            return JSONUtil.toBean(Json, type);
        }
        //判断命中的是否是空值
        if (Json != null) {
            return null;
        }
        //3、若不存在 根据id去数据库中查询
        R r = dbFallback.apply(id);
        //4、若数据库中不存在，直接返回错误
        if (r == null) {
            //防止穿透！！！
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5、若存在写入redis，返回商铺信息
        this.set(key, r, time, unit);
        return r;
    }




    private static final ExecutorService CACHE_REBUID_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑过期防击穿
    public <R,ID> R queryWithLogicExpire(
            String KeyPrefix, ID id,Class<R> type, Function<ID,R> dbFallback, Long time,  TimeUnit unit){
        String key = KeyPrefix + id;
        //1、从redis中查信息
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2、若存在 直接返回店铺信息。
        // StrUtil.isNotBlank(str)这个函数只有在str有值是返回ture
        if (StrUtil.isBlank(Json)) {
            return null;
        }
        //3、命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //4、判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //4.1未过期，直接返回店铺信息
            return r;
        }
        //4.2已过期，需要缓存重建
        //5、缓存重建
        //5.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            //5.2判断是否获取锁成功
            //5.3成功，开启独立线程，实现缓存重建
            CACHE_REBUID_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    //先查数据库
                    R r1 = dbFallback.apply(id);


                    //写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });

        }
        //5.4返回商铺信息
        return r;

    }



    //上锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //解锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }



}
