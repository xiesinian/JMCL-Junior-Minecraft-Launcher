# JMCL

JMCL 是一个基于 [Hello Minecraft! Launcher (HMCL)](https://github.com/HMCL-dev/HMCL) 修改的 Minecraft 启动器分支。

## 说明

- 本项目改编自 [Hello Minecraft! Launcher (HMCL)](https://github.com/HMCL-dev/HMCL)，并非 HMCL 官方版本。
- 用户可见的名称已改为 **JMCL**，并加入了一些彩蛋。
- 内部包名、配置目录（`.hmcl`）等保持不变，以兼容原版数据。

## 构建

需要 JDK 17 或更高版本。

```bash
# 编译
./gradlew assemble

# 运行（开发调试）
./gradlew :JMCL:run

# 生成可执行文件（exe / sh）
./gradlew :JMCL:makeExecutables
```

产物位于 `JMCL/build/libs/`。

> 首次构建需要从 Maven Central 等仓库下载依赖。国内网络较慢时，可以设置环境变量
> `MAVEN_CENTRAL_REPO=https://maven.aliyun.com/repository/public` 使用阿里云镜像。

## 许可证与署名

本项目遵循 **GNU General Public License v3**，完整协议见 [LICENSE](LICENSE)，协议全文：<https://www.gnu.org/licenses/gpl-3.0.html>。

- 本项目源码：<https://github.com/xiesinian/JMCL-Junior-Minecraft-Launcher>
- 原始项目：<https://github.com/HMCL-dev/HMCL>
- 原始版权：Copyright (C) 2021 huangyuhui \<huanghongxun2008@126.com\> and contributors
- 修改内容与合规声明见 [NOTICE](NOTICE)

根据 GPLv3，分发本项目的二进制文件时，必须同时提供对应的完整源代码。请不要删除源码文件中的原始版权声明。

## 免责声明

JMCL 与 Mojang Studios、Microsoft 没有任何关联，也未获得其认可或赞助。
"Minecraft" 是 Mojang Synergies AB 的商标。
