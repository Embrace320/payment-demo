package com.anthony.service.impl;

import com.anthony.config.WxPayConfig;
import com.anthony.entity.OrderInfo;
import com.anthony.entity.RefundInfo;
import com.anthony.enums.OrderStatus;
import com.anthony.enums.wxpay.WxApiType;
import com.anthony.enums.wxpay.WxNotifyType;
import com.anthony.enums.wxpay.WxRefundStatus;
import com.anthony.enums.wxpay.WxTradeState;
import com.anthony.mapper.OrderInfoMapper;
import com.anthony.service.OrderInfoService;
import com.anthony.service.PaymentInfoService;
import com.anthony.service.RefundInfoService;
import com.anthony.service.WxPayService;
import com.anthony.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.gson.Gson;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Anthony_CMH
 * @create 2023-05-19 11:02
 */
@Service
@Slf4j
public class WxpayServiceImpl implements WxPayService {
    @Resource
    private WxPayConfig wxPayConfig;
    @Resource
    private CloseableHttpClient wxPayClient;

    @Resource
    private CloseableHttpClient wxPayNoSignClient;

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private PaymentInfoService paymentInfoService;

    @Resource
    private OrderInfoMapper orderInfoMapper;

    @Resource
    private RefundInfoService refundInfoService;

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 创建订单，调用Native支付接口
     * 返回 订单号和对应的code_url（二维码）
     */
    @Override
    public Map<String, Object> nativePay (Long productId) throws Exception{

        log.info("生成订单");
        //生成订单并将其放如数据库
        OrderInfo orderInfo = orderInfoService.createOrderByProducId(productId);
        String codeUrl = orderInfo.getCodeUrl();
        if(!StringUtils.isEmpty(codeUrl)){
            log.info("二维码订单已存在，二维码已保存");
            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
            return map;
        }


        //调用统一下单API
        log.info("调用统一下单API");
        //地址为：https://api.mch.weixin.qq.com/v3/pay/transactions/native
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.NATIVE_PAY.getType()));

        //构建请求body参数
        Gson gson = new Gson();//转换json格式用
        Map paramsMap = new HashMap();
        paramsMap.put("appid",wxPayConfig.getAppid());
        paramsMap.put("mchid",wxPayConfig.getMchId());
        paramsMap.put("description",orderInfo.getTitle());
        paramsMap.put("out_trade_no",orderInfo.getOrderNo());
        paramsMap.put("notify_url",wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));

        Map amountMap = new HashMap();
        amountMap.put("total",orderInfo.getTotalFee());
        amountMap.put("currency","CNY");

        paramsMap.put("amount",amountMap);

        //将参数转换成json字符串
        String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数" + jsonParams);

        //将请求参数设置到请求对象中，并对请求体类型进行设置
        StringEntity entity = new StringEntity(jsonParams,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");

        //将请求发送出去，完成签名并获得响应，并对响应进行验签
        CloseableHttpResponse response =  wxPayClient.execute(httpPost);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());//响应体
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功, 返回结果 = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功");
            } else {
                log.info("Native下单失败,响应码 = " + statusCode+ ",返回结果 = " +
                        bodyAsString);
                throw new IOException("request failed");
            }
            //响应结果
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            //二维码
            codeUrl = resultMap.get("code_url");

            //保存二维码
            String orderNo = orderInfo.getOrderNo();
            orderInfoService.saveCodeUrl(orderNo,codeUrl);

            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
            return map;
        } finally {
            response.close();
        }
    }


    /**
     * 处理微信回调请求中的订单信息
     * @param bodyMap
     */
    @Override
    public void processOrder(Map<String, Object> bodyMap) throws Exception {
        log.info("处理订单");
        //解密明文
        String plainText = decryptFromResource(bodyMap);

        //将明文转化为Map
        Gson gson = new Gson();
        Map plainTextMap = gson.fromJson(plainText,HashMap.class);
        String orderNo = (String)plainTextMap.get("out_trade_no");

        /**
         * 在对业务数据进行状态检查和处理之前，
         * 要采用数据锁进行并发控制，以避免函数重入造成的数据混乱
         */
        //尝试获取锁：
        // 成功获取则立即返回true，获取失败则立即返回false。不必一直等待锁的释放
        if(lock.tryLock()){
            try{
                //处理重复通知
                //接口调用的幂等性，无论接口被调用多少次，产生的结果是一致的。
                String orederStatus =  orderInfoService.getOrderStatus(orderNo);
                if (!OrderStatus.NOTPAY.getType().equals(orederStatus)){
                    return;
                }
                //更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.SUCCESS);

                //记录日志
                paymentInfoService.createPaymentInfo(plainText);
            }finally {
                lock.unlock();
            }
        }
    }

    /**
     * 用户手动取消订单
     * @param orderNo
     */
    @Override
    public void cancelOrder(String orderNo) throws Exception {
        //调用微信支付的关单窗口
        this.closeOrder(orderNo);

        //更新商户订单状态：设置为取消
        orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.CANCEL);
    }

    /**
     * 客户端向微信手动查询订单
     * @param orderNo
     * @return
     */
    @Override
    public String queryOrder(String orderNo) throws Exception {
        log.info("查询窗口调用====>{}",orderNo);
        //创建请求地址
        String url = String.format(WxApiType.ORDER_QUERY_BY_NO.getType(),orderNo);
        url = wxPayConfig.getDomain().concat(url).concat("?mchid=").concat(wxPayConfig.getMchId());

        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept","application/json");

        //将请求发送出去，完成签名并获得响应，并对响应进行验签
        CloseableHttpResponse response =  wxPayClient.execute(httpGet);
        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());//响应体
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功, 返回结果 = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功");
            } else {
                log.info("Native下单失败,响应码 = " + statusCode+ ",返回结果 = " +
                        bodyAsString);
                throw new IOException("request failed");
            }
            return bodyAsString;
        } finally {
            response.close();
        }
    }

    /**
     * 调用微信查单窗口，查询订单真实支付情况
     * 如果订单已支付，则更新商户端订单状态
     * 如果订单未支付，则调用关单接口关闭订单并更新商户端状态
     * @param orderNo
     */
    @Override
    public void checkOrderStatus(String orderNo) throws Exception {
        log.info("根据订单号核实订单状态====> {}",orderNo);
        String result = this.queryOrder(orderNo);

        Gson gson = new Gson();
        Map resultMap = gson.fromJson(result, HashMap.class);

        //获取支付状态
        String tradeState = (String)resultMap.get("trade_state");

        //判断状态
        if(WxTradeState.SUCCESS.getType().equals(tradeState)){
            log.info("核实订单已支付=====> {}",orderNo);

            //订单已支付，需要修改商户客户端订单状态
            orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.SUCCESS);

            //记录支付日志(查询返回的信息其实是和支付通知中密文解密出来的plainText格式一致)
            paymentInfoService.createPaymentInfo(result);
        }
        if(WxTradeState.NOTPAY.getType().equals(tradeState)){
            log.info("核实订单未支付=====> {}",orderNo);
            //订单未支付，调用关单接口
            this.closeOrder(orderNo);

            //更新本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.CLOSED);
        }
    }

    /**
     * 关单接口的调用
     * @param orderNo
     */
    private void closeOrder(String orderNo) throws Exception {
        log.info("关单接口的调用，订单号 ===> {}", orderNo);

        String url = wxPayConfig.getDomain().
                concat(String.format(WxApiType.CLOSE_ORDER_BY_NO.getType(),orderNo));

        HttpPost httpPost = new HttpPost(url);
        //组装请求体
        Gson gson = new Gson();
        Map<String, String> paramaterMap = new HashMap<>();
        paramaterMap.put("mchid",wxPayConfig.getMchId());

        String jsonParams = gson.toJson(paramaterMap);
        log.info("请求参数====>"+jsonParams);

        //将请求参数设置到请求对象中，并对请求体类型进行设置
        StringEntity entity = new StringEntity(jsonParams,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");

        //将请求发送出去，完成签名并获得响应，并对响应进行验签
        CloseableHttpResponse response =  wxPayClient.execute(httpPost);

        try {
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功200");
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功204");
            } else {
                log.info("Native下单失败,响应码 = " + statusCode);
                throw new IOException("request failed");
            }
        } finally {
            response.close();
        }
    }

    /**
     * 订单信息处理的辅助方法，解密resource
     * @param bodyMap
     * @return
     */
    private String decryptFromResource(Map<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("密文解密");

        //获取通知中的resource,密文存在这里
        Map<String, String> resourceMap = (Map) bodyMap.get("resource");

        //解密需要resource中的nonce以及associated_data以及密钥
        //被揭密的秘文是ciphertext
        String ciphertext = resourceMap.get("ciphertext");
        //nonce是随机串
        String nonce = resourceMap.get("nonce");
        //associated_data是附加字符串
        String associated_data = resourceMap.get("associated_data");

        log.info("密文====>"+ciphertext);

        //解密工具
        AesUtil aesUtil = new AesUtil(wxPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        String plainText = aesUtil.decryptToString(associated_data.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8),ciphertext);

        log.info("明文===>"+plainText);
        return plainText;
    }


    /**
     * 调用订单退款API
     * @param orderNo
     * @param reason
     * @throws Exception
     */
    @Override
    public void refund(String orderNo, String reason) throws Exception {
        log.info("创建退款单记录");
        //根据订单编号船舰退款单
        RefundInfo refundInfo = refundInfoService.createRefundByOrderNo(orderNo,reason);

        //调用退款API
        log.info("调用退款API");

        String url = wxPayConfig.getDomain().concat(WxApiType.DOMESTIC_REFUNDS.getType());
        HttpPost httpPost = new HttpPost(url);

        Gson gson = new Gson();
        Map paramsMap = new HashMap();
        paramsMap.put("out_trade_no",refundInfo.getOrderNo());
        paramsMap.put("out_refund_no",refundInfo.getRefundNo());
        paramsMap.put("reason",reason);
        paramsMap.put("notify_url",wxPayConfig.getNotifyDomain().concat(WxNotifyType.REFUND_NOTIFY.getType()));

        Map amountMap = new HashMap();
        amountMap.put("refund",refundInfo.getRefund());
        amountMap.put("total",refundInfo.getTotalFee());
        amountMap.put("currency","CNY");

        paramsMap.put("amount",amountMap);

        String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数====> {}",jsonParams);

        //将请求参数设置到请求对象中，并对请求体类型进行设置
        StringEntity entity = new StringEntity(jsonParams,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept","application/json");

        //将请求发送出去，完成签名并获得响应，并对响应进行验签
        CloseableHttpResponse response = wxPayClient.execute(httpPost);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());//响应体
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功, 退款返回结果 = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功");
            } else {
                throw new RuntimeException("退款异常, 响应码 = " + statusCode+ ", 退款返回结果 = " + bodyAsString);
            }

            //更新订单状态（是t_order_info记录的信息）
            orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.REFUND_PROCESSING);

            //更新退款单状态（是解析响应信息，并更新t_refund_info中的状态）
            refundInfoService.updataRefund(bodyAsString);

        } finally {
            response.close();
        }
    }

    /**
     * 手动查询订单退款情况
     * @param refundNo
     * @return
     */
    @Override
    public String queryRefund(String refundNo) throws Exception {
        log.info("查询退款接口调用====> {}",refundNo);
        //创建请求地址
        String url = String.format(WxApiType.DOMESTIC_REFUNDS_QUERY.getType(),refundNo);
        url = wxPayConfig.getDomain().concat(url);

        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept","application/json");

        //将请求发送出去，完成签名并获得响应，并对响应进行验签
        CloseableHttpResponse response =  wxPayClient.execute(httpGet);
        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());//响应体
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功, 查询退款返回结果 = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功");
            } else {
                throw new RuntimeException("查询退款异常, 响应码 = " + statusCode+ ", 查询退款返回结果 = " + bodyAsString);
            }
            return bodyAsString;
        } finally {
            response.close();
        }
    }

    /**
     * 处理退款通知
     * @param bodyMap
     */
    @Override
    public void processRefund(Map<String, Object> bodyMap) throws Exception {
        log.info("处理退款订单");
        //解密明文
        String plainText = decryptFromResource(bodyMap);

        //将明文转化为Map
        Gson gson = new Gson();
        Map plainTextMap = gson.fromJson(plainText,HashMap.class);
        String orderNo = (String)plainTextMap.get("out_trade_no");

        /**
         * 在对业务数据进行状态检查和处理之前，
         * 要采用数据锁进行并发控制，以避免函数重入造成的数据混乱
         */
        //尝试获取锁：
        // 成功获取则立即返回true，获取失败则立即返回false。不必一直等待锁的释放
        if(lock.tryLock()){
            try{
                //处理重复通知
                //接口调用的幂等性，无论接口被调用多少次，产生的结果是一致的。
                String orederStatus =  orderInfoService.getOrderStatus(orderNo);
                if (!OrderStatus.REFUND_PROCESSING.getType().equals(orederStatus)){
                    return;
                }
                //更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.REFUND_SUCCESS);

                //更新退款单
                refundInfoService.updataRefund(plainText);
            }finally {
                lock.unlock();
            }
        }
    }

    /**
     * 根据退款单号核实退款单状态
     * @param refundNo
     */
    @Override
    public void checkRefundStatus(String refundNo) throws Exception {
        log.info("根据退款订单号核实订单状态====> {}",refundNo);
        String result = this.queryRefund(refundNo);

        Gson gson = new Gson();
        Map resultMap = gson.fromJson(result, HashMap.class);

        //获取支付状态
        String status = (String)resultMap.get("status");
        String orderNo = (String)resultMap.get("out_trade_no");

        //判断状态
        if(WxRefundStatus.SUCCESS.getType().equals(status)){
            log.info("核实该退款订单已退款=====> {}",refundNo);

            //订单已退款，需要修改商户客户端订单状态
            orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.REFUND_SUCCESS);

            ////更新退款单
            refundInfoService.updataRefund(result);
        }

        if(WxRefundStatus.ABNORMAL.getType().equals(status)){
            log.info("核实退款订单异常=====> {}",refundNo);

            //退款订单异常，更新本地退款状态
            orderInfoService.updateStatusByOrderNo(orderNo,OrderStatus.REFUND_ABNORMAL);

            //更新退款单状态（t_refund_order）
            refundInfoService.updataRefund(result);
        }
    }

    /**
     * 申请账单，获取账单下载url
     * @param billDate
     * @param type
     * @return
     * @throws Exception
     */
    @Override
    public String queryBill(String billDate, String type) throws Exception {
        log.info("申请账单接口调用 {}", billDate);

        String url = "";
        if("tradebill".equals(type)){
            url = WxApiType.TRADE_BILLS.getType();
        }else if("fundflowbill".equals(type)){
            url = WxApiType.FUND_FLOW_BILLS.getType();
        }else{
            throw new RuntimeException("不支持的账单类型");
        }
        url = wxPayConfig.getDomain().concat(url+"?bill_date="+billDate);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Accept", "application/json");
        //使用wxPayClient发送请求得到响应
        CloseableHttpResponse response = wxPayClient.execute(httpGet);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());//响应体
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功, 申请账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功");
            } else {
                throw new RuntimeException("申请账单失败,响应码 = " + statusCode+ ",返回结果 = " +
                        bodyAsString);
            }
            //响应结果
            Gson gson = new Gson();
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);

            return resultMap.get("download_url");
        } finally {
            response.close();
        }
    }

    @Override
    public String downloadBill(String billDate, String type) throws Exception {
        log.info("下载账单接口调用 {}, {}", billDate, type);
        //获取账单url地址
        String downloadUrl = this.queryBill(billDate, type);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(downloadUrl);
        httpGet.addHeader("Accept", "application/json");
        //使用wxPayNoSignClient发送请求得到响应
        // wxPayNoSignClient无需签名验证
        CloseableHttpResponse response = wxPayNoSignClient.execute(httpGet);
        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 下载账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("下载账单异常, 响应码 = " + statusCode+ ",下载账单返回结果 = " + bodyAsString);
            }
            return bodyAsString;
        } finally {
            response.close();
        }
    }
}
