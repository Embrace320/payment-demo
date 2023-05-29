package com.anthony.service.impl;

import com.anthony.entity.OrderInfo;
import com.anthony.entity.Product;
import com.anthony.entity.RefundInfo;
import com.anthony.enums.OrderStatus;
import com.anthony.enums.wxpay.WxRefundStatus;
import com.anthony.mapper.OrderInfoMapper;
import com.anthony.mapper.ProductMapper;
import com.anthony.mapper.RefundInfoMapper;
import com.anthony.service.OrderInfoService;
import com.anthony.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
@Slf4j
@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private ProductMapper productMapper;

    @Resource
    private OrderInfoMapper orderInfoMapper;

    @Resource
    private RefundInfoMapper refundInfoMapper;

    @Override
    public OrderInfo createOrderByProducId(Long productId) {
        //查找已存在但未支付的订单
        OrderInfo orderInfo = this.getNoPayOrderByProductId(productId);

        if(orderInfo != null){
            return orderInfo;
        }

        //搜索商品编号，获取商品信息
        Product product = productMapper.selectById(productId);

        //生成订单
        orderInfo = new OrderInfo();
        orderInfo.setTitle(product.getTitle());
        orderInfo.setOrderNo(OrderNoUtils.getOrderNo());
        orderInfo.setProductId(productId);
        orderInfo.setTotalFee(product.getPrice());//单位为分
        orderInfo.setOrderStatus(OrderStatus.NOTPAY.getType());
        //存入数据库
        orderInfoMapper.insert(orderInfo);

        return orderInfo;
    }

    /**
     * 存储订单二维码
     * @param orderNo
     * @param codeUrl
     */
    @Override
    public void saveCodeUrl(String orderNo, String codeUrl) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no", orderNo);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCodeUrl(codeUrl);
        orderInfoMapper.update(orderInfo,queryWrapper);
    }

    /**
     * 倒序查询订单列表
     * @return
     */
    @Override
    public List<OrderInfo> listOrderByCreateTimeDesc() {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        return orderInfoMapper.selectList(queryWrapper);
    }

    /**
     * 根据订单编号更新订单状态
     * @param orderNo
     * @param orderStatus
     */
    @Override
    public void updateStatusByOrderNo(String orderNo, OrderStatus orderStatus) {
        log.info("更新订单状态====> {}",orderStatus.getType());

        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no",orderNo);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderStatus(orderStatus.getType());

        orderInfoMapper.update(orderInfo,queryWrapper);
    }

    @Override
    public String getOrderStatus(String orderNo) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no",orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        if(orderInfo == null){
            return null;
        }
        return orderInfo.getOrderStatus();
    }

    /**
     * 按照时间间隔查询未支付订单
     * @param minutes
     * @return
     */
    @Override
    public List<OrderInfo> getNoPayOrderByDuration(int minutes) {
        //创建一个当前时间实例，并减去五分钟，如果订单创建时间在这个时间之前，则被选中
        Instant instant = Instant.now().minus(Duration.ofMinutes(minutes));

        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        //首先得未支付
        queryWrapper.eq("order_status",OrderStatus.NOTPAY.getType());
        queryWrapper.le("create_time",instant);

        List<OrderInfo> orderInfoList = orderInfoMapper.selectList(queryWrapper);

        return orderInfoList;
    }

    /**
     * 根据订单号获取订单信息
     * @param orderNo
     * @return
     */
    @Override
    public OrderInfo getOrderByOrderNo(String orderNo) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no",orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);

        return orderInfo;
    }

    @Override
    public List<RefundInfo> getNoRefundOrderByDuration(int minutes) {
        //创建一个当前时间实例，并减去五分钟，如果订单创建时间在这个时间之前，则被选中
        Instant instant = Instant.now().minus(Duration.ofMinutes(minutes));

        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        //首先得未支付
        queryWrapper.eq("refund_status", WxRefundStatus.PROCESSING.getType());
        queryWrapper.le("create_time",instant);

        List<RefundInfo> refundInfoList = refundInfoMapper.selectList(queryWrapper);

        return refundInfoList;
    }


    /**
     * 根据商品id查询未支付订单
     * 防止重复创建订单对象
     * @param productId
     * @return
     */
    private OrderInfo getNoPayOrderByProductId(Long productId){
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("product_id",productId);
        queryWrapper.eq("order_status",OrderStatus.NOTPAY.getType());
        //
        //        queryWrapper.eq("user_id",userId);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        return orderInfo;
    }
}
