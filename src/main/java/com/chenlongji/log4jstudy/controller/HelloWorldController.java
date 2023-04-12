package com.chenlongji.log4jstudy.controller;


import org.apache.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author clj
 */
@RestController
@RequestMapping("")
public class HelloWorldController {
    /**
     * log4j使用方式
     * logback使用方式 private static Logger logger = LoggerFactory.getLogger(HelloWorldController.class);
     */
    private static Logger logger = Logger.getLogger(HelloWorldController.class);

    @GetMapping("/hello")
    public String hello (){
        System.out.println("很好啊");
        logger.info("这是info的日志");
        return "hello clj";
    }

    public static void main(String[] args) {
        /*System.out.println("很好啊");
        logger.debug("这是debug级别的日志");
        logger.info("这是info级别的日志");
        logger.warn("这是warn级别的日志");
        logger.error("这是error级别的日志");*/
        for (int i = 0; i < 1000; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.error("这是error级别的日志" + i);
        }
    }

}
