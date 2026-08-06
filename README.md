# Workbench Extensions

Workbench 的公开扩展发行仓库。

本仓库只托管经过验证的扩展目录、校验信息和二进制扩展包，不包含 `workbench-platform` 私有源码，也不表示扩展源代码采用开源许可证。

## 客户端入口

Windows 客户端通过内置“扩展下载管理器”读取：

`https://raw.githubusercontent.com/Zhanfg/workbench-extensions/main/registry/catalog-v1.json`

普通用户无需手动下载或解压 `.wpack`。

## 当前扩展

- `org.workbench.novel`：小说工具。一个插件内含章节结构、术语一致性、翻译提示词、译文质量检查和出版导出，五项功能在插件设置中独立开关。

## 安全边界

客户端安装前必须核对目录声明、文件大小、SHA-256、ZIP/CRC32 与包内 `integrity.json`。公开托管仓库不包含构建密钥和签名私钥。

## 仓库性质

- 公开二进制发行仓库
- 非源码仓库
- 不接受在此仓库直接提交插件实现代码
