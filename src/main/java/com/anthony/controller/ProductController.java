package com.anthony.controller;

import com.anthony.entity.Product;
import com.anthony.service.ProductService;
import com.anthony.valueobject.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * @author Anthony_CMH
 * @create 2023-05-16 10:08
 */
@CrossOrigin //开放前端的跨域访问
@Api(tags="商品管理") //定义这个控制类在Swagger中的名字
@RestController
@RequestMapping("/api/product")
public class ProductController {
    @Resource
    private ProductService productService;

    @ApiOperation("接口测试") //定义这个方法在Swagger中的名字
    @GetMapping("/test")
    public R test(){
        return R.ok().data("message","hello").data("now",new Date());
    }


    @GetMapping("/list")
    public R list(){
        List<Product> list = productService.list();
        return R.ok().data("productList",list);
    }

}
