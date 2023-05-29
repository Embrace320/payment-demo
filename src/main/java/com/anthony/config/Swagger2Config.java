package com.anthony.config;


import org.springframework.context.annotation.*;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author Anthony_CMH
 * @create 2023-05-16 10:26
 */
@Configuration
@EnableSwagger2 //代表是一个Swagger的配置文件
public class Swagger2Config {
    @Bean
    public Docket docket(){
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(new ApiInfoBuilder().title("微信支付接口文档").build());
        //apiInfo()的作用在于定义接口文档的信息
    }
}
