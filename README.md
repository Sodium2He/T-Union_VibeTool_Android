# 交通卡助手 (T-Union Reader)

🚇💳 基于 Android NFC 的交通联合卡余额及记录查询工具。

本项目参考/使用了 [T-Union_Master](https://github.com/SocialSisterYi/T-Union_Master) 中的许多代码及资料文件。

## 功能概述

本应用利用 Android 手机内建 NFC 功能，读取符合**交通联合（T-Union）**标准的交通卡信息

⚠️ **请注意：由于代码逻辑问题，暂未实现透支额度的识别，因此读出余额信息均为包含透支额度的余额，并不（不完全）等于可用余额；有关透支额度的可用情况按城市而定。**

### 声明

本项目仅供学习研究用途。所有 NFC 通讯逻辑、APDU 指令序列及卡片数据解析均基于公开标准、资料实现；

本应用对卡片执行只读操作，不会写入、修改、复制或模拟任何卡片数据。请务必遵守所在国家/地区的法律法规，请勿将本软件用于任何违法或未经授权的用途。

> 本项目几乎全部由 AI 完成，且本人暂无足够时间精力维护，因此关闭 issue / pull request 功能；如有需要请自行 fork 修改。This repository does not accept issues or pull requests. It is published as-is for reference only.

### 支持查询的内容

| 分类 | 信息 |
|------|------|
| 基础信息 | 余额、卡号、发卡城市、卡种类型 |
| 有效期 | 生效日期、失效日期 |
| 发卡信息 | 发卡机构代码、应用类型、应用版本 |
| 交易记录 | 最近 10 笔交易（充值/消费/复合消费） |
| 行程记录 | 最近 30 条行程（进站/出站/换乘/地铁/公交） |

### 技术特点

- **纯离线查询**：无需网络，不上传任何数据
- **NFC 前台调度**：打开 App 后直接贴卡即读，无需额外操作
- **协议标准**：基于 EMV 标准 + 交通运输部 JT/T 978 标准
- **通讯协议**：ISO/IEC 14443 Type 4A (IsoDep)

## 技术原理

### 通讯流程

```
1. SELECT PSE (2PAY.SYS.DDF01) 或直接 SELECT AID
2. GET BALANCE  (CLA=80 INS=5C P1=00 P2=02 Le=04)
3. READ BINARY  SFI=0x15  公共应用信息
4. READ BINARY  SFI=0x16  持卡人信息
5. READ BINARY  SFI=0x17  管理信息（含城市代码）
6. READ RECORD  SFI=0x18  交易记录 ×10
7. READ RECORD  SFI=0x1E  行程记录 ×30
```

### 关键 APDU 指令

| 功能 | APDU |
|------|------|
| 选择应用 | `00 A4 04 00 [Lc] [AID]` |
| 读取余额 | `80 5C 00 02 04` |
| 读取二进制文件 | `00 B0 [SFI\|0x80] 00 [Le]` |
| 读取线性记录 | `00 B2 [记录号] [(SFI<<3)\|0x04] 00` |

### 项目结构

```
app/src/main/java/com/tunion/reader/
├── model/
│   └── CardModels.kt        # 数据模型（CardInfo、TransactionRecord、TripRecord）
├── nfc/
│   └── TUnionCardReader.kt   # 核心 NFC 读卡逻辑（APDU 通讯与数据解析）
└── ui/
    ├── MainActivity.kt        # 主界面（NFC 前台调度、状态管理）
    └── Adapters.kt            # RecyclerView 适配器
```

## 运行要求

- Android 7.0 (API 24) 以上
- 设备需具备 NFC 硬件
- 需在系统设置中开启 NFC

## 构建方式

1. 使用 Android Studio (Hedgehog 2023.1+) 打开项目根目录
2. 等待 Gradle Sync 完成
3. 连接具有 NFC 功能的 Android 设备
4. 点击 Run 即可安装运行

## 使用方式

1. 打开 App，看到「请将交通联合卡贴近手机背面」提示
2. 将交通联合卡贴在手机 NFC 感应区（通常在背面上方）
3. 等待读取完成，即可查看余额、交易记录、行程记录
4. 再次贴卡可重新读取

## 兼容性说明

本应用支持所有符合**交通联合（T-Union）**标准的卡片，覆盖全国 300+ 城市的公交、地铁卡。已内置常见城市代码数据库，可显示发卡城市名称。

> ⚠️ 部分城市的交通卡可能使用非交通联合标准（如北京一卡通 BMAC、深圳通等），这些卡需要不同的协议处理，本版本暂不支持。

## 协议参考

- [T-Union_Master 卡片协议分析](https://github.com/SocialSisterYi/T-Union_Master/blob/main/docs/card_data_format.md)
- [NFC Wiki - 交通联合卡](https://wiki.nfc.im/books/智能卡手册/page/交通联合卡（t-union）)
- EMV 标准 / JT/T 978 标准
- [Trip Reader 读卡识途](https://www.domosekai.com/reader/index.html)
