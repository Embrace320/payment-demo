package com.anthony.controller;

import com.anthony.config.WxPayConfig;
import com.anthony.valueobject.R;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author Anthony_CMH
 * @create 2023-05-18 11:21
 */
@CrossOrigin
@Api(tags = "测试控制器")
@RestController
@RequestMapping("/api/test")
public class TestController {
    @Resource
    private WxPayConfig wxPayConfig;
    @GetMapping("/get-wxpay-config")
    public R getWxConfig(){
        String macId = wxPayConfig.getMchId();
        return R.ok().data("macTd",macId);
    }
}
