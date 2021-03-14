package com.virjar.echo.hproxy.service;

import com.google.common.base.Preconditions;
import com.virjar.echo.server.common.ConstantHashUtil;
import com.virjar.echo.server.common.PortSpaceMappingConfigParser;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分配代理端口资源
 */
@Slf4j
public class ProxyPortAllocator {
    private final String portSpace;
    private Slot[] slots;

    private int totalSize;
    private AtomicInteger allocatedSize;
    /**
     * 当遇到哈希冲突，最多使用相邻10个端口资源
     */
    private static final int MAX_REUSE_HASH_SLOTS = 10;

    public ProxyPortAllocator(String portSpace) {
        Preconditions.checkNotNull(portSpace, "need portSpace parameter");
        this.portSpace = portSpace;
        parseAndInit();
    }

    /**
     * 归还端口资源，当设备下线的时候，会想设备占用的端口资源释放。之后可以给其他设备使用该资源
     *
     * @param clientId clientId
     */
    public void returnResource(String clientId) {
        clientId = clientId.trim();
        String finalClientId = clientId;
        iteratorSlot(clientId, MAX_REUSE_HASH_SLOTS + 1,
                slot -> {
                    if (finalClientId.equals(slot.occupyClient)) {
                        if (slot.used.compareAndSet(true, false)) {
                            allocatedSize.decrementAndGet();
                        }
                        return true;
                    }
                    return false;
                }

        );
    }

    /**
     * 分配一个端口资源，代理服务会在对应的端口上面提供。<br>
     * 分配算法会尽量保证同一个设备多次使用绑定在相同的端口资源上面，这样是为了让客户使用不被迷惑（同一个手机资源每次出口唯一）。<br>
     * 另一方面，如果客户通过定时任务同步代理配置,那么客户端断开重连之后可以在配置同步前自动恢复。一定程序可以减少代理失败率
     *
     * @param clientId 客户端唯一id，分配算法会尽量保证同一个设备多次使用绑定在相同的端口资源上面<br>
     *                 如果无法保证，那么分配算法尽量选择空余资源。
     * @return 端口资源，如果分配中失败，返回-1
     */
    public int allocateOne(String clientId) {
        if (allocatedSize.get() > totalSize) {
            log.error("not enough port resource total:{} used:{}", totalSize, allocatedSize);
            return -1;
        }
        Slot slot = iteratorSlot(clientId, MAX_REUSE_HASH_SLOTS, slotIt -> slotIt.used.compareAndSet(false, true));
        if (slot == null) {
            return -1;
        }
        slot.occupyClient = clientId;
        allocatedSize.incrementAndGet();
        return slot.port;
    }

    private Slot iteratorSlot(String clientId, int n, SlotIterator slotIterator) {
        clientId = clientId.trim();
        long hash = Math.abs(ConstantHashUtil.murHash(clientId));
        int index = (int) (hash % totalSize);
        for (int i = 0; i < n; i++) {
            //重试10次，如果都被占用了，那么就不分配了
            Slot slot = slots[index];
            if (slotIterator.it(slot)) {
                return slot;
            }
            index++;
            if (index >= totalSize) {
                // think the slots array as a ring, so need reset to begin of the slots array
                index = 0;
            }
        }
        return null;
    }

    private interface SlotIterator {
        boolean it(Slot slot);
    }

    private static class Slot {
        Slot(int port) {
            this.port = port;
            used = new AtomicBoolean(false);
        }

        private final int port;
        private String occupyClient;
        private final AtomicBoolean used;

    }

    private void parseAndInit() {
        Set<Integer> duplicateRemoveConfig = PortSpaceMappingConfigParser.parseConfig(portSpace);
        if (duplicateRemoveConfig.size() < 10) {
            throw new IllegalStateException("端口资源配置过少..无法开启代理服务!!");
        }
        slots = new Slot[duplicateRemoveConfig.size()];
        int i = 0;
        for (Integer port : duplicateRemoveConfig) {
            slots[i++] = new Slot(port);
        }

        totalSize = slots.length;
        allocatedSize = new AtomicInteger(0);
    }


}
