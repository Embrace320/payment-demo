package com.anthony;

import com.anthony.config.WxPayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.PrivateKey;

@SpringBootTest
class PaymentDemoApplicationTests {
    @Autowired
    private WxPayConfig wxPayConfig;

    // 测试获取账户私钥
    @Test
    void contextLoads() {
        String path = wxPayConfig.getPrivateKeyPath();
        PrivateKey privateKey= wxPayConfig.getPrivateKey(path);
        System.out.println(privateKey);
    }

}
