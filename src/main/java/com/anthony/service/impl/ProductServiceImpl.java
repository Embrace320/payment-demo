package com.anthony.service.impl;

import com.anthony.entity.Product;
import com.anthony.mapper.ProductMapper;
import com.anthony.service.ProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

}
