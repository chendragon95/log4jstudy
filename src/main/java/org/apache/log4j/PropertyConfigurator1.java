
package org.apache.log4j;

import org.apache.log4j.config.PropertySetter;
import org.apache.log4j.helpers.FileWatchdog;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.or.RendererMap;
import org.apache.log4j.spi.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URLConnection;
import java.util.*;

public class PropertyConfigurator1 implements Configurator {

    /**
     * Used internally to keep track of configured appenders.
     */
    protected Hashtable registry = new Hashtable(11);
    private LoggerRepository repository;
    protected LoggerFactory loggerFactory = new DefaultCategoryFactory();

    static final String CATEGORY_PREFIX = "log4j.category.";
    static final String LOGGER_PREFIX = "log4j.logger.";
    static final String FACTORY_PREFIX = "log4j.factory";
    static final String ADDITIVITY_PREFIX = "log4j.additivity.";
    static final String ROOT_CATEGORY_PREFIX = "log4j.rootCategory";
    static final String ROOT_LOGGER_PREFIX = "log4j.rootLogger";
    static final String APPENDER_PREFIX = "log4j.appender.";
    static final String RENDERER_PREFIX = "log4j.renderer.";
    static final String THRESHOLD_PREFIX = "log4j.threshold";
    private static final String THROWABLE_RENDERER_PREFIX = "log4j.throwableRenderer";
    private static final String LOGGER_REF = "logger-ref";
    private static final String ROOT_REF = "root-ref";
    private static final String APPENDER_REF_TAG = "appender-ref";


    /**
     * Key for specifying the {@link LoggerFactory
     * LoggerFactory}.  Currently set to "<code>log4j.loggerFactory</code>".
     */
    public static final String LOGGER_FACTORY_KEY = "log4j.loggerFactory";

    /**
     * If property set to true, then hierarchy will be reset before configuration.
     */
    private static final String RESET_KEY = "log4j.reset";

    static final private String INTERNAL_ROOT_NAME = "root";

