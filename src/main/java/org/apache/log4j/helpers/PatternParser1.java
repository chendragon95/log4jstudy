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

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

public class PatternParser1 {

    private static final char ESCAPE_CHAR = '%';

    private static final int LITERAL_STATE = 0;
    private static final int CONVERTER_STATE = 1;
    private static final int DOT_STATE = 3;
    private static final int MIN_STATE = 4;
    private static final int MAX_STATE = 5;

    static final int FULL_LOCATION_CONVERTER = 1000;
    static final int METHOD_LOCATION_CONVERTER = 1001;
    static final int CLASS_LOCATION_CONVERTER = 1002;
    static final int LINE_LOCATION_CONVERTER = 1003;
    static final int FILE_LOCATION_CONVERTER = 1004;

    static final int RELATIVE_TIME_CONVERTER = 2000;
    static final int THREAD_CONVERTER = 2001;
    static final int LEVEL_CONVERTER = 2002;
    static final int NDC_CONVERTER = 2003;
    static final int MESSAGE_CONVERTER = 2004;

    int state;
    protected StringBuffer currentLiteral = new StringBuffer(32);
    protected int patternLength;
    protected int i;
    PatternConverter1 head;
    PatternConverter1 tail;
    protected FormattingInfo1 formattingInfo = new FormattingInfo1();
    protected String pattern;

    public PatternParser1(String pattern) {
        this.pattern = pattern;
        patternLength = pattern.length();
        state = LITERAL_STATE;
    }

    private void addToList(PatternConverter1 pc) {
        if (head == null) {
            head = tail = pc;
        } else {
            tail.next = pc;
            tail = pc;
        }
    }

    protected String extractOption() {
        if ((i < patternLength) && (pattern.charAt(i) == '{')) {
            int end = pattern.indexOf('}', i);
            if (end > i) {
                String r = pattern.substring(i + 1, end);
                i = end + 1;
                return r;
            }
        }
        return null;
    }


    /**
     * The option is expected to be in decimal and positive. In case of
     * error, zero is returned.
     */
    protected int extractPrecisionOption() {
        String opt = extractOption();
        int r = 0;
        if (opt != null) {
            try {
                r = Integer.parseInt(opt);
                if (r <= 0) {
                    LogLog.error(
                            "Precision option (" + opt + ") isn't a positive integer.");
                    r = 0;
                }
            } catch (NumberFormatException e) {
                LogLog.error("Category option \"" + opt + "\" not a decimal integer.", e);
            }
        }
        return r;
    }

    public PatternConverter1 parse() {
        char c;
        i = 0;
        while (i < patternLength) {
            c = pattern.charAt(i++);
            switch (state) {
                case LITERAL_STATE:
                    // 在字面状态下，最后一个字符总是一个字面
                    if (i == patternLength) {
                        currentLiteral.append(c);
                        continue;
                    }
                    if (c == '%') {
                        // %起头, 看下一个字符
                        switch (pattern.charAt(i)) {
                            case '%':
                                // %% -> 输出%
                                currentLiteral.append(c);
                                i++;
                                break;
                            case 'n':
                                // %n -> 输出系统当前换行符
                                currentLiteral.append(Layout.LINE_SEP);
                                i++;
                                break;
                            default:
                                if (currentLiteral.length() != 0) {
                                    addToList(new LiteralPatternConverter(currentLiteral.toString()));
                                }
                                currentLiteral.setLength(0);
                                // append %
                                currentLiteral.append(c);
                                state = CONVERTER_STATE;
                                formattingInfo.reset();
                        }
                    } else {
                        currentLiteral.append(c);
                    }
                    break;
                case CONVERTER_STATE:
                    currentLiteral.append(c);
                    switch (c) {
                        case '-':
                            // 左对齐
                            formattingInfo.leftAlign = true;
                            break;
                        case '.':
                            state = DOT_STATE;
                            break;
                        default:
                            // 拼接小数点前的数字
                            if (c >= '0' && c <= '9') {
                                formattingInfo.min = c - '0';
                                state = MIN_STATE;
                            } else{
                                finalizeConverter(c);
                            }
                    }
                    break;
                case MIN_STATE:
                    currentLiteral.append(c);
                    // 拼接小数点前的数字
                    if (c >= '0' && c <= '9') {
                        formattingInfo.min = formattingInfo.min * 10 + (c - '0');
                    }
                    else if (c == '.') {
                        state = DOT_STATE;
                    }
                    else {
                        finalizeConverter(c);
                    }
                    break;
                case DOT_STATE:
                    currentLiteral.append(c);
                    // 拼接小数点后的数字
                    if (c >= '0' && c <= '9') {
                        formattingInfo.max = c - '0';
                        state = MAX_STATE;
                    } else {
                        LogLog.error("Error occured in position " + i + ".\n Was expecting digit, instead got char \"" + c + "\".");
                        state = LITERAL_STATE;
                    }
                    break;
                case MAX_STATE:
                    currentLiteral.append(c);
                    // 拼接小数点后的数字
                    if (c >= '0' && c <= '9'){
                        formattingInfo.max = formattingInfo.max * 10 + (c - '0');
                    }
                    else {
                        finalizeConverter(c);
                        state = LITERAL_STATE;
                    }
                    break;
            }
        }
        if (currentLiteral.length() != 0) {
            addToList(new LiteralPatternConverter(currentLiteral.toString()));
        }
        return head;
    }

