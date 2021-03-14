package com.virjar.echo.server.common.auth;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author virjar
 * @since 2020-07-27
 */
public class IpTrie {
    private IpTrie left;
    private IpTrie right;
    private Set<AuthenticateAccountInfo> accountList = new TreeSet<>();


    private static final String localhostStr = "localhost";
    private static final String localhost = "127.0.0.1";

    public IpTrie copy() {
        IpTrie other = new IpTrie();
        other.accountList = new TreeSet<>(accountList);
        if (left != null) {
            left = left.copy();
        }
        if (right != null) {
            right = right.copy();
        }
        return other;
    }

    public void insert(String ipConfig, AuthenticateAccountInfo account) {
        String ip;
        int cidr = 32;
        if (ipConfig.contains("/")) {
            String[] split = ipConfig.split("/");
            ip = StringUtils.trim(split[0]);
            cidr = Integer.parseInt(StringUtils.trim(split[1]));
        } else {
            ip = ipConfig.trim();
        }
        insert(0, ip2Int(ip), cidr, account);
    }

    private void insert(int deep, long ip, int cidr, AuthenticateAccountInfo account) {
        if (deep >= cidr) {
            synchronized (this) {
                accountList.add(account);
            }
            return;
        }

        int bit = (int) ((ip >>> (32 - deep)) & 0x01);
        if (bit == 0) {
            if (left == null) {
                left = new IpTrie();
            }
            left.insert(deep + 1, ip, cidr, account);
        } else {
            if (right == null) {
                right = new IpTrie();
            }
            right.insert(deep + 1, ip, cidr, account);
        }
    }

    private static long ip2Int(String ip) {
        if (localhostStr.equals(ip)) {
            ip = localhost;
        }
        String[] split = ip.split("\\.");
        return ((Long.parseLong(split[0]) << 24
                | Long.parseLong(split[1]) << 16
                | Long.parseLong(split[2]) << 8
                | Long.parseLong(split[3]) << 8));
    }

    public AuthenticateAccountInfo find(String ip) {
        Set<AuthenticateAccountInfo> strings = find(ip, null);
        if (strings == null) {
            return null;
        }
        if (strings.isEmpty()) {
            return null;
        }
        return strings.iterator().next();
    }

    public Set<AuthenticateAccountInfo> find(String ip, AuthenticateAccountInfo account) {
        return find(ip2Int(ip), 0, account);
    }


    private Set<AuthenticateAccountInfo> find(long ip, int deep, AuthenticateAccountInfo account) {
        if (account != null && accountList.contains(account)) {
            return Sets.newHashSet(account);
        }
        if (accountList.size() > 0) {
            return Collections.unmodifiableSet(accountList);
        }
        int bit = (int) ((ip >>> (32 - deep)) & 0x01);
        if (bit == 0) {
            if (left == null) {
                return null;
            }
            return left.find(ip, deep + 1, account);
        } else {
            if (right == null) {
                return null;
            }
            return right.find(ip, deep + 1, account);
        }
    }

    public void remove(String ipConfig, AuthenticateAccountInfo account) {
        String ip;
        int cidr = 32;
        if (ipConfig.contains("/")) {
            String[] split = ipConfig.split("/");
            cidr = Integer.parseInt(StringUtils.trim(split[1]));
            ip = StringUtils.trim(split[0]);
        } else {
            ip = ipConfig.trim();
        }
        remove(0, ip2Int(ip), cidr, account);
    }

    private void remove(int deep, long ip, int cidr, AuthenticateAccountInfo account) {
        if (deep >= cidr) {
            synchronized (this) {
                accountList.remove(account);
            }
            return;
        }

        int bit = (int) ((ip >>> (32 - deep)) & 0x01);
        if (bit == 0) {
            if (left == null) {
                return;
            }
            left.remove(deep + 1, ip, cidr, account);
        } else {
            if (right == null) {
                return;
            }
            right.remove(deep + 1, ip, cidr, account);
        }
    }

    public int remove4Account(AuthenticateAccountInfo account) {
        int nodeSize = 0;
        if (left != null) {
            int i = left.remove4Account(account);
            if (i == 0) {
                left = null;
            }
            nodeSize += i;
        }
        if (right != null) {
            int i = right.remove4Account(account);
            nodeSize += i;
            if (i == 0) {
                right = null;
            }
        }
        synchronized (this) {
            accountList.remove(account);
        }
        return nodeSize + accountList.size();
    }


    public static void main(String[] args) {
        AuthenticateAccountInfo authenticateAccountInfo = new AuthenticateAccountInfo();
        authenticateAccountInfo.setAdmin(true);
        authenticateAccountInfo.setMaxQps(10);
        IpTrie ipTrie = new IpTrie();
        ipTrie.insert("192.168.1.1/24", authenticateAccountInfo);
        System.out.println(ipTrie.find("192.168.1.1"));
        System.out.println(ipTrie.find("192.168.2.34"));

        System.out.println();
        ipTrie.insert("192.168.1.1/16", authenticateAccountInfo);
        System.out.println(ipTrie.find("192.168.1.1"));
        System.out.println(ipTrie.find("192.168.2.34"));

        System.out.println();
        ipTrie.remove("192.168.1.1/16", authenticateAccountInfo);
        System.out.println(ipTrie.find("192.168.1.1"));
        System.out.println(ipTrie.find("192.168.2.36"));


        System.out.println();
        ipTrie.insert("192.168.1.1/16", authenticateAccountInfo);
        System.out.println(ipTrie.find("192.168.1.1"));
        System.out.println(ipTrie.find("192.168.2.35"));
        ipTrie.remove4Account(authenticateAccountInfo);
        System.out.println(ipTrie.find("192.168.1.1"));
        System.out.println(ipTrie.find("192.168.2.34"));

    }
}
