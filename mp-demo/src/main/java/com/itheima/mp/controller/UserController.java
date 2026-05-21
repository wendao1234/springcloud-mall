package com.itheima.mp.controller;


import com.itheima.mp.service.IUserService;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author author
 * @since 2025-08-26
 */
@RestController
@RequestMapping("/user")
@Slf4j
@ApiOperation("用户管理")
public class UserController {
    @Autowired
    private IUserService userService;


}
