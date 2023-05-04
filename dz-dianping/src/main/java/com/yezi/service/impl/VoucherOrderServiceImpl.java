package com.yezi.service.impl;

import com.yezi.dto.Result;
import com.yezi.entity.SeckillVoucher;
import com.yezi.entity.VoucherOrder;
import com.yezi.mapper.VoucherOrderMapper;
import com.yezi.service.ISeckillVoucherService;
import com.yezi.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yezi.utils.RedisIdWorker;
import com.yezi.utils.UserHolder;
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
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

//    @Resource
//    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null) {
            return Result.fail("该优惠券已消失");
        }

        if(voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("该活动还未开始");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("该活动已结束");
        }

        if(voucher.getStock() <= 0) {
            return Result.fail("已售空");
        }
        //库存减一
        //mysql单条update具有原子性
        boolean flag = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)//cas
                .update();
        if(!flag) {
            return Result.fail("已售空");
        }

        //新建优惠券订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("voucher_order");//ID生成器生成全局唯一订单ID
        voucherOrder.setId(orderId);
        Long id = UserHolder.getUser().getId();//用户ID
        voucherOrder.setUserId(id);
        voucherOrder.setVoucherId(voucherId);//优惠券ID

        this.save(voucherOrder);
        return Result.ok(orderId);
    }
}
