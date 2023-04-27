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

// WARNING This class MUST not have references to the Category or
// WARNING RootCategory classes in its static initiliazation neither
// WARNING directly nor indirectly.

// Contributors:
//                Luke Blanshard <luke@quiq.com>
//                Mario Schomburg - IBM Global Services/Germany
//                Anders Kristensen
//                Igor Poteryaev

package org.apache.log4j;


import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.or.ObjectRenderer;
import org.apache.log4j.or.RendererMap;
import org.apache.log4j.spi.*;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class is specialized in retrieving loggers by name and also
 * maintaining the logger hierarchy.
 *
 * <p><em>The casual user does not have to deal with this class
 * directly.</em>
 *
 * <p>The structure of the logger hierarchy is maintained by the
 * {@link #getLogger} method. The hierarchy is such that children link
 * to their parent but parents do not have any pointers to their
 * children. Moreover, loggers can be instantiated in any order, in
 * particular descendant before ancestor.
 *
 * <p>In case a descendant is created before a particular ancestor,
 * then it creates a provision node for the ancestor and adds itself
 * to the provision node. Other descendants of the same ancestor add
 * themselves to the previously created provision node.
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class Hierarchy1 implements LoggerRepository, RendererSupport, ThrowableRendererSupport {

    private LoggerFactory defaultFactory;
    private Vector listeners;

    Hashtable ht;
    Logger root;
    RendererMap rendererMap;

    int thresholdInt;
    Level threshold;

    boolean emittedNoAppenderWarning = false;
    boolean emittedNoResourceBundleWarning = false;

    private ThrowableRenderer throwableRenderer = null;

    /**
     * Create a new logger hierarchy.
     *
     * @param root The root of the new hierarchy.
     */
    public Hierarchy1(Logger root) {
        ht = new Hashtable();
        listeners = new Vector(1);
        this.root = root;
        // Enable all level levels by default.
        setThreshold(Level.ALL);
        this.root.setHierarchy(this);
        rendererMap = new RendererMap();
        defaultFactory = new DefaultCategoryFactory();
    }

    /**
     * Add an object renderer for a specific class.
     */
    public void addRenderer(Class classToRender, ObjectRenderer or) {
        rendererMap.put(classToRender, or);
    }

    public void addHierarchyEventListener(HierarchyEventListener listener) {
        if (listeners.contains(listener)) {
            LogLog.warn("Ignoring attempt to add an existent listener.");
        } else {
            listeners.addElement(listener);
        }
    }

    /**
     * This call will clear all logger definitions from the internal
     * hashtable. Invoking this method will irrevocably mess up the
     * logger hierarchy.
     *
     * <p>You should <em>really</em> know what you are doing before
     * invoking this method.
     *
     * @since 0.9.0
     */
    public void clear() {
        //System.out.println("\n\nAbout to clear internal hash table.");
        ht.clear();
    }

    public void emitNoAppenderWarning(Category cat) {
        // No appenders in hierarchy, warn user only once.
        if (!this.emittedNoAppenderWarning) {
            LogLog.warn("No appenders could be found for logger (" +
                    cat.getName() + ").");
            LogLog.warn("Please initialize the log4j system properly.");
            LogLog.warn("See http://logging.apache.org/log4j/1.2/faq.html#noconfig for more info.");
            this.emittedNoAppenderWarning = true;
        }
    }

    /**
     * Check if the named logger exists in the hierarchy. If so return
     * its reference, otherwise returns <code>null</code>.
     *
     * @param name The name of the logger to search for.
     */
    public Logger exists(String name) {
        Object o = ht.get(new CategoryKey(name));
        if (o instanceof Logger) {
            return (Logger) o;
        } else {
            return null;
        }
    }

    /**
     * The string form of {@link #setThreshold(Level)}.
     */
    public void setThreshold(String levelStr) {
        Level l = (Level) Level.toLevel(levelStr, null);
        if (l != null) {
            setThreshold(l);
        } else {
            LogLog.warn("Could not convert [" + levelStr + "] to Level.");
        }
    }


    /**
     * Enable logging for logging requests with level <code>l</code> or
     * higher. By default all levels are enabled.
     *
     * @param l The minimum level for which logging requests are sent to
     *          their appenders.
     */
    public void setThreshold(Level l) {
        if (l != null) {
            thresholdInt = l.level;
            threshold = l;
        }
    }

    public void fireAddAppenderEvent(Category logger, Appender appender) {
        if (listeners != null) {
            int size = listeners.size();
            HierarchyEventListener listener;
            for (int i = 0; i < size; i++) {
                listener = (HierarchyEventListener) listeners.elementAt(i);
                listener.addAppenderEvent(logger, appender);
            }
        }
    }

    void fireRemoveAppenderEvent(Category logger, Appender appender) {
        if (listeners != null) {
            int size = listeners.size();
            HierarchyEventListener listener;
            for (int i = 0; i < size; i++) {
                listener = (HierarchyEventListener) listeners.elementAt(i);
                listener.removeAppenderEvent(logger, appender);
            }
        }
    }

    /**
     * Returns a {@link Level} representation of the <code>enable</code>
     * state.
     *
     * @since 1.2
     */
    public Level getThreshold() {
        return threshold;
    }

    /**
     Returns an integer representation of the this repository's
     threshold.

     @since 1.2 */
    //public
    //int getThresholdInt() {
    //  return thresholdInt;
    //}


    /**
     * Return a new logger instance named as the first parameter using
     * the default factory.
     *
     * <p>If a logger of that name already exists, then it will be
     * returned.  Otherwise, a new logger will be instantiated and
     * then linked with its existing ancestors as well as children.
     *
     * @param name The name of the logger to retrieve.
     */
    public Logger getLogger(String name) {
        return getLogger(name, defaultFactory);
    }

    /**
     * Return a new logger instance named as the first parameter using
     * <code>factory</code>.
     *
     * <p>If a logger of that name already exists, then it will be
     * returned.  Otherwise, a new logger will be instantiated by the
     * <code>factory</code> parameter and linked with its existing
     * ancestors as well as children.
     *
     * @param name    The name of the logger to retrieve.
     * @param factory The factory that will make the new logger instance.
     */
    public Logger getLogger(String name, LoggerFactory factory) {
        CategoryKey key = new CategoryKey(name);
        Logger logger;
        synchronized (ht) {
            Object o = ht.get(key);
            if (o == null) {
                // 为空. 创建logger实例. 默认实现 Logger logger = new Logger(name)
                logger = factory.makeNewLoggerInstance(name);
                logger.setHierarchy(this);
                // 写入Hierarchy的ht(Hashtable)缓存中
                ht.put(key, logger);
                // 绑定当前logger和父logger
                updateParents(logger);
                return logger;
            } else if (o instanceof Logger) {
                // Logger类型. 直接返回
                return (Logger) o;
            } else if (o instanceof ProvisionNode) {
                // 临时节点类型. 先创建出该节点
                logger = factory.makeNewLoggerInstance(name);
                logger.setHierarchy(this);
                // 写入Hierarchy的ht(Hashtable)缓存中
                ht.put(key, logger);
                // 找到创建logger之前临时存储的子节点, 重新绑定(子logger->旧parent  =>  子logger->当前logger->旧parent)
                updateChildren((ProvisionNode) o, logger);
                // 绑定当前logger和父logger
                updateParents(logger);
                return logger;
            } else {
                return null;
            }
        }
    }

    /**
     * Returns all the currently defined categories in this hierarchy as
     * an {@link Enumeration Enumeration}.
     *
     * <p>The root logger is <em>not</em> included in the returned
     * {@link Enumeration}.
     */
    public Enumeration getCurrentLoggers() {
        // The accumlation in v is necessary because not all elements in
        // ht are Logger objects as there might be some ProvisionNodes
        // as well.
        Vector v = new Vector(ht.size());

        Enumeration elems = ht.elements();
        while (elems.hasMoreElements()) {
            Object o = elems.nextElement();
            if (o instanceof Logger) {
                v.addElement(o);
            }
        }
        return v.elements();
    }

    /**
     * @deprecated Please use {@link #getCurrentLoggers} instead.
     */
    public Enumeration getCurrentCategories() {
        return getCurrentLoggers();
    }


    /**
     * Get the renderer map for this hierarchy.
     */
    public RendererMap getRendererMap() {
        return rendererMap;
    }


    /**
     * Get the root of this hierarchy.
     *
     * @since 0.9.0
     */
    public Logger getRootLogger() {
        return root;
    }

    /**
     * This method will return <code>true</code> if this repository is
     * disabled for <code>level</code> object passed as parameter and
     * <code>false</code> otherwise. See also the {@link
     * #setThreshold(Level) threshold} emthod.
     */
    public boolean isDisabled(int level) {
        return thresholdInt > level;
    }

    /**
     * @deprecated Deprecated with no replacement.
     */
    public void overrideAsNeeded(String override) {
        LogLog.warn("The Hiearchy.overrideAsNeeded method has been deprecated.");
    }

    /**
     * Reset all values contained in this hierarchy instance to their
     * default.  This removes all appenders from all categories, sets
     * the level of all non-root categories to <code>null</code>,
     * sets their additivity flag to <code>true</code> and sets the level
     * of the root logger to {@link Level#DEBUG DEBUG}.  Moreover,
     * message disabling is set its default "off" value.
     *
     * <p>Existing categories are not removed. They are just reset.
     *
     * <p>This method should be used sparingly and with care as it will
     * block all logging until it is completed.</p>
     *
     * @since 0.8.5
     */
    public void resetConfiguration() {

        getRootLogger().setLevel((Level) Level.DEBUG);
        root.setResourceBundle(null);
        setThreshold(Level.ALL);

        // the synchronization is needed to prevent JDK 1.2.x hashtable
        // surprises
        synchronized (ht) {
            shutdown(); // nested locks are OK

            Enumeration cats = getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.setLevel(null);
                c.setAdditivity(true);
                c.setResourceBundle(null);
            }
        }
        rendererMap.clear();
        throwableRenderer = null;
    }

    /**
     * Does nothing.
     *
     * @deprecated Deprecated with no replacement.
     */
    public void setDisableOverride(String override) {
        LogLog.warn("The Hiearchy.setDisableOverride method has been deprecated.");
    }


    /**
     * Used by subclasses to add a renderer to the hierarchy passed as parameter.
     */
    public void setRenderer(Class renderedClass, ObjectRenderer renderer) {
        rendererMap.put(renderedClass, renderer);
    }

    /**
     * {@inheritDoc}
     */
    public void setThrowableRenderer(final ThrowableRenderer renderer) {
        throwableRenderer = renderer;
    }

    /**
     * {@inheritDoc}
     */
    public ThrowableRenderer getThrowableRenderer() {
        return throwableRenderer;
    }


    /**
     * Shutting down a hierarchy will <em>safely</em> close and remove
     * all appenders in all categories including the root logger.
     *
     * <p>Some appenders such as {@link org.apache.log4j.net.SocketAppender}
     * and {@link AsyncAppender} need to be closed before the
     * application exists. Otherwise, pending logging events might be
     * lost.
     *
     * <p>The <code>shutdown</code> method is careful to close nested
     * appenders before closing regular appenders. This is allows
     * configurations where a regular appender is attached to a logger
     * and again to a nested appender.
     *
     * @since 1.0
     */
    public void shutdown() {
        Logger root = getRootLogger();

        // begin by closing nested appenders
        root.closeNestedAppenders();

        synchronized (ht) {
            Enumeration cats = this.getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.closeNestedAppenders();
            }

            // then, remove all appenders
            root.removeAllAppenders();
            cats = this.getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.removeAllAppenders();
            }
        }
    }


    /**
     * This method loops through all the *potential* parents of
     * 'cat'. There 3 possible cases:
     * <p>
     * 1) No entry for the potential parent of 'cat' exists
     * <p>
     * We create a ProvisionNode for this potential parent and insert
     * 'cat' in that provision node.
     * <p>
     * 2) There entry is of type Logger for the potential parent.
     * <p>
     * The entry is 'cat's nearest existing parent. We update cat's
     * parent field with this entry. We also break from the loop
     * because updating our parent's parent is our parent's
     * responsibility.
     * <p>
     * 3) There entry is of type ProvisionNode for this potential parent.
     * <p>
     * We add 'cat' to the list of children for this potential parent.
     */
    final private void updateParents(Logger cat) {
        // 示例logger的name = com.clj.abc, 假设初始化到当前只解析了 根logger和logger(com.clj.abc)
        String name = cat.name;
        int length = name.length();
        // 标识是否找到父类了
        boolean parentFound = false;

        // 这里遍历出com.clj 和 com
        for (int i = name.lastIndexOf('.', length - 1); i >= 0;
             i = name.lastIndexOf('.', i - 1)) {
            // 第一次遍历得到com.clj. 第二次遍历得到com
            String substr = name.substring(0, i);
            CategoryKey key = new CategoryKey(substr);
            Object o = ht.get(key);
            // Create a provision node for a future parent.
            if (o == null) {
                // ht中没缓存到, 则创建出临时节点, 临时节点存储的是当前的logger(com.clj.abc)
                ProvisionNode pn = new ProvisionNode(cat);
                // ht缓存临时节点. 第一次遍历(缓存了com.clj -> [logger(com.clj.abc)]), 第二次遍历(缓存了com -> [logger(com.clj.abc)])
                ht.put(key, pn);
            } else if (o instanceof Category) {
                // 若ht中得到是Category类型, 直接绑定当前logger的父节点, 修改parentFound标识
                parentFound = true;
                cat.parent = (Category) o;
                break;
            } else if (o instanceof ProvisionNode) {
                // 若ht中得到是ProvisionNode类型, 则将当前cat加入列表.
                // 这里假设之前o的列表为 [logger(com.clj.def)], 添加后列表为 [logger(com.clj.def), logger(com.clj.abc)],
                //  对应ht中的缓存键值对为com.clj -> [logger(com.clj.def), logger(com.clj.abc)]
                ((ProvisionNode) o).addElement(cat);
            } else {
                Exception e = new IllegalStateException("unexpected object type " + o.getClass() + " in ht.");
                e.printStackTrace();
            }
        }
        // 暂没找到父类, 那直接设置根logger为父类
        if (!parentFound) {
            cat.parent = root;
        }
    }

    /**
     * We update the links for all the children that placed themselves
     * in the provision node 'pn'. The second argument 'cat' is a
     * reference for the newly created Logger, parent of all the
     * children in 'pn'
     * <p>
     * We loop on all the children 'c' in 'pn':
     * <p>
     * If the child 'c' has been already linked to a child of
     * 'cat' then there is no need to update 'c'.
     * <p>
     * Otherwise, we set cat's parent field to c's parent and set
     * c's parent field to cat.
     */
    final private void updateChildren(ProvisionNode pn, Logger logger) {
        // 遍历临时节点中所有的子节点
        final int last = pn.size();
        for (int i = 0; i < last; i++) {
            Logger l = (Logger) pn.elementAt(i);
            // 若子节点的 原父logger 不是当前logger, 则设置子节点的 父节点为 当前logger, 设置当前logger的 父节点为 原父logger
            // 示例: 当前logger(com.clj), 子节点logger(com.clj.abc), 子节点的父logger为 root, 即 logger(com.clj.abc) -> logger(root)
            //       处理后为: logger(com.clj.abc) -> logger(com.clj) -> logger(root)
            if (!l.parent.name.startsWith(logger.name)) {
                logger.parent = l.parent;
                l.parent = logger;
            }
        }
    }

}