    /**
     * Read configuration from a file. <b>The existing configuration is
     * not cleared nor reset.</b> If you require a different behavior,
     * then call {@link  LogManager#resetConfiguration
     * resetConfiguration} method before calling
     * <code>doConfigure</code>.
     *
     * <p>The configuration file consists of statements in the format
     * <code>key=value</code>. The syntax of different configuration
     * elements are discussed below.
     *
     * <h3>Repository-wide threshold</h3>
     *
     * <p>The repository-wide threshold filters logging requests by level
     * regardless of logger. The syntax is:
     *
     * <pre>
     * log4j.threshold=[level]
     * </pre>
     *
     * <p>The level value can consist of the string values OFF, FATAL,
     * ERROR, WARN, INFO, DEBUG, ALL or a <em>custom level</em> value. A
     * custom level value can be specified in the form
     * level#classname. By default the repository-wide threshold is set
     * to the lowest possible value, namely the level <code>ALL</code>.
     * </p>
     *
     *
     * <h3>Appender configuration</h3>
     *
     * <p>Appender configuration syntax is:
     * <pre>
     * # For appender named <i>appenderName</i>, set its class.
     * # Note: The appender name can contain dots.
     * log4j.appender.appenderName=fully.qualified.name.of.appender.class
     *
     * # Set appender specific options.
     * log4j.appender.appenderName.option1=value1
     * ...
     * log4j.appender.appenderName.optionN=valueN
     * </pre>
     * <p>
     * For each named appender you can configure its {@link Layout}. The
     * syntax for configuring an appender's layout is:
     * <pre>
     * log4j.appender.appenderName.layout=fully.qualified.name.of.layout.class
     * log4j.appender.appenderName.layout.option1=value1
     * ....
     * log4j.appender.appenderName.layout.optionN=valueN
     * </pre>
     * <p>
     * The syntax for adding {@link Filter}s to an appender is:
     * <pre>
     * log4j.appender.appenderName.filter.ID=fully.qualified.name.of.filter.class
     * log4j.appender.appenderName.filter.ID.option1=value1
     * ...
     * log4j.appender.appenderName.filter.ID.optionN=valueN
     * </pre>
     * The first line defines the class name of the filter identified by ID;
     * subsequent lines with the same ID specify filter option - value
     * paris. Multiple filters are added to the appender in the lexicographic
     * order of IDs.
     * <p>
     * The syntax for adding an {@link ErrorHandler} to an appender is:
     * <pre>
     * log4j.appender.appenderName.errorhandler=fully.qualified.name.of.filter.class
     * log4j.appender.appenderName.errorhandler.root-ref={true|false}
     * log4j.appender.appenderName.errorhandler.logger-ref=loggerName
     * log4j.appender.appenderName.errorhandler.appender-ref=appenderName
     * log4j.appender.appenderName.errorhandler.option1=value1
     * ...
     * log4j.appender.appenderName.errorhandler.optionN=valueN
     * </pre>
     *
     * <h3>Configuring loggers</h3>
     *
     * <p>The syntax for configuring the root logger is:
     * <pre>
     * log4j.rootLogger=[level], appenderName, appenderName, ...
     * </pre>
     *
     * <p>This syntax means that an optional <em>level</em> can be
     * supplied followed by appender names separated by commas.
     *
     * <p>The level value can consist of the string values OFF, FATAL,
     * ERROR, WARN, INFO, DEBUG, ALL or a <em>custom level</em> value. A
     * custom level value can be specified in the form
     * <code>level#classname</code>.
     *
     * <p>If a level value is specified, then the root level is set
     * to the corresponding level.  If no level value is specified,
     * then the root level remains untouched.
     *
     * <p>The root logger can be assigned multiple appenders.
     *
     * <p>Each <i>appenderName</i> (separated by commas) will be added to
     * the root logger. The named appender is defined using the
     * appender syntax defined above.
     *
     * <p>For non-root categories the syntax is almost the same:
     * <pre>
     * log4j.logger.logger_name=[level|INHERITED|NULL], appenderName, appenderName, ...
     * </pre>
     *
     * <p>The meaning of the optional level value is discussed above
     * in relation to the root logger. In addition however, the value
     * INHERITED can be specified meaning that the named logger should
     * inherit its level from the logger hierarchy.
     *
     * <p>If no level value is supplied, then the level of the
     * named logger remains untouched.
     *
     * <p>By default categories inherit their level from the
     * hierarchy. However, if you set the level of a logger and later
     * decide that that logger should inherit its level, then you should
     * specify INHERITED as the value for the level value. NULL is a
     * synonym for INHERITED.
     *
     * <p>Similar to the root logger syntax, each <i>appenderName</i>
     * (separated by commas) will be attached to the named logger.
     *
     * <p>See the <a href="../../../../manual.html#additivity">appender
     * additivity rule</a> in the user manual for the meaning of the
     * <code>additivity</code> flag.
     *
     * <h3>ObjectRenderers</h3>
     * <p>
     * You can customize the way message objects of a given type are
     * converted to String before being logged. This is done by
     * specifying an {@link org.apache.log4j.or.ObjectRenderer ObjectRenderer}
     * for the object type would like to customize.
     *
     * <p>The syntax is:
     *
     * <pre>
     * log4j.renderer.fully.qualified.name.of.rendered.class=fully.qualified.name.of.rendering.class
     * </pre>
     * <p>
     * As in,
     * <pre>
     * log4j.renderer.my.Fruit=my.FruitRenderer
     * </pre>
     *
     * <h3>ThrowableRenderer</h3>
     * <p>
     * You can customize the way an instance of Throwable is
     * converted to String before being logged. This is done by
     * specifying an {@link ThrowableRenderer ThrowableRenderer}.
     *
     * <p>The syntax is:
     *
     * <pre>
     * log4j.throwableRenderer=fully.qualified.name.of.rendering.class
     * log4j.throwableRenderer.paramName=paramValue
     * </pre>
     * <p>
     * As in,
     * <pre>
     * log4j.throwableRenderer=org.apache.log4j.EnhancedThrowableRenderer
     * </pre>
     *
     * <h3>Logger Factories</h3>
     * <p>
     * The usage of custom logger factories is discouraged and no longer
     * documented.
     *
     * <h3>Resetting Hierarchy</h3>
     * <p>
     * The hierarchy will be reset before configuration when
     * log4j.reset=true is present in the properties file.
     *
     * <h3>Example</h3>
     *
     * <p>An example configuration is given below. Other configuration
     * file examples are given in the <code>examples</code> folder.
     *
     * <pre>
     *
     * # Set options for appender named "A1".
     * # Appender "A1" will be a SyslogAppender
     * log4j.appender.A1=org.apache.log4j.net.SyslogAppender
     *
     * # The syslog daemon resides on www.abc.net
     * log4j.appender.A1.SyslogHost=www.abc.net
     *
     * # A1's layout is a PatternLayout, using the conversion pattern
     * # <b>%r %-5p %c{2} %M.%L %x - %m\n</b>. Thus, the log output will
     * # include # the relative time since the start of the application in
     * # milliseconds, followed by the level of the log request,
     * # followed by the two rightmost components of the logger name,
     * # followed by the callers method name, followed by the line number,
     * # the nested disgnostic context and finally the message itself.
     * # Refer to the documentation of {@link PatternLayout} for further information
     * # on the syntax of the ConversionPattern key.
     * log4j.appender.A1.layout=org.apache.log4j.PatternLayout
     * log4j.appender.A1.layout.ConversionPattern=%-4r %-5p %c{2} %M.%L %x - %m\n
     *
     * # Set options for appender named "A2"
     * # A2 should be a RollingFileAppender, with maximum file size of 10 MB
     * # using at most one backup file. A2's layout is TTCC, using the
     * # ISO8061 date format with context printing enabled.
     * log4j.appender.A2=org.apache.log4j.RollingFileAppender
     * log4j.appender.A2.MaxFileSize=10MB
     * log4j.appender.A2.MaxBackupIndex=1
     * log4j.appender.A2.layout=org.apache.log4j.TTCCLayout
     * log4j.appender.A2.layout.ContextPrinting=enabled
     * log4j.appender.A2.layout.DateFormat=ISO8601
     *
     * # Root logger set to DEBUG using the A2 appender defined above.
     * log4j.rootLogger=DEBUG, A2
     *
     * # Logger definitions:
     * # The SECURITY logger inherits is level from root. However, it's output
     * # will go to A1 appender defined above. It's additivity is non-cumulative.
     * log4j.logger.SECURITY=INHERIT, A1
     * log4j.additivity.SECURITY=false
     *
     * # Only warnings or above will be logged for the logger "SECURITY.access".
     * # Output will go to A1.
     * log4j.logger.SECURITY.access=WARN
     *
     *
     * # The logger "class.of.the.day" inherits its level from the
     * # logger hierarchy.  Output will go to the appender's of the root
     * # logger, A2 in this case.
     * log4j.logger.class.of.the.day=INHERIT
     * </pre>
     *
     * <p>Refer to the <b>setOption</b> method in each Appender and
     * Layout for class specific options.
     *
     * <p>Use the <code>#</code> or <code>!</code> characters at the
     * beginning of a line for comments.
     *
     * <p>
     *
     * @param configFileName The name of the configuration file where the
     *                       configuration information is stored.
     */
    public void doConfigure(String configFileName, LoggerRepository hierarchy) {
        Properties props = new Properties();
        FileInputStream istream = null;
        try {
            istream = new FileInputStream(configFileName);
            props.load(istream);
            istream.close();
        } catch (Exception e) {
            if (e instanceof InterruptedIOException || e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LogLog.error("Could not read configuration file [" + configFileName + "].", e);
            LogLog.error("Ignoring configuration file [" + configFileName + "].");
            return;
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (InterruptedIOException ignore) {
                    Thread.currentThread().interrupt();
                } catch (Throwable ignore) {
                }

            }
        }
        // If we reach here, then the config file is alright.
        doConfigure(props, hierarchy);
    }

