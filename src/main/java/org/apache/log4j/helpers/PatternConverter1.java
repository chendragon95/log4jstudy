/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j.helpers;

import org.apache.log4j.spi.LoggingEvent;


public abstract class PatternConverter1 {
    // 下一个模式转换器
    public PatternConverter1 next;
    // 小数点左侧的值
    int min = -1;
    // 小数点右侧的值
    int max = 0x7FFFFFFF;
    // 左对齐标志
    boolean leftAlign = false;

    protected PatternConverter1() {
    }

    protected PatternConverter1(FormattingInfo1 fi) {
        min = fi.min;
        max = fi.max;
        leftAlign = fi.leftAlign;
    }

    abstract protected String convert(LoggingEvent event);

    public void format(StringBuffer sbuf, LoggingEvent e) {
        // 执行子类的convert方法
        String s = convert(e);

        if (s == null) {
            // 直接拼接min个空格
            if (0 < min) {
                spacePad(sbuf, min);
            }
            return;
        }

        int len = s.length();

        // 实际内容大于 阈值, 保留指定阈值长度右侧内容
        if (len > max) {
            sbuf.append(s.substring(len - max));
        } else if (len < min) {
            // 左对齐, 先拼接内容再拼接多余空格
            if (leftAlign) {
                sbuf.append(s);
                spacePad(sbuf, min - len);
            }
            // 右对齐, 先拼接多余空格再拼接内容
            else {
                spacePad(sbuf, min - len);
                sbuf.append(s);
            }
        } else {
            // 没有额外设置, 则直接拼接内容
            sbuf.append(s);
        }
    }

    static String[] SPACES = {" ", "  ", "    ", "        ", //1,2,4,8 spaces
            "                ", // 16 spaces
            "                                "}; // 32 spaces

    /**
     * 有几个空格就拼接几个空格
     */
    public void spacePad(StringBuffer sbuf, int length) {
        while (length >= 32) {
            sbuf.append(SPACES[5]);
            length -= 32;
        }

        for (int i = 4; i >= 0; i--) {
            if ((length & (1 << i)) != 0) {
                sbuf.append(SPACES[i]);
            }
        }
    }
}
