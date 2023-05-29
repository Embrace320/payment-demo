package com.anthony.controller;

import com.anthony.service.WxPayService;
import com.anthony.util.HttpUtils;
import com.anthony.util.WechatPay2ValidatorForRequest;
import com.anthony.valueobject.R;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Anthony_CMH
 * @create 2023-05-19 10:59
 */
@CrossOrigin//跨域注解
@RestController
@RequestMapping("/api/wx-pay")
@Api(tags = "网站微信支付API")
@Slf4j //lombok提供的日志打印
public class WxPayController {
    @Resource
    private WxPayService wxPayService;

    @Resource
    private Verifier verifier;

    @ApiOperation("调用统一下单API，生成支付二维码")
    @PostMapping("native/{productId}")
    public R nativePay(@PathVariable Long productId) throws Exception {

        log.info("发起支付请求");

        //返回支付订单号和对应的支付二维码链接
        Map<String, Object> map = wxPayService.nativePay(productId);

        R r = new R();
        r.setData(map);
        return r;
    }


    @PostMapping("/native/notify")
    public String nativeNotify(HttpServletRequest request, HttpServletResponse response){
        Gson gson = new Gson();

        //应答对象
        Map<String, String> map = new HashMap<>();

        //处理通知参数
        try {
            String body = HttpUtils.readData(request);
            Map<String,Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String) bodyMap.get("id");
            log.info("支付通知的id====> {}", requestId);
            log.info("支付通知的完整信息====> {}", body);

            //签名的验证（是对微信的request进行验证）
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, body, requestId);
            if(!wechatPay2ValidatorForRequest.validate(request)){

                log.info("请求通知验签失败");
                response.setStatus(500);
                map.put("code","ERROR");
                map.put("message","失败");
                return gson.toJson(map);
            }
            //验签成功，继续执行内容
            log.info("请求通知验签成功");

            //对订单进行处理
            wxPayService.processOrder(bodyMap);


            //应答超时的情况下，微信会不断发送请求。
            //TimeUtil类底层还是Thread类得sleep方法，让线程睡眠。
//            TimeUnit.SECONDS.sleep(5);

            //成功的应答（应答给微信）
            response.setStatus(200);
            map.put("code","SUCUSS");
            map.put("message","成功");
            return gson.toJson(map);
        } catch (Exception e) {
            //失败的应答（应答给微信）
            response.setStatus(500);
            map.put("code","ERROR");
            map.put("message","失败");
            return gson.toJson(map);
        }
    }

    /**
     * 用户手动取消订单
     * @param orderNo
     * @return
     */
    @PostMapping("/cancel/{orderNo}")
    public R cancel(@PathVariable String orderNo) throws Exception {
        log.info("取消订单");
        wxPayService.cancelOrder(orderNo);

        R r = R.ok();
        r.setMessage("订单已取消");
        return r;
    }

    /**
     * 当商户客户端迟迟没有收到微信发送的支付结果通知时
     * 客户端会自动向微信发送请求查询订单情况
     * @param orderNo
     * @return
     */
    @GetMapping("/query/{orderNo}")
    public R queryOrder(@PathVariable String orderNo) throws Exception {
        log.info("查询订单的支付情况（微信端）");
        String result = wxPayService.queryOrder(orderNo);
        return R.ok().setMessage("查询成功").data("result",result);
    }


    /**
     * 申请退款
     */
    @PostMapping("/refunds/{orderNo}/{reason}")
    public R refunds(@PathVariable String orderNo,@PathVariable String reason) throws Exception {
        log.info("申请退款");
        wxPayService.refund(orderNo,reason);
        return R.ok();
    }

    @PostMapping("/refunds/notify")
    public String refundsNotify(HttpServletRequest request, HttpServletResponse response){
        log.info("退款通知执行");
        Gson gson = new Gson();

        //应答对象
        Map<String, String> map = new HashMap<>();

        //处理通知参数
        try {
            String body = HttpUtils.readData(request);
            Map<String,Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String) bodyMap.get("id");
            log.info("退款通知的id====> {}", requestId);
//            log.info("退款通知的完整信息====> {}", body);

            //签名的验证（是对微信的request进行验证）
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, body, requestId);
            if(!wechatPay2ValidatorForRequest.validate(request)){

                log.info("请求通知验签失败");
                response.setStatus(500);
                map.put("code","ERROR");
                map.put("message","失败");
                return gson.toJson(map);
            }
            //验签成功，继续执行内容
            log.info("请求通知验签成功");

            //对订单进行处理
            wxPayService.processRefund(bodyMap);


            //应答超时的情况下，微信会不断发送请求。
            //TimeUtil类底层还是Thread类得sleep方法，让线程睡眠。
//            TimeUnit.SECONDS.sleep(5);

            //成功的应答（应答给微信）
            response.setStatus(200);
            map.put("code","SUCUSS");
            map.put("message","成功");
            return gson.toJson(map);
        } catch (Exception e) {
            //失败的应答（应答给微信）
            response.setStatus(500);
            map.put("code","ERROR");
            map.put("message","失败");
            return gson.toJson(map);
        }
    }


    /**
     * 查询退款
     * @param refundNo
     * @return
     * @throws Exception
     */
    @ApiOperation("查询退款：测试用")
    @GetMapping("/query-refund/{refundNo}")
    public R queryRefund(@PathVariable String refundNo) throws Exception {
        log.info("查询退款");
        String result = wxPayService.queryRefund(refundNo);
        return R.ok().setMessage("查询成功").data("result", result);
    }

    @ApiOperation("获取账单下载url，测试用")
    @GetMapping("querybill/{billDate}/{type}")
    public R queryTradeBill(@PathVariable String billDate,@PathVariable String type) throws Exception {
        log.info("获取账单url");
        String downloadUrl = wxPayService.queryBill(billDate, type);
        return R.ok().setMessage("获取账单url成功").data("downloadUrl", downloadUrl);
    }

    @ApiOperation("下载账单")
    @GetMapping("/downloadbill/{billDate}/{type}")
    public R downloadBill(
            @PathVariable String billDate,
            @PathVariable String type) throws Exception {
        log.info("下载账单");
        String result = wxPayService.downloadBill(billDate, type);
        return R.ok().data("result", result);
    }

}
