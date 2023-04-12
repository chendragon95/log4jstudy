package com.chenlongji.log4jstudy.controller;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

import java.util.UUID;

/**
 * @author clj
 */
public class LogTest {
    // 获取logger对象
    //private static Logger logger = Logger.getLogger(LogTest.class.getName());
    private static Logger logger = Logger.getLogger(LogTest.class);

    public static void main(String[] args) {
        // 注: 部分打印不出来, 那就是配置的日志级别太高
        //testRolling();
        ///logger.setPriority("traceId", UUID.randomUUID().toString());

        MDC.put("traceId" , UUID.randomUUID().toString().replace("-", ""));
        logger.debug("这是debug级别的日志");
        logger.info("这是info级别的日志");
        logger.warn("这是warn级别的日志");
        logger.error("这是error级别的日志");
    }

    private static void testRolling() {
        for (int i = 0; i < 3; i++) {
            logger.warn("12345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456123456789012345612345678901234561234567890123456");
            try {
                Thread.sleep(1000);
            } catch (Exception e) {

            }
        }
    }
}
