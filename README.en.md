# Echo(Distributed IP Proxy System)

Echo is a distributed system used for IP proxy sharing and management. The terminal of agent, running on multiple devices, are represented as long urls in Echo. The network resources of agents are managed by an IP cluster in Echo. Authentication, traffic monitoring, quota control  cluster are also fully supported in Echo.

## Why Echo

1. Echo is born for complex network environment. Agent of Echo can be easily deployed on multiple mobile devices(even on a Raspberry Pi).
2. For Agent IPs are managed in cluster mode by Echo, ADSI could be used as resource interface. Using ADSI as the only stable standard interface, Echo will no longer takes a heavy redis load.
3. SDK supported by Echo provides complete **Android APK** and **Gradle Library Dependence**.
4. Echo is distributed designed, working in cluster mode with no resource limit. All nodes in system work in **dual active hot standby mode** automatically, take no SPOF risk.
5. Echo is fully designed in NIO mode, which makes Echo take less resources and get better concurrent capacity.
6. Echo can be extended easily. The lower layer of Echo is designed to support multiple protocols(such as udp, tcp, vpn), while all protocols can be supported with no need of system upgrading. 
7. With the operation on terminal, you can execute any instruction on a specific terminal by HTTP interface(such as shell and **IP replay**).

## Download

| Source | Links                            |
| ------ | -------------------------------- |
| github | https://github.com/virjar/echo/  |
| gitee  | https://gitee.com/virjar/echo    |
| virjar | https://git.virjar.com/echo/echo |

## See also

To get more information, add wechat: virjar1 

| Name         | Description                                                  | Links                                  |
| ------------ | ------------------------------------------------------------ | -------------------------------------- |
| echo         | distributed cluster for agent IP sharing                     | https://git.virjar.com/echo/echo       |
| sekiro       | Android private API exposed system based long url and code injection | https://github.com/virjar/sekiro       |
| ratel        | Injection engine for Android applications' repackage.        | https://git.virjar.com/ratel/ratel-doc |
| zelda        | An implement of App multiboxing.                             | https://github.com/virjar/zelda        |
| geeEtacsufbo | The Extracting of "Geetest" Slider Captcha's JS Code:  **Anti-obfuscation of Control Flow Flattening JS Code**(The earliest practice of JS Control Flow's Anti-obfusecation by AST). | https://github.com/virjar              |
| thanos       | Java spiders management system (undeveloped).                | https://github.com/virjar/thanos       |

## Content

1. [Architecture](./doc/1.architecture.md)
2. [Quick Start](./doc/2.quick_start.md)
3. [Deploy Client on PC](./doc/3.jvm_installer.md)
4. [SDK Installing](./doc/4.sdk.md) 
5. [Deploy Backend Server](./doc/5.server_deploy.md)
6. [Deploy Backend Server by Docker](./doc/5.server_docker_deploy.md)

## Subitem

- [Front End of Echo](https://github.com/virjar/echo-fe)

- [Android Client](https://github.com/virjar/echo-android)

## Community

wechat:virjar1

> Remarking “echo入群“ when add above wechat account.

## Advertising

Get [Agent Cloud IP](http://i0k.cn/5ewVg) free right now!

Scanning following QR code to get more FREE agent IP！

![扫码领取ip](dailiyun_ad_free_proxy.jpg)