    /**
     *
     */
    static
    public void configure(String configFilename) {
        new PropertyConfigurator().doConfigure(configFilename,
                LogManager.getLoggerRepository());
    }

    /**
     * Read configuration options from url <code>configURL</code>.
     *
     * @since 0.8.2
     */
    public
    static void configure(java.net.URL configURL) {
        new PropertyConfigurator().doConfigure(configURL,
                LogManager.getLoggerRepository());
    }

    /**
     * Reads configuration options from an InputStream.
     *
     * @since 1.2.17
     */
    public
    static void configure(InputStream inputStream) {
        new PropertyConfigurator().doConfigure(inputStream,
                LogManager.getLoggerRepository());
    }


    /**
     * Read configuration options from <code>properties</code>.
     * <p>
     * See {@link #doConfigure(String, LoggerRepository)} for the expected format.
     */
    static
    public void configure(Properties properties) {
        new PropertyConfigurator().doConfigure(properties,
                LogManager.getLoggerRepository());
    }

    /**
     * Like {@link #configureAndWatch(String, long)} except that the
     * default delay as defined by {@link FileWatchdog#DEFAULT_DELAY} is
     * used.
     *
     * @param configFilename A file in key=value format.
     */
    static
    public void configureAndWatch(String configFilename) {
        configureAndWatch(configFilename, FileWatchdog.DEFAULT_DELAY);
    }


