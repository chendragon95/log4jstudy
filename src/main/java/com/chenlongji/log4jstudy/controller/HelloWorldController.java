package com.chenlongji.log4jstudy.controller;


import org.apache.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author clj
 * 该代码与log4j无关
 */
@RestController
@RequestMapping("")
public class HelloWorldController {

    private static Logger logger = Logger.getLogger(HelloWorldController.class);

    @GetMapping("/hello")
    public String hello (){
        System.out.println("很好啊");
        logger.info("这是info的日志");
        return "hello clj";
    }

}
