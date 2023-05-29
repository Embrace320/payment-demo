package com.anthony.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Anthony_CMH
 * @create 2023-05-16 15:26
 */
@Configuration
@MapperScan("com.anthony.mapper") //开启扫描 如果不使用，就必须要求使用@Mapper的类路径在Springboot路径下能找到，才可以正常使用。
@EnableTransactionManagement //启用事务管理
public class MybatisPlusConfig {

}