    protected void finalizeConverter(char c) {
        PatternConverter1 pc = null;
        switch (c) {
            case 'c':
                // 获取LoggerName, 示例%c、%c{2} 花括号的内容表示取全限定名从右到左第几个.开始后段部分的值
                pc = new CategoryPatternConverter(formattingInfo, extractPrecisionOption());
                currentLiteral.setLength(0);
                break;
            case 'C':
                // 输出日志消息产生时所在类的全限定类名, 示例%C、%C{2} 花括号的内容表示取全限定名从右到左第几个.开始后段部分的值
                pc = new ClassNamePatternConverter(formattingInfo, extractPrecisionOption());
                currentLiteral.setLength(0);
                break;
            case 'd':
                String dateFormatStr = AbsoluteTimeDateFormat.ISO8601_DATE_FORMAT;
                DateFormat df;
                String dOpt = extractOption();
                if (dOpt != null) {
                    dateFormatStr = dOpt;
                }
                if (dateFormatStr.equalsIgnoreCase(AbsoluteTimeDateFormat.ISO8601_DATE_FORMAT)) {
                    df = new ISO8601DateFormat();
                } else if (dateFormatStr.equalsIgnoreCase(AbsoluteTimeDateFormat.ABS_TIME_DATE_FORMAT)) {
                    df = new AbsoluteTimeDateFormat();
                } else if (dateFormatStr.equalsIgnoreCase(AbsoluteTimeDateFormat.DATE_AND_TIME_DATE_FORMAT)) {
                    df = new DateTimeDateFormat();
                } else {
                    try {
                        df = new SimpleDateFormat(dateFormatStr);
                    } catch (IllegalArgumentException e) {
                        LogLog.error("Could not instantiate SimpleDateFormat with " + dateFormatStr, e);
                        df = (DateFormat) OptionConverter.instantiateByClassName(
                                "org.apache.log4j.helpers.ISO8601DateFormat", DateFormat.class, null);
                    }
                }
                pc = new DatePatternConverter(formattingInfo, df);
                currentLiteral.setLength(0);
                break;
            case 'F':
                pc = new LocationPatternConverter(formattingInfo, FILE_LOCATION_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 'l':
                pc = new LocationPatternConverter(formattingInfo, FULL_LOCATION_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 'L':
                pc = new LocationPatternConverter(formattingInfo, LINE_LOCATION_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 'm':
                pc = new BasicPatternConverter(formattingInfo, MESSAGE_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 'M':
                pc = new LocationPatternConverter(formattingInfo, METHOD_LOCATION_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 'p':
                pc = new BasicPatternConverter(formattingInfo, LEVEL_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 'r':
                pc = new BasicPatternConverter(formattingInfo, RELATIVE_TIME_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 't':
                pc = new BasicPatternConverter(formattingInfo, THREAD_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 'x':
                pc = new BasicPatternConverter(formattingInfo, NDC_CONVERTER);
                currentLiteral.setLength(0);
                break;
            case 'X':
                String xOpt = extractOption();
                pc = new MDCPatternConverter(formattingInfo, xOpt);
                currentLiteral.setLength(0);
                break;
            default:
                LogLog.error("Unexpected char [" + c + "] at position " + i + " in conversion patterrn.");
                pc = new LiteralPatternConverter(currentLiteral.toString());
                currentLiteral.setLength(0);
        }

        // 添加转换器
        addConverter(pc);
    }

    protected void addConverter(PatternConverter1 pc) {
        currentLiteral.setLength(0);
        // Add the pattern converter to the list.
        addToList(pc);
        // Next pattern is assumed to be a literal.
        state = LITERAL_STATE;
        // Reset formatting info
        formattingInfo.reset();
    }

    // ---------------------------------------------------------------------
    //                      PatternConverters
    // ---------------------------------------------------------------------

    private static class BasicPatternConverter extends PatternConverter1 {
        int type;

        BasicPatternConverter(FormattingInfo1 formattingInfo, int type) {
            super(formattingInfo);
            this.type = type;
        }

        @Override
        public String convert(LoggingEvent event) {
            switch (type) {
                case RELATIVE_TIME_CONVERTER:
                    return (Long.toString(event.timeStamp - LoggingEvent.getStartTime()));
                case THREAD_CONVERTER:
                    return event.getThreadName();
                case LEVEL_CONVERTER:
                    return event.getLevel().toString();
                case NDC_CONVERTER:
                    return event.getNDC();
                case MESSAGE_CONVERTER: {
                    return event.getRenderedMessage();
                }
                default:
                    return null;
            }
        }
    }

    private static class LiteralPatternConverter extends PatternConverter1 {
        private String literal;

        LiteralPatternConverter(String value) {
            literal = value;
        }

        @Override
        public final void format(StringBuffer sbuf, LoggingEvent event) {
            sbuf.append(literal);
        }

        @Override
        public String convert(LoggingEvent event) {
            return literal;
        }
    }

    private static class DatePatternConverter extends PatternConverter1 {
        private DateFormat df;
        private Date date;

        DatePatternConverter(FormattingInfo1 formattingInfo, DateFormat df) {
            super(formattingInfo);
            date = new Date();
            this.df = df;
        }

        @Override
        public String convert(LoggingEvent event) {
            date.setTime(event.timeStamp);
            String converted = null;
            try {
                converted = df.format(date);
            } catch (Exception ex) {
                LogLog.error("Error occured while converting date.", ex);
            }
            return converted;
        }
    }

    private static class MDCPatternConverter extends PatternConverter1 {
        private String key;

        MDCPatternConverter(FormattingInfo1 formattingInfo, String key) {
            super(formattingInfo);
            this.key = key;
        }

        @Override
        public String convert(LoggingEvent event) {
            if (key == null) {
                StringBuffer buf = new StringBuffer("{");
                Map properties = event.getProperties();
                if (properties.size() > 0) {
                    Object[] keys = properties.keySet().toArray();
                    Arrays.sort(keys);
                    for (int i = 0; i < keys.length; i++) {
                        buf.append('{');
                        buf.append(keys[i]);
                        buf.append(',');
                        buf.append(properties.get(keys[i]));
                        buf.append('}');
                    }
                }
                buf.append('}');
                return buf.toString();
            } else {
                Object val = event.getMDC(key);
                if (val == null) {
                    return null;
                } else {
                    return val.toString();
                }
            }
        }
    }


    private class LocationPatternConverter extends PatternConverter1 {
        int type;

        LocationPatternConverter(FormattingInfo1 formattingInfo, int type) {
            super(formattingInfo);
            this.type = type;
        }

        @Override
        public String convert(LoggingEvent event) {
            LocationInfo locationInfo = event.getLocationInformation();
            switch (type) {
                case FULL_LOCATION_CONVERTER:
                    return locationInfo.fullInfo;
                case METHOD_LOCATION_CONVERTER:
                    return locationInfo.getMethodName();
                case LINE_LOCATION_CONVERTER:
                    return locationInfo.getLineNumber();
                case FILE_LOCATION_CONVERTER:
                    return locationInfo.getFileName();
                default:
                    return null;
            }
        }
    }

    private static abstract class NamedPatternConverter extends PatternConverter1 {
        int precision;

        NamedPatternConverter(FormattingInfo1 formattingInfo, int precision) {
            super(formattingInfo);
            this.precision = precision;
        }

        abstract String getFullyQualifiedName(LoggingEvent event);

        @Override
        public String convert(LoggingEvent event) {
            String n = getFullyQualifiedName(event);
            if (precision <= 0) {
                return n;
            } else {
                int len = n.length();
                int end = len - 1;
                for (int i = precision; i > 0; i--) {
                    end = n.lastIndexOf('.', end - 1);
                    if (end == -1) {
                        return n;
                    }
                }
                return n.substring(end + 1, len);
            }
        }
    }

    private class ClassNamePatternConverter extends NamedPatternConverter {

        ClassNamePatternConverter(FormattingInfo1 formattingInfo, int precision) {
            super(formattingInfo, precision);
        }

        @Override
        String getFullyQualifiedName(LoggingEvent event) {
            return event.getLocationInformation().getClassName();
        }
    }

    private class CategoryPatternConverter extends NamedPatternConverter {

        CategoryPatternConverter(FormattingInfo1 formattingInfo, int precision) {
            super(formattingInfo, precision);
        }

        @Override
        String getFullyQualifiedName(LoggingEvent event) {
            return event.getLoggerName();
        }
    }
}

