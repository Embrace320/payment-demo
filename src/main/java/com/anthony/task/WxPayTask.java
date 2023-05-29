package com.anthony.task;

import com.anthony.config.WxPayConfig;
import com.anthony.entity.OrderInfo;
import com.anthony.entity.RefundInfo;
import com.anthony.service.OrderInfoService;
import com.anthony.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Anthony_CMH
 * @create 2023-05-26 10:04
 */
@Component
@Slf4j
public class WxPayTask {

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private WxPayService wxPayService;

    /**
     * 测试
     * (cron="秒 分 时 日 月 周")
     * *：每隔一秒执行
     * 0/3：从第0秒开始，每隔3秒执行一次
     * 1-3: 从第1秒开始执行，到第3秒结束执行
     * 1,2,3：第1、2、3秒执行
     * ?：不指定，若指定日期，则不指定周，反之同理
     */
//    @Scheduled(cron = "0/3 * * * * ?")
//    public void taskt1(){
//        log.info("task1 被执行");
//    }

    /**
     * 从第0秒开始每隔30秒执行1次，查询创建超过5分钟，并且未支付的订单
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void orderConfirm() throws Exception {
        log.info("orderConfirm 被执行");

        List<OrderInfo> orderInfoList = orderInfoService.getNoPayOrderByDuration(1);

        for(OrderInfo orderInfo:orderInfoList){
            String orderNo = orderInfo.getOrderNo();
            log.warn("超时订单====> {}",orderNo);

            //调用微信查单窗口，查询真实的支付状态（有可能存在已支付，但是客户端未收到支付通知）
            wxPayService.checkOrderStatus(orderNo);


        }
    }

    /**
     * 从第0秒开始每隔30秒执行1次，查询创建超过5分钟，并且未支付的订单
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void refundConfirm() throws Exception {
        log.info("refundConfirm 被执行");

        List<RefundInfo> refundInfoList = orderInfoService.getNoRefundOrderByDuration(1);

        for(RefundInfo refundInfo:refundInfoList){
            String refundNo = refundInfo.getRefundNo();
            log.warn("超时未退款的退款单号====> {}",refundNo);

            //调用微信查单窗口，查询真实的支付状态（有可能存在已支付，但是客户端未收到支付通知）
            wxPayService.checkRefundStatus(refundNo);


        }
    }
}
