package com.anthony.controller;

import com.anthony.entity.OrderInfo;
import com.anthony.enums.OrderStatus;
import com.anthony.service.OrderInfoService;
import com.anthony.valueobject.R;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Anthony_CMH
 * @create 2023-05-22 19:22
 */
@CrossOrigin//跨域注解
@RestController
@RequestMapping("/api/order-info")
@Api(tags = "商品订单管理")
@Slf4j //lombok提供的日志打印
public class OrderInfoController {
    @Resource
    private OrderInfoService orderInfoService;

    @GetMapping("/list")
    public R list(){
        List<OrderInfo> list = orderInfoService.listOrderByCreateTimeDesc();
        return R.ok().data("list",list);
    }

    /**
     * 查询本地订单状态
     * @param orderNo
     * @return
     */
    @GetMapping("/query-order-status/{orderNo}")
    public R queryOrderStatus(@PathVariable String orderNo){
        String orderStatus = orderInfoService.getOrderStatus(orderNo);
        if(OrderStatus.SUCCESS.getType().equals(orderStatus)){
            R r =  R.ok();
            r.setMessage("支付成功");
            return r;
        }
        R r =  R.ok();
        r.setCode(101);
        r.setMessage("支付中...");
        return r;
    }
}
