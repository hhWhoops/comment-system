package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Slf4j
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

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //@PostConstruct当前对象初始化完毕就会立即执行
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while(true) {
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {

        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断锁是否获取成功
        if (!isLock) {
            //锁获取失败
            return;
        }
        try {
            proxy.creatVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }

    }


//    @Override
//    public Result seckillVocher(Long voucherId) {
//        //1、查询优惠卷
//        SeckillVoucher vocher = iSeckillVoucherService.getById(voucherId);
//        //2、判断秒杀是否开启
//        if (vocher.getBeginTime().isAfter(LocalDateTime.now()) ||
//                vocher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("未在规定范围内");
//        }
//        //3、判断库存是否充足
//        if (vocher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()){//！！--自己上锁
////            //return creatVoucherOrder(voucherId);
////            // 此时调用的不是代理对象的creatVoucherOrder
////            // 方法，事务会失效，因此需要获取到代理对象，再调用creatVoucherOrder这个方法，才能事务，
////            // 这里是一个事务失效的场景
////
////            //解决
////            //获取代理对象（事务）
////            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
////            return proxy.creatVoucherOrder(voucherId);
////        }
//            //return creatVoucherOrder(voucherId);
//            // 此时调用的不是代理对象的creatVoucherOrder
//            // 方法，事务会失效，因此需要获取到代理对象，再调用creatVoucherOrder这个方法，才能事务，
//            // 这里是一个事务失效的场景
//
//            //解决
//            //获取代理对象（事务）
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean isLock = lock.tryLock();
//        //判断锁是否获取成功
//        if (!isLock) {
//            //锁获取失败
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.creatVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//    }


        //成员变量 proxy 父进程
private IVoucherOrderService proxy;
@Override
public Result seckillVocher(Long voucherId) {
    //获取用户id
    Long userId = UserHolder.getUser().getId();
    //1.执行lua脚本
    Long result = stringRedisTemplate.execute(
            SECKILL_SCRIPT,
            Collections.emptyList(),
            voucherId.toString(),userId.toString()
    );
    int r = result.intValue();
    //2.判断是否为0
    if (r == 0) {
        //2.1不为0，代表没有购买资格，
        return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
    }
    //2.2为0，有购买资格，把下单信息保存到阻塞队列中
    //TODO 保存阻塞队列
    VoucherOrder voucherOrder = new VoucherOrder();

    //2.3订单id
    Long orderId = redisIdWorker.nextId("order");
    voucherOrder.setId(orderId);

    //2.4用户id
    voucherOrder.setUserId(userId);

    //2.5代金卷id
    voucherOrder.setVoucherId(voucherId);

    //2.6放入阻塞队列
    orderTasks.add(voucherOrder);

    //3.获取代理对象
    proxy = (IVoucherOrderService)AopContext.currentProxy();

    //4.返回订单id
    return Result.ok(orderId);
}



    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单
        //5.1查询订单

        //5.3、用户id
        Long userId = voucherOrder.getUserId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();

        //是否存在
        if (count > 0) {
            //用户已经购买过了
            log.error("用户已经购买过一次");
            return;
        }
        //4、扣减库存
        boolean flag = iSeckillVoucherService.update()
                .setSql("stock = stock -1") // set stock = stock -1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock",0) //where voucher_id ,stock
                .update();

        if(!flag){
            log.error("更新失败");
            return;
        }

        save(voucherOrder);
    }
}
