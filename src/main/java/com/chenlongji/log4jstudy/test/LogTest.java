package com.chenlongji.log4jstudy.test;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * @author clj
 * 使用文档: https://blog.csdn.net/Java_dragon95/article/details/129953654
 * 源码文档: https://blog.csdn.net/Java_dragon95/article/details/130013167
 */
public class LogTest {
    /**
     * log4j使用的需要依赖
     *   <dependency>
     *       <groupId>log4j</groupId>
     *       <artifactId>log4j</artifactId>
     *       <version>1.2.17</version>
     *   </dependency>
     *
     *
     * 获取logger对象
     *
     * logback使用方式
     *      private static Logger logger = LoggerFactory.getLogger(HelloWorldController.class);
     *
     * log4j使用方式
     *      private static Logger logger = Logger.getLogger(LogTest.class.getName());
     *      private static Logger logger = Logger.getLogger(LogTest.class);
     */
    private static Logger logger = Logger.getLogger(LogTest.class);

    /**
     * logback获取logger对象方式
     */
    private static org.slf4j.Logger logger2 = LoggerFactory.getLogger(LogTest.class);

    public static void main(String[] args) {
        //testRolling();

        // 设置mdc属性
        MDC.put("traceId" , UUID.randomUUID().toString().replace("-", ""));

        // 注: 若部分打印不出来, 那就是配置的日志级别太高
        /*logger.debug("这是debug级别的日志");
        logger.info("这是info级别的日志");
        logger.warn("这是warn级别的日志");*/
        logger.error("这是error级别的日志");
    }

    /**
     * 测试滚动文件
     */
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
