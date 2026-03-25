package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.val;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVocher(Long voucherId) {
        //1、查询优惠卷
        SeckillVoucher vocher = iSeckillVoucherService.getById(voucherId);
        //2、判断秒杀是否开启
        if (vocher.getBeginTime().isAfter(LocalDateTime.now()) ||
                vocher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("未在规定范围内");
        }
        //3、判断库存是否充足
        if (vocher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()){//！！--自己上锁
//            //return creatVoucherOrder(voucherId);
//            // 此时调用的不是代理对象的creatVoucherOrder
//            // 方法，事务会失效，因此需要获取到代理对象，再调用creatVoucherOrder这个方法，才能事务，
//            // 这里是一个事务失效的场景
//
//            //解决
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.creatVoucherOrder(voucherId);
//        }
            //return creatVoucherOrder(voucherId);
            // 此时调用的不是代理对象的creatVoucherOrder
            // 方法，事务会失效，因此需要获取到代理对象，再调用creatVoucherOrder这个方法，才能事务，
            // 这里是一个事务失效的场景

            //解决
            //获取代理对象（事务）
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断锁是否获取成功
        if (!isLock) {
            //锁获取失败
            return Result.fail("不允许重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }

    }
    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //5.一人一单
        //5.1查询订单

        //5.3、用户id
        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        //是否存在
        if (count > 0) {
            //用户已经购买过了
            return Result.fail("该用户已经购买过一次！");
        }
        //4、扣减库存
        boolean flag = iSeckillVoucherService.update()
                .setSql("stock = stock -1") // set stock = stock -1
                .eq("voucher_id", voucherId).gt("stock",0) //where voucher_id ,stock
                .update();

        if(!flag){
            return Result.fail("更新失败");
        }

        //5、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        voucherOrder.setUserId(userId);
        //5.3代金卷id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);
        //6、返回订单id
        return Result.ok(orderId);
    }
}
