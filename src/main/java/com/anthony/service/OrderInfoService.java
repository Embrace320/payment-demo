package com.anthony.service;

import com.anthony.entity.OrderInfo;
import com.anthony.entity.RefundInfo;
import com.anthony.enums.OrderStatus;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

//定义这些接口的目的在于，实现类除了继承mybatis-plus类中定义好的方法，还可以自定义方法
public interface OrderInfoService extends IService<OrderInfo> {
    OrderInfo createOrderByProducId(Long productId);


    void saveCodeUrl(String orderNo, String codeUrl);

    List<OrderInfo> listOrderByCreateTimeDesc();

    void updateStatusByOrderNo(String orderNo, OrderStatus orderStatus);

    String getOrderStatus(String orderNo);

    List<OrderInfo> getNoPayOrderByDuration(int minutes);

    OrderInfo getOrderByOrderNo(String orderNo);

    List<RefundInfo> getNoRefundOrderByDuration(int minutes);
}