    /**
     * Read the configuration file <code>configFilename</code> if it
     * exists. Moreover, a thread will be created that will periodically
     * check if <code>configFilename</code> has been created or
     * modified. The period is determined by the <code>delay</code>
     * argument. If a change or file creation is detected, then
     * <code>configFilename</code> is read to configure log4j.
     *
     * @param configFilename A file in key=value format.
     * @param delay          The delay in milliseconds to wait between each check.
     */
    static
    public void configureAndWatch(String configFilename, long delay) {
        PropertyWatchdog pdog = new PropertyWatchdog(configFilename);
        pdog.setDelay(delay);
        pdog.start();
    }


    /**
     * Read configuration options from <code>properties</code>.
     * <p>
     * See {@link #doConfigure(String, LoggerRepository)} for the expected format.
     */
    public void doConfigure(Properties properties, LoggerRepository hierarchy) {
        // 缓存hierarchy, 方便后续使用
        repository = hierarchy;

        // 判断配置中log4j.debug是否开启, 开启则后续会打印log4j项目内的debug级别的日志
        String value = properties.getProperty(LogLog.DEBUG_KEY);
        if (value == null) {
            value = properties.getProperty("log4j.configDebug");
        }
        if (value != null) {
            LogLog.setInternalDebugging(OptionConverter.toBoolean(value, true));
        }

        // 判断配置中log4j.reset是否开启, 是则重置hierarchy
        String reset = properties.getProperty(RESET_KEY);
        if (reset != null && OptionConverter.toBoolean(reset, false)) {
            hierarchy.resetConfiguration();
        }

        // 获取配置中的log4j.threshold, 即全局的日志级别, 设置后低于该级别的日志都不能输出, 不设置则默认为ALL
        String thresholdStr = OptionConverter.findAndSubst(THRESHOLD_PREFIX, properties);
        if (thresholdStr != null) {
            hierarchy.setThreshold(OptionConverter.toLevel(thresholdStr, (Level) Level.ALL));
        }

        // 核心代码: 配置根logger及其appender
        configureRootCategory(properties, hierarchy);
        // 配置LoggerFactory
        configureLoggerFactory(properties);
        // 核心代码: 配置自定义的logger及其appender, 以及一些异常处理器等
        parseCatsAndRenderers(properties, hierarchy);

        LogLog.debug("Finished configuring.");
        // 去除registry缓存(该缓存作用: 缓存初始化过程中的appender), 以便不影响垃圾回收
        registry.clear();
    }

