package com.anthony.service.impl;

import com.anthony.entity.PaymentInfo;
import com.anthony.enums.PayType;
import com.anthony.mapper.PaymentInfoMapper;
import com.anthony.service.PaymentInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {

    @Resource
    private PaymentInfoMapper paymentInfoMapper;
    @Override
    public void createPaymentInfo(String plainText) {
        log.info("记录支付日志");

        Gson gson = new Gson();
        Map<String,Object> plainTextMap = gson.fromJson(plainText, HashMap.class);
        String orderNo = (String)plainTextMap.get("out_trade_no");
        String transactionId = (String)plainTextMap.get("transaction_id");
        String tradeType = (String)plainTextMap.get("trade_type");
        String tradeState = (String)plainTextMap.get("trade_state");

        Map<String, Object> amount = (Map)plainTextMap.get("amount");
        Integer payerTotal = ((Double)amount.get("total")).intValue();
//        Integer payerTotal = (int)amount.get("total");


        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderNo(orderNo);
        paymentInfo.setTransactionId(transactionId);
        paymentInfo.setPaymentType(PayType.WXPAY.getType());
        paymentInfo.setTradeType(tradeType);
        paymentInfo.setTradeState(tradeState);
        paymentInfo.setPayerTotal(payerTotal);
        paymentInfo.setContent(plainText);

        paymentInfoMapper.insert(paymentInfo);
    }
}
