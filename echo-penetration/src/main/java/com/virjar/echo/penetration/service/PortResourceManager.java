package com.virjar.echo.penetration.service;

import com.google.common.base.Preconditions;
import com.virjar.echo.server.common.PortSpaceMappingConfigParser;

import java.util.concurrent.LinkedBlockingQueue;

public class PortResourceManager {

    private final LinkedBlockingQueue<Integer> resources = new LinkedBlockingQueue<>();

    /**
     * @param portSpace like "20000-25000"
     */
    public PortResourceManager(String portSpace) {
        Preconditions.checkNotNull(portSpace, "need portSpace parameter");
        resources.addAll(PortSpaceMappingConfigParser.parseConfig(portSpace));
    }

    public Integer allocate() {
        return resources.poll();
    }

    public void returnPort(Integer port) {
        resources.offer(port);
    }

}
