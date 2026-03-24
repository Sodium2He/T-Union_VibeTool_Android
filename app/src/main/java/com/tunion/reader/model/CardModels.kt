package com.tunion.reader.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * 交通联合卡基础信息
 */
data class CardInfo(
    val balance: Int = 0,              // 余额（单位：分）
    val cardNumber: String = "",       // 卡号（应用序列号）
    val issuerCode: String = "",       // 发卡机构标识
    val appType: Int = 0,              // 应用类型标识
    val appVersion: Int = 0,           // 应用版本
    val validFrom: String = "",        // 生效日期
    val validUntil: String = "",       // 失效日期
    val cityCode: String = "",         // 城市代码
    val cardType: Int = 0,             // 卡种类型
    val interopType: String = "",      // 互通卡种
    val provinceCode: String = "",     // 省级代码
    val countryCode: String = "",      // 国际代码
    val holderName: String = "",       // 持卡人姓名
    val holderIdType: Int = 0,         // 证件类型
    val holderIdNumber: String = "",   // 证件号码
) {
    /** 余额（元） */
    val balanceYuan: Double get() = balance / 100.0

    /** 格式化余额显示 */
    val balanceDisplay: String get() = String.format("¥%.2f", balanceYuan)

    /** 卡种名称 */
    val cardTypeName: String
        get() = when (cardType) {
            0x01 -> "标准卡"
            0x02 -> "老年卡"
            0x03 -> "学生卡"
            0x04 -> "员工卡"
            0x05 -> "残疾卡"
            0x06 -> "拥军卡"
            else -> "其他 (0x${String.format("%02X", cardType)})"
        }

    /** 城市名称映射 */
    val cityName: String get() = TransitDatabase.getCityName(cityCode)
}

/**
 * 交易记录
 */
data class TransactionRecord(
    val sequence: Int = 0,         // 交易序号
    val amount: Int = 0,           // 交易金额（单位：分）
    val type: Int = 0,             // 交易类型
    val terminalId: String = "",   // 终端号
    val timestamp: String = "",    // 时间戳 YYYYMMDDhhmmss
) {
    /** 金额（元） */
    val amountYuan: Double get() = amount / 100.0

    /** 格式化金额显示 */
    val amountDisplay: String get() = String.format("¥%.2f", amountYuan)

    /** 交易类型名称 */
    val typeName: String
        get() = when (type) {
            0x02 -> "充值"
            0x06 -> "消费"
            0x09 -> "复合消费"
            else -> "未知 (0x${String.format("%02X", type)})"
        }

    /** 格式化时间显示 */
    val timeDisplay: String
        get() {
            return try {
                if (timestamp.length >= 14) {
                    val y = timestamp.substring(0, 4)
                    val m = timestamp.substring(4, 6)
                    val d = timestamp.substring(6, 8)
                    val h = timestamp.substring(8, 10)
                    val min = timestamp.substring(10, 12)
                    val s = timestamp.substring(12, 14)
                    "$y-$m-$d $h:$min:$s"
                } else {
                    timestamp
                }
            } catch (e: Exception) {
                timestamp
            }
        }

    /** 简短时间（月日时分） */
    val timeShort: String
        get() {
            return try {
                if (timestamp.length >= 12) {
                    val m = timestamp.substring(4, 6)
                    val d = timestamp.substring(6, 8)
                    val h = timestamp.substring(8, 10)
                    val min = timestamp.substring(10, 12)
                    "$m/$d $h:$min"
                } else {
                    timestamp
                }
            } catch (e: Exception) {
                timestamp
            }
        }
}

/**
 * 行程记录
 */
data class TripRecord(
    val type: Int = 0,             // 交易类型
    val terminalId: String = "",   // 终端号
    val auxType: Int = 0,          // 辅助类型（地铁/公交）
    val lineStation: String = "",  // 线路和站点
    val amount: Int = 0,           // 交易金额（分）
    val balance: Int = 0,          // 余额（分）
    val timestamp: String = "",    // 时间戳
    val cityCode: String = "",     // 城市代码
    val acquirer: String = "",     // 收单机构
) {
    /** 交易类型名称 */
    val typeName: String
        get() = when (type) {
            0x02 -> "单次"
            0x03 -> "进站"
            0x04 -> "出站"
            0x06 -> "单次"
            else -> "未知"
        }

    /** 交通类型名称 */
    val auxTypeName: String
        get() = when (auxType) {
            0x01 -> "地铁"
            0x02 -> "公交"
            else -> "其他"
        }

    /** 金额（元） */
    val amountYuan: Double get() = amount / 100.0

    /** 余额（元） */
    val balanceYuan: Double get() = balance / 100.0

    /** 格式化时间 */
    val timeDisplay: String
        get() {
            return try {
                if (timestamp.length >= 12) {
                    val m = timestamp.substring(4, 6)
                    val d = timestamp.substring(6, 8)
                    val h = timestamp.substring(8, 10)
                    val min = timestamp.substring(10, 12)
                    "$m/$d $h:$min"
                } else {
                    timestamp
                }
            } catch (e: Exception) {
                timestamp
            }
        }

    /** 城市名 */
    val cityName: String get() = TransitDatabase.getCityName(cityCode)

    /** 站台查询结果（含线路名、站台名） */
    val stationResult: TransitDatabase.StationResult
        get() = TransitDatabase.resolveStation(cityCode, lineStation)
}