    /**
     * Read configuration options from url <code>configURL</code>.
     *
     * @since 1.2.17
     */
    public void doConfigure(InputStream inputStream, LoggerRepository hierarchy) {
        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) {
            if (e instanceof InterruptedIOException) {
                Thread.currentThread().interrupt();
            }
            LogLog.error("Could not read configuration file from InputStream [" + inputStream
                    + "].", e);
            LogLog.error("Ignoring configuration InputStream [" + inputStream + "].");
            return;
        }
        this.doConfigure(props, hierarchy);
    }

    /**
     * Read configuration options from url <code>configURL</code>.
     */
    public void doConfigure(java.net.URL configURL, LoggerRepository hierarchy) {
        // 读取配置文件内容, 写入props中
        Properties props = new Properties();
        InputStream istream = null;
        URLConnection uConn = null;
        try {
            uConn = configURL.openConnection();
            uConn.setUseCaches(false);
            istream = uConn.getInputStream();
            props.load(istream);
        } catch (Exception e) {
            return;
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (InterruptedIOException ignore) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignore) {
                } catch (RuntimeException ignore) {
                }
            }
        }
        // 继续进行配置
        doConfigure(props, hierarchy);
    }


    // --------------------------------------------------------------------------
    // Internal stuff
    // --------------------------------------------------------------------------

    /**
     * Check the provided <code>Properties</code> object for a
     * {@link LoggerFactory LoggerFactory}
     * entry specified by {@link #LOGGER_FACTORY_KEY}.  If such an entry
     * exists, an attempt is made to create an instance using the default
     * constructor.  This instance is used for subsequent Category creations
     * within this configurator.
     *
     * @see #parseCatsAndRenderers
     */
    protected void configureLoggerFactory(Properties props) {
        // 获取配置文件loggerFactory全限定名, 创建loggerFactory实例
        String factoryClassName = OptionConverter.findAndSubst("log4j.loggerFactory", props);
        if (factoryClassName != null) {
            LogLog.debug("Setting category factory to [" + factoryClassName + "].");
            loggerFactory = (LoggerFactory)
                    OptionConverter.instantiateByClassName(factoryClassName, LoggerFactory.class, loggerFactory);
            // 反射设置loggerFactory的属性
            PropertySetter.setProperties(loggerFactory, props, "log4j.factory" + ".");
        }
    }

  /*
  void configureOptionHandler(OptionHandler oh, String prefix,
			      Properties props) {
    String[] options = oh.getOptionStrings();
    if(options == null)
      return;

    String value;
    for(int i = 0; i < options.length; i++) {
      value =  OptionConverter.findAndSubst(prefix + options[i], props);
      LogLog.debug(
         "Option " + options[i] + "=[" + (value == null? "N/A" : value)+"].");
      // Some option handlers assume that null value are not passed to them.
      // So don't remove this check
      if(value != null) {
	oh.setOption(options[i], value);
      }
    }
    oh.activateOptions();
  }
  */


    void configureRootCategory(Properties props, LoggerRepository hierarchy) {
        // 获取log4j.rootLogger的配置信息.
        String effectiveFrefix = ROOT_LOGGER_PREFIX;
        // OptionConverter.findAndSubst(..)方法是获取指定key前缀的配置, 赋值到props中, 后续经常使用
        String value = OptionConverter.findAndSubst(ROOT_LOGGER_PREFIX, props);

        if (value == null) {
            // 没配置log4j.rootLogger, 则获取log4j.rootCategory
            value = OptionConverter.findAndSubst(ROOT_CATEGORY_PREFIX, props);
            effectiveFrefix = ROOT_CATEGORY_PREFIX;
        }

        if (value == null){
            LogLog.debug("Could not find root logger information. Is this OK?");
        } else {
            // 获取出hierarchy中的根logger
            Logger root = hierarchy.getRootLogger();
            synchronized (root) {
                // 核心代码: 解析根logger 及绑定它的appender (后续自定义的loggger也是走这个逻辑)
                parseCategory(props, root, effectiveFrefix, INTERNAL_ROOT_NAME, value);
            }
        }
    }


    /**
     * Parse non-root elements, such non-root categories and renderers.
     */
    protected void parseCatsAndRenderers(Properties props, LoggerRepository hierarchy) {
        // 遍历Properties中所有的key
        Enumeration enumeration = props.propertyNames();
        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            if (key.startsWith(CATEGORY_PREFIX) || key.startsWith(LOGGER_PREFIX)) {
                // 解析所有log4j.category.开头和log4j.logger.开头的key
                String loggerName = null;
                if (key.startsWith(CATEGORY_PREFIX)) {
                    loggerName = key.substring(CATEGORY_PREFIX.length());
                } else if (key.startsWith(LOGGER_PREFIX)) {
                    loggerName = key.substring(LOGGER_PREFIX.length());
                }
                String value = OptionConverter.findAndSubst(key, props);
                // 核心代码: 创建出自定义logger对象, 绑定其与其他logger的关系
                Logger logger = hierarchy.getLogger(loggerName, loggerFactory);
                synchronized (logger) {
                    // 核心代码: 解析非根logger 及绑定它的appender(这里和根logger解析是同一个方法)
                    parseCategory(props, logger, key, loggerName, value);
                    // 解析additivity属性, 设置到logger中
                    parseAdditivityForLogger(props, logger, loggerName);
                }
            } else if (key.startsWith(RENDERER_PREFIX)) {
                // 解析所有log4j.renderer.开头的key, 初始化和绑定渲染器. 较少使用, 不展开讲
                String renderedClass = key.substring(RENDERER_PREFIX.length());
                String renderingClass = OptionConverter.findAndSubst(key, props);
                if (hierarchy instanceof RendererSupport) {
                    RendererMap.addRenderer((RendererSupport) hierarchy, renderedClass,
                            renderingClass);
                }
            } else if (key.equals(THROWABLE_RENDERER_PREFIX)) {
                // 解析所有log4j.throwableRenderer开头的异常渲染器, 初始化和绑定异常渲染器. 较少使用, 不展开讲
                if (hierarchy instanceof ThrowableRendererSupport) {
                    ThrowableRenderer tr = (ThrowableRenderer)
                            OptionConverter.instantiateByKey(props, THROWABLE_RENDERER_PREFIX, ThrowableRenderer.class, null);
                    if (tr == null) {
                        LogLog.error("Could not instantiate throwableRenderer.");
                    } else {
                        PropertySetter setter = new PropertySetter(tr);
                        setter.setProperties(props, THROWABLE_RENDERER_PREFIX + ".");
                        ((ThrowableRendererSupport) hierarchy).setThrowableRenderer(tr);
                    }
                }
            }
        }
    }

    /**
     * Parse the additivity option for a non-root category.
     */
    void parseAdditivityForLogger(Properties props, Logger cat, String loggerName) {
        // 获取additivity属性(key为log4j.additivity.[loggerName])
        String value = OptionConverter.findAndSubst(ADDITIVITY_PREFIX + loggerName, props);
        LogLog.debug("Handling " + ADDITIVITY_PREFIX + loggerName + "=[" + value + "]");
        // 设置到logger的additive字段中(作用: 表示打印本logger日志后, 是否将日志添加到父级logger日志中处理, 默认值为true)
        if ((value != null) && (!value.equals(""))) {
            boolean additivity = OptionConverter.toBoolean(value, true);
            LogLog.debug("Setting additivity for \"" + loggerName + "\" to " + additivity);
            cat.setAdditivity(additivity);
        }
    }

    /**
     * This method must work for the root category as well.
     */
    void parseCategory(Properties props, Logger logger, String optionKey,
                       String loggerName, String value) {

        // 将logger的配置信息按照逗号分隔, 示例: log4j.rootLogger=info,console,rollingFile, 分隔后得到info 和 console及rollingFile
        StringTokenizer st = new StringTokenizer(value, ",");

        if (!(value.startsWith(",") || value.equals(""))) {
            if (!st.hasMoreTokens()){
                return;
            }

            // 获取当前logger的日志级别level. 第一个参数
            String levelStr = st.nextToken();

            // 非法的level值判断
            if (INHERITED.equalsIgnoreCase(levelStr) || NULL.equalsIgnoreCase(levelStr)) {
                if (loggerName.equals(INTERNAL_ROOT_NAME)) {
                    LogLog.warn("The root logger cannot be set to null.");
                } else {
                    logger.setLevel(null);
                }
            } else {
                // 设置logger的level值, 不填则设置为DEBUG
                // (不填level这时第一个appenderName就被无效掉了, 示例log4j.rootLogger=console,rollingFile, 其中console就被丢弃了)
                logger.setLevel(OptionConverter.toLevel(levelStr, (Level) Level.DEBUG));
            }
            LogLog.debug("Category " + loggerName + " set to " + logger.getLevel());
        }

        // 移除所有的appender
        logger.removeAllAppenders();

        Appender appender;
        String appenderName;
        // 遍历log4j.rootLogger中的appenderName
        while (st.hasMoreTokens()) {
            appenderName = st.nextToken().trim();
            if (appenderName == null || appenderName.equals(",")){
                continue;
            }
            // 核心代码: 根据appenderName解析出appender
            appender = parseAppender(props, appenderName);
            if (appender != null) {
                // 将appender加入到 logger的AppenderAttachableImpl的appenderList中
                logger.addAppender(appender);
            }
        }
    }

    Appender parseAppender(Properties props, String appenderName) {
        // 根据appenderName获取registry缓存中的appender, 若存在则直接放回
        Appender appender = registryGet(appenderName);
        if ((appender != null)) {
            LogLog.debug("Appender \"" + appenderName + "\" was already parsed.");
            return appender;
        }

        // 注. 这里就写死了配置文件中layout只能是小写的.
        String prefix = "log4j.appender." + appenderName;
        String layoutPrefix = prefix + ".layout";

        // 通过配置文件中类的全限定名实例化appender对象. OptionConverter.instantiateByKey(....)该方法会经常使用到
        appender = (Appender) OptionConverter.instantiateByKey(props, prefix, Appender.class, null);
        if (appender == null) {
            LogLog.error("Could not instantiate appender named \"" + appenderName + "\".");
            return null;
        }
        // 设置appender的name
        appender.setName(appenderName);

        if (appender instanceof OptionHandler) {
            if (appender.requiresLayout()) {
                // 通过配置文件中类的全限定名实例化layout对象
                Layout layout = (Layout) OptionConverter.instantiateByKey(props, layoutPrefix, Layout.class, null);
                if (layout != null) {
                    // 设置appender的layout
                    appender.setLayout(layout);
                    LogLog.debug("Parsing layout options for \"" + appenderName + "\".");
                    // 反射设置layout属性(layout.conversionPattern), 初始化解析器(代码位置: org.apache.log4j.helpers.PatternParser1.parse)
                    PropertySetter.setProperties(layout, props, layoutPrefix + ".");
                    LogLog.debug("End of parsing for \"" + appenderName + "\".");
                }
            }

            // 初始化异常处理器
            final String errorHandlerPrefix = prefix + ".errorhandler";
            String errorHandlerClass = OptionConverter.findAndSubst(errorHandlerPrefix, props);
            if (errorHandlerClass != null) {
                ErrorHandler eh = (ErrorHandler) OptionConverter.instantiateByKey(props,
                        errorHandlerPrefix,
                        ErrorHandler.class,
                        null);
                if (eh != null) {
                    // 设置appender的errorHandler
                    appender.setErrorHandler(eh);
                    LogLog.debug("Parsing errorhandler options for \"" + appenderName + "\".");
                    parseErrorHandler(eh, errorHandlerPrefix, props, repository);
                    final Properties edited = new Properties();
                    final String[] keys = new String[]{
                            errorHandlerPrefix + "." + ROOT_REF,
                            errorHandlerPrefix + "." + LOGGER_REF,
                            errorHandlerPrefix + "." + APPENDER_REF_TAG
                    };
                    for (Iterator iter = props.entrySet().iterator(); iter.hasNext(); ) {
                        Map.Entry entry = (Map.Entry) iter.next();
                        int i = 0;
                        for (; i < keys.length; i++) {
                            if (keys[i].equals(entry.getKey())) {
                                break;
                            }
                        }
                        if (i == keys.length) {
                            edited.put(entry.getKey(), entry.getValue());
                        }
                    }
                    PropertySetter.setProperties(eh, edited, errorHandlerPrefix + ".");
                    LogLog.debug("End of errorhandler parsing for \"" + appenderName + "\".");
                }

            }
            // 反射设置appender的其他属性(如encoding、threshold、maxFileSize、datePattern). PropertySetter.setProperties(...)该方法会经常使用到
            PropertySetter.setProperties(appender, props, prefix + ".");
            LogLog.debug("Parsed \"" + appenderName + "\" options.");
        }
        // 解析appender的Filter. 使用较少, 这里不展开讲
        parseAppenderFilters(props, appenderName, appender);
        // 加入registry缓存中
        registryPut(appender);
        return appender;
    }

    private void parseErrorHandler(
            final ErrorHandler eh,
            final String errorHandlerPrefix,
            final Properties props,
            final LoggerRepository hierarchy) {
        boolean rootRef = OptionConverter.toBoolean(
                OptionConverter.findAndSubst(errorHandlerPrefix + ROOT_REF, props), false);
        if (rootRef) {
            eh.setLogger(hierarchy.getRootLogger());
        }
        String loggerName = OptionConverter.findAndSubst(errorHandlerPrefix + LOGGER_REF, props);
        if (loggerName != null) {
            Logger logger = (loggerFactory == null) ? hierarchy.getLogger(loggerName)
                    : hierarchy.getLogger(loggerName, loggerFactory);
            eh.setLogger(logger);
        }
        String appenderName = OptionConverter.findAndSubst(errorHandlerPrefix + APPENDER_REF_TAG, props);
        if (appenderName != null) {
            Appender backup = parseAppender(props, appenderName);
            if (backup != null) {
                eh.setBackupAppender(backup);
            }
        }
    }


    void parseAppenderFilters(Properties props, String appenderName, Appender appender) {
        // extract filters and filter options from props into a hashtable mapping
        // the property name defining the filter class to a list of pre-parsed
        // name-value pairs associated to that filter
        final String filterPrefix = APPENDER_PREFIX + appenderName + ".filter.";
        int fIdx = filterPrefix.length();
        Hashtable filters = new Hashtable();
        Enumeration e = props.keys();
        String name = "";
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            if (key.startsWith(filterPrefix)) {
                int dotIdx = key.indexOf('.', fIdx);
                String filterKey = key;
                if (dotIdx != -1) {
                    filterKey = key.substring(0, dotIdx);
                    name = key.substring(dotIdx+1);
                }
                Vector filterOpts = (Vector) filters.get(filterKey);
                if (filterOpts == null) {
                    filterOpts = new Vector();
                    filters.put(filterKey, filterOpts);
                }
                if (dotIdx != -1) {
                    String value = OptionConverter.findAndSubst(key, props);
                    filterOpts.add(new NameValue(name, value));
                }
            }
        }

        // sort filters by IDs, insantiate filters, set filter options,
        // add filters to the appender
        Enumeration g = new SortedKeyEnumeration(filters);
        while (g.hasMoreElements()) {
            String key = (String) g.nextElement();
            String clazz = props.getProperty(key);
            if (clazz != null) {
                LogLog.debug("Filter key: ["+key+"] class: ["+props.getProperty(key) +"] props: "+filters.get(key));
                Filter filter = (Filter) OptionConverter.instantiateByClassName(clazz, Filter.class, null);
                if (filter != null) {
                    PropertySetter propSetter = new PropertySetter(filter);
                    Vector v = (Vector)filters.get(key);
                    Enumeration filterProps = v.elements();
                    while (filterProps.hasMoreElements()) {
                        NameValue kv = (NameValue)filterProps.nextElement();
                        propSetter.setProperty(kv.key, kv.value);
                    }
                    propSetter.activate();
                    LogLog.debug("Adding filter of type ["+filter.getClass()
                            +"] to appender named ["+appender.getName()+"].");
                    appender.addFilter(filter);
                }
            } else {
                LogLog.warn("Missing class definition for filter: ["+key+"]");
            }
        }
    }


    void registryPut(Appender appender) {
        registry.put(appender.getName(), appender);
    }

    Appender registryGet(String name) {
        return (Appender) registry.get(name);
    }
}

