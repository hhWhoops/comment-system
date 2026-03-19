package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;
import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
//        //缓存穿透
//        Shop shop = queryWithPassthrough(id);

        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //返回
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id){
        //1、从redis中查信息
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2、若存在 直接返回店铺信息。
        // StrUtil.isNotBlank(str)这个函数只有在str有值是返回ture
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值
        if (shopJson != null) {
            return null;
        }

        //3、实现缓存重建
        //3.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY +id;
        Shop shopDetail = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2判断是否成功
            if(!isLock) {
                //4.3失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4成功，根据id查询数据库
            shopDetail = getById(id);
            //模拟重建时的延迟
            Thread.sleep(200);
            String shopDetailJson = JSONUtil.toJsonStr(shopDetail);
            //4、若数据库中不存在，直接返回错误
            if (shopDetail == null) {
                //防止穿透！！！
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //5、若存在写入redis，返回商铺信息
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id , shopDetailJson,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //6、释放互斥锁
            unLock(lockKey);
        }


        return shopDetail;

    }


    public Shop queryWithPassthrough(Long id){
        //1、从redis中查信息
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2、若存在 直接返回店铺信息。
        // StrUtil.isNotBlank(str)这个函数只有在str有值是返回ture
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值
        if (shopJson != null) {
            return null;
        }
        //3、若不存在 根据id去数据库中查询
        Shop shopDetail = getById(id);
        String shopDetailJson = JSONUtil.toJsonStr(shopDetail);
        //4、若数据库中不存在，直接返回错误
        if (shopDetail == null) {
            //防止穿透！！！
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //5、若存在写入redis，返回商铺信息
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id , shopDetailJson,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return shopDetail;

    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }



    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {

        if (StringUtils.isEmpty(shop.getId())) {
            return Result.fail("店铺id不为空");
        }
        //1更新数据库
        updateById(shop);
        //2删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}




























