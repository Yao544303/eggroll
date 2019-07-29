package com.webank.ai.eggroll.framework.egg.computing.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.webank.ai.eggroll.core.constant.StringConstants;
import com.webank.ai.eggroll.core.model.ComputingEngine;
import com.webank.ai.eggroll.core.server.ServerConf;
import com.webank.ai.eggroll.core.utils.PropertyGetter;
import com.webank.ai.eggroll.core.utils.RuntimeUtils;
import com.webank.ai.eggroll.core.utils.impl.PriorityPropertyGetter;
import com.webank.ai.eggroll.framework.egg.computing.EngineOperator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProcessorEngineOperator implements EngineOperator {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String COMPUTING_ENGINE_NAME = "processor";
    private static final String VENV = "venv";
    private static final String PYTHON_PATH = "python-path";
    private static final String ENGINE_PATH = "engine-path";
    private static final String PORT = "port";
    private static final String DATA_DIR = "data-dir";
    private static final String LOGS_DIR = "logs-dir";
    private static final String BOOTSTRAP_SCRIPT = "bootstrap.script";
    private static final String START_PORT = "start.port";
    private static final String BASH = "/bin/bash";

    private static String[] scriptArgs = {VENV, PYTHON_PATH, ENGINE_PATH, PORT, DATA_DIR, LOGS_DIR};
    private static final String startCmdTemplate;

    static {
        String argTemplate = "${e}";
        String longOptTemplate = "--e";
        String e = "e";

        List<String> startCmdElement = Lists.newArrayList();
        startCmdElement.add(BASH);
        startCmdElement.add(argTemplate.replace(e, BOOTSTRAP_SCRIPT));

        for (String arg : scriptArgs) {
            startCmdElement.add(longOptTemplate.replace(e, arg));
            startCmdElement.add(argTemplate.replace(e, arg));
        }

        startCmdTemplate = String.join(StringConstants.SPACE, startCmdElement);
        System.out.println(startCmdTemplate);
    }

    @Autowired
    private ServerConf serverConf;
    @Autowired
    private RuntimeUtils runtimeUtils;
    @Autowired
    private PropertyGetter propertyGetter;

    private final String confPrefix;

    private String startScriptPath;
    private AtomicInteger lastPort;
    private int maxPort;


    public ProcessorEngineOperator() {
        confPrefix = String.join(StringConstants.DOT, StringConstants.EGGROLL,
                StringConstants.COMPUTING,
                COMPUTING_ENGINE_NAME,
                StringConstants.EMPTY);
    }

    @PostConstruct
    public void init() {
        String startPortString = propertyGetter.getProperty(confPrefix + START_PORT, "50000");
        lastPort = new AtomicInteger(Integer.valueOf(startPortString));
        maxPort = lastPort.get() + 5000;
    }

    @Override
    public ComputingEngine start(ComputingEngine computingEngine, Properties prop) {
        List<Properties> allSources = Lists.newArrayList();
        allSources.add(prop);
        allSources.addAll(propertyGetter.getAllSources());
        allSources.add(serverConf.getProperties());
        PriorityPropertyGetter priorityPropertyGetter = (PriorityPropertyGetter) propertyGetter;
        Map<String, String> valueBindingsMap = Maps.newHashMap();
        int port = -1;

        String bootStrapScript = priorityPropertyGetter.getPropertyInIterable(confPrefix + BOOTSTRAP_SCRIPT, allSources);
        valueBindingsMap.put(BOOTSTRAP_SCRIPT, bootStrapScript);
        for (String key : scriptArgs) {
            String actualValue = priorityPropertyGetter.getPropertyInIterable(confPrefix + key, allSources);
            if (StringUtils.isBlank(actualValue) && !key.equals(PORT)) {
                throw new IllegalArgumentException("key: " +
                        key + " is blank when starting session");
            }
            valueBindingsMap.put(key, actualValue);
        }

        Process engineProcess = null;

        try {
            while (engineProcess == null) {
                port = lastPort.getAndIncrement();
                if (runtimeUtils.isPortAvailable(port)) {
                    valueBindingsMap.put(PORT, String.valueOf(port));

                    String actualStartCmd = StringSubstitutor.replace(startCmdTemplate, valueBindingsMap);
                    LOGGER.info("[EGG][ENGINE][PROCESSOR] start cmd: {}", actualStartCmd);
                    engineProcess = Runtime.getRuntime().exec(actualStartCmd);
                    OutputStream stdout = engineProcess.getOutputStream();
                    InputStream stderr = engineProcess.getInputStream();

                    if (!engineProcess.isAlive()) {
                        throw new IllegalStateException("Processor engine dead: " + actualStartCmd);
                    }
                }
            }
        } catch (Throwable e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(e);
        }

        return new ComputingEngine(runtimeUtils.getMySiteLocalAddress(), port, computingEngine.getComputingEngineType(), engineProcess);
    }

    @Override
    public void stop(ComputingEngine computingEngine) {
        computingEngine.getProcess().destroy();
    }

    @Override
    public ComputingEngine stopForcibly(ComputingEngine computingEngine) {
        computingEngine.getProcess().destroyForcibly();
        return computingEngine;
    }

    @Override
    public boolean isAlive(ComputingEngine computingEngine) {
        return computingEngine.getProcess().isAlive();
    }
}
