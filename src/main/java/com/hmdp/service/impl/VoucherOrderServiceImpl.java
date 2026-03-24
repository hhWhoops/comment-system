package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
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

    @Transactional
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
        //4、扣减库存
        boolean flag = iSeckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId).update();

        if(!flag){
            return Result.fail("更新失败");
        }
        //5、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1、订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //5.2、用户id
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //5.3代金卷id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);
        //6、返回订单id
        return Result.ok(orderId);
    }
}
