/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.log4j.builders.appender;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.bridge.AppenderWrapper;
import org.apache.log4j.bridge.LayoutAdapter;
import org.apache.log4j.bridge.LayoutWrapper;
import org.apache.log4j.builders.AbstractBuilder;
import org.apache.log4j.builders.BooleanHolder;
import org.apache.log4j.builders.Holder;
import org.apache.log4j.config.Log4j1Configuration;
import org.apache.log4j.config.PropertiesConfiguration;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.xml.XmlConfiguration;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.status.StatusLogger;
import org.w3c.dom.Element;

import java.util.Properties;

import static org.apache.log4j.builders.BuilderManager.CATEGORY;
import static org.apache.log4j.config.Log4j1Configuration.THRESHOLD_PARAM;
import static org.apache.log4j.xml.XmlConfiguration.FILTER_TAG;
import static org.apache.log4j.xml.XmlConfiguration.LAYOUT_TAG;
import static org.apache.log4j.xml.XmlConfiguration.PARAM_TAG;
import static org.apache.log4j.xml.XmlConfiguration.forEachElement;
import static org.apache.log4j.xml.XmlConfiguration.NAME_ATTR;
import static org.apache.log4j.xml.XmlConfiguration.VALUE_ATTR;

/**
 * Build a File Appender
 */
@Plugin(name = "org.apache.log4j.FileAppender", category = CATEGORY)
public class FileAppenderBuilder extends AbstractBuilder implements AppenderBuilder {

    private static final Logger LOGGER = StatusLogger.getLogger();

    public FileAppenderBuilder() {
    }

    public FileAppenderBuilder(final String prefix, final Properties props) {
        super(prefix, props);
    }

    @Override
    public Appender parseAppender(Element appenderElement, XmlConfiguration config) {
        String name = getNameAttribute(appenderElement);
        Holder<Layout> layout = new Holder<>();
        Holder<Filter> filter = new Holder<>();
        Holder<String> fileName = new Holder<>();
        Holder<String> level = new Holder<>();
        Holder<Boolean> immediateFlush = new BooleanHolder(true);
        Holder<Boolean> append = new BooleanHolder(true);
        Holder<Boolean> bufferedIo = new BooleanHolder();
        Holder<Integer> bufferSize = new Holder<>(8192);
        forEachElement(appenderElement.getChildNodes(), (currentElement) -> {
            switch (currentElement.getTagName()) {
                case LAYOUT_TAG:
                    layout.set(config.parseLayout(currentElement));
                    break;
                case FILTER_TAG:
                    config.addFilter(filter, currentElement);
                    break;
                case PARAM_TAG:
                    switch (getNameAttributeKey(currentElement)) {
                        case FILE_PARAM:
                            setString(FILE_PARAM, currentElement, fileName);
                            break;
                        case APPEND_PARAM:
                            setBoolean(APPEND_PARAM, currentElement, append);
                            break;
                        case BUFFERED_IO_PARAM:
                            setBoolean(BUFFERED_IO_PARAM, currentElement, bufferedIo);
                            break;
                        case BUFFER_SIZE_PARAM:
                            setInteger(BUFFER_SIZE_PARAM, currentElement, bufferSize);
                            break;
                        case THRESHOLD_PARAM:
                            setString(THRESHOLD_PARAM, currentElement, level);
                            break;
                        case IMMEDIATE_FLUSH_PARAM:
                            setBoolean(IMMEDIATE_FLUSH_PARAM, currentElement, immediateFlush);
                            break;
                    }
                    break;
            }
        });

        return createAppender(name, config, layout.get(), filter.get(), fileName.get(), level.get(),
                immediateFlush.get(), append.get(), bufferedIo.get(), bufferSize.get());
    }

    @Override
    public Appender parseAppender(final String name, final String appenderPrefix, final String layoutPrefix,
            final String filterPrefix, final Properties props, final PropertiesConfiguration configuration) {
        final Layout layout = configuration.parseLayout(layoutPrefix, name, props);
        final Filter filter = configuration.parseAppenderFilters(props, filterPrefix, name);
        final String level = getProperty(THRESHOLD_PARAM);
        final String fileName = getProperty(FILE_PARAM);
        final boolean append = getBooleanProperty(APPEND_PARAM, true);
        final boolean immediateFlush = getBooleanProperty(IMMEDIATE_FLUSH_PARAM, true);
        final boolean bufferedIo = getBooleanProperty(BUFFERED_IO_PARAM, false);
        final int bufferSize = Integer.parseInt(getProperty(BUFFER_SIZE_PARAM, "8192"));
        return createAppender(name, configuration, layout, filter, fileName, level, immediateFlush,
                append, bufferedIo, bufferSize);
    }

    private Appender createAppender(final String name, final Log4j1Configuration configuration, final Layout layout,
            final Filter filter, final String fileName, final String level, boolean immediateFlush, final boolean append,
            final boolean bufferedIo, final int bufferSize) {
        org.apache.logging.log4j.core.Layout<?> fileLayout = null;
        if (bufferedIo) {
            immediateFlush = false;
        }
        if (layout instanceof LayoutWrapper) {
            fileLayout = ((LayoutWrapper) layout).getLayout();
        } else if (layout != null) {
            fileLayout = new LayoutAdapter(layout);
        }
        final org.apache.logging.log4j.core.Filter fileFilter = buildFilters(level, filter);
        if (fileName == null) {
            LOGGER.warn("Unable to create File Appender, no file name provided");
            return null;
        }
        return new AppenderWrapper(FileAppender.newBuilder()
                .setName(name)
                .setConfiguration(configuration)
                .setLayout(fileLayout)
                .setFilter(fileFilter)
                .setFileName(fileName)
                .setImmediateFlush(immediateFlush)
                .setAppend(append)
                .setBufferedIo(bufferedIo)
                .setBufferSize(bufferSize)
                .build());
    }
}
