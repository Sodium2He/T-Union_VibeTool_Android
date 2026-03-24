package com.tunion.reader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.tunion.reader.model.CardInfo
import com.tunion.reader.model.TransactionRecord
import com.tunion.reader.model.TripRecord

/**
 * T-Union（交通联合）卡片 NFC 读取器
 *
 * 协议参考：
 * - EMV 标准
 * - 交通运输部 JT/T 978 标准
 * - ISO/IEC 14443 Type 4A (IsoDep)
 * - https://github.com/SocialSisterYi/T-Union_Master/blob/main/docs/card_data_format.md
 */
class TUnionCardReader {

    companion object {
        private const val TAG = "TUnionCardReader"

        // PSE (Payment System Environment) DF 名称
        private val PSE_NAME = "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)

        // 常见交通联合 AID 前缀
        private val KNOWN_AIDS = listOf(
            "A000000632010105",  // 电子钱包
            "A000000632010106",  // 电子现金
        )

        // 状态字：成功
        private const val SW_OK_1 = 0x90.toByte()
        private const val SW_OK_2 = 0x00.toByte()
    }

    /**
     * 读取结果
     */
    data class ReadResult(
        val cardInfo: CardInfo,
        val transactions: List<TransactionRecord>,
        val trips: List<TripRecord>,
        val rawAid: String = "",
    )

    /**
     * 从 NFC Tag 读取交通联合卡信息
     */
    fun readCard(tag: Tag): ReadResult {
        val isoDep = IsoDep.get(tag)
            ?: throw CardReadException("此卡不支持 IsoDep 协议")

        isoDep.connect()
        isoDep.timeout = 5000 // 5秒超时

        try {
            // Step 1: 选择 PSE 目录
            val selectedAid = selectApplication(isoDep)
            Log.d(TAG, "已选择应用: $selectedAid")

            // Step 2: 读取余额
            val balance = readBalance(isoDep)
            Log.d(TAG, "余额: $balance 分")

            // Step 3: 读取公共应用信息 (SFI 0x15)
            val appInfo = readBinaryFile(isoDep, 0x15, 0x1E)
            Log.d(TAG, "公共应用信息: ${bytesToHex(appInfo)}")

            // Step 4: 读取持卡人信息 (SFI 0x16)
            val holderInfo = readBinaryFile(isoDep, 0x16, 0x37)

            // Step 5: 读取管理信息 (SFI 0x17)
            val mgmtInfo = readBinaryFile(isoDep, 0x17, 0x3C)

            // Step 6: 读取交易记录 (SFI 0x18, 最多10条)
            val transactions = readTransactionRecords(isoDep)

            // Step 7: 读取行程记录 (SFI 0x1E, 最多30条)
            val trips = readTripRecords(isoDep)

            // 解析卡片信息
            val cardInfo = parseCardInfo(balance, appInfo, holderInfo, mgmtInfo)

            return ReadResult(
                cardInfo = cardInfo,
                transactions = transactions,
                trips = trips,
                rawAid = selectedAid,
            )
        } finally {
            try { isoDep.close() } catch (_: Exception) {}
        }
    }

    /**
     * 选择应用（先 PSE 再 AID）
     */
    private fun selectApplication(isoDep: IsoDep): String {
        // 先尝试通过 PSE 选择
        try {
            val pseApdu = buildSelectApdu(PSE_NAME)
            val pseResp = isoDep.transceive(pseApdu)
            if (isSuccess(pseResp)) {
                // 解析 PSE 返回的 FCI，提取 AID
                val aid = parseAidFromFci(pseResp)
                if (aid != null) {
                    val selectResp = isoDep.transceive(buildSelectApdu(hexToBytes(aid)))
                    if (isSuccess(selectResp)) {
                        return aid
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PSE 选择失败，尝试直接 AID 选择: ${e.message}")
        }

        // 逐一尝试已知 AID
        for (aid in KNOWN_AIDS) {
            try {
                val resp = isoDep.transceive(buildSelectApdu(hexToBytes(aid)))
                if (isSuccess(resp)) {
                    return aid
                }
            } catch (e: Exception) {
                Log.w(TAG, "AID $aid 选择失败: ${e.message}")
            }
        }

        throw CardReadException("无法选择交通联合应用，此卡可能不是交通联合卡")
    }

    /**
     * 读取卡内余额
     * APDU: 80 5C 00 02 04
     */
    private fun readBalance(isoDep: IsoDep): Int {
        val apdu = byteArrayOf(
            0x80.toByte(), 0x5C, 0x00, 0x02, 0x04
        )
        val resp = isoDep.transceive(apdu)
        if (!isSuccess(resp) || resp.size < 6) {
            throw CardReadException("读取余额失败")
        }
        // 大端序 uint32，单位为分
        return ((resp[0].toInt() and 0xFF) shl 24) or
                ((resp[1].toInt() and 0xFF) shl 16) or
                ((resp[2].toInt() and 0xFF) shl 8) or
                (resp[3].toInt() and 0xFF)
    }

    /**
     * 读取二进制文件
     * APDU: 00 B0 [SFI|0x80] 00 [Le]
     */
    private fun readBinaryFile(isoDep: IsoDep, sfi: Int, length: Int): ByteArray {
        val p1 = 0x80 or (sfi and 0x1F)  // P1 高位=1 表示 SFI 模式, 低5位=SFI
        val apdu = byteArrayOf(
            0x00, 0xB0.toByte(), (p1 and 0xFF).toByte(), 0x00, (length and 0xFF).toByte()
        )
        val resp = isoDep.transceive(apdu)
        if (!isSuccess(resp)) {
            Log.w(TAG, "读取 SFI 0x${String.format("%02X", sfi)} 失败: SW=${getSW(resp)}")
            return ByteArray(length)  // 返回空数据
        }
        return resp.copyOfRange(0, resp.size - 2) // 去掉 SW
    }

    /**
     * 读取线性记录
     * APDU: 00 B2 [记录号] [(SFI<<3)|0x04] 00
     */
    private fun readRecord(isoDep: IsoDep, sfi: Int, recordNum: Int): ByteArray? {
        val p2 = (sfi shl 3) or 0x04
        val apdu = byteArrayOf(
            0x00, 0xB2.toByte(), (recordNum and 0xFF).toByte(),
            (p2 and 0xFF).toByte(), 0x00
        )
        val resp = isoDep.transceive(apdu)
        if (!isSuccess(resp) || resp.size <= 2) {
            return null
        }
        return resp.copyOfRange(0, resp.size - 2)
    }

    /**
     * 读取交易记录（SFI 0x18，最多10条?->255）
     */
    private fun readTransactionRecords(isoDep: IsoDep): List<TransactionRecord> {
        val records = mutableListOf<TransactionRecord>()
        for (i in 1..255) {
            try {
                val data = readRecord(isoDep, 0x18, i) ?: break
                if (data.size < 23) continue

                val record = TransactionRecord(
                    sequence = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF),
                    amount = ((data[5].toInt() and 0xFF) shl 24) or
                            ((data[6].toInt() and 0xFF) shl 16) or
                            ((data[7].toInt() and 0xFF) shl 8) or
                            (data[8].toInt() and 0xFF),
                    type = data[9].toInt() and 0xFF,
                    terminalId = bcdToString(data, 10, 6),
                    timestamp = bcdToString(data, 16, 7),
                )
                records.add(record)
            } catch (e: Exception) {
                Log.w(TAG, "读取交易记录 $i 失败: ${e.message}")
                break
            }
        }
        return records
    }

    /**
     * 读取行程记录（SFI 0x1E，最多30条?->255）
     */
    private fun readTripRecords(isoDep: IsoDep): List<TripRecord> {
        val records = mutableListOf<TripRecord>()
        for (i in 1..255) {
            try {
                val data = readRecord(isoDep, 0x1E, i) ?: break
                if (data.size < 0x2A) continue

                // 检查是否全零（空记录）
                if (data.all { it == 0.toByte() }) continue

                val record = TripRecord(
                    type = (data[0].toInt() and 0xFF),
                    terminalId = bcdToString(data, 1, 8),
                    auxType = (data[9].toInt() and 0xFF),
                    lineStation = bcdToString(data, 10, 7),
                    amount = ((data[0x11].toInt() and 0xFF) shl 24) or
                            ((data[0x12].toInt() and 0xFF) shl 16) or
                            ((data[0x13].toInt() and 0xFF) shl 8) or
                            (data[0x14].toInt() and 0xFF),
                    balance = ((data[0x15].toInt() and 0xFF) shl 24) or
                            ((data[0x16].toInt() and 0xFF) shl 16) or
                            ((data[0x17].toInt() and 0xFF) shl 8) or
                            (data[0x18].toInt() and 0xFF),
                    timestamp = bcdToString(data, 0x19, 7),
                    cityCode = bcdToString(data, 0x20, 2),
                    acquirer = bcdToString(data, 0x22, 8),
                )
                records.add(record)
            } catch (e: Exception) {
                Log.w(TAG, "读取行程记录 $i 失败: ${e.message}")
                break
            }
        }
        return records
    }

    /**
     * 解析卡片信息
     */
    private fun parseCardInfo(
        balance: Int,
        appInfo: ByteArray,
        holderInfo: ByteArray,
        mgmtInfo: ByteArray
    ): CardInfo {
        return CardInfo(
            balance = balance,
            issuerCode = if (appInfo.size >= 8) bytesToHex(appInfo.copyOfRange(0, 8)) else "",
            appType = if (appInfo.size >= 9) appInfo[8].toInt() and 0xFF else 0,
            appVersion = if (appInfo.size >= 10) appInfo[9].toInt() and 0xFF else 0,
            cardNumber = if (appInfo.size >= 20) bytesToHex(appInfo.copyOfRange(10, 20)) else "",
            validFrom = if (appInfo.size >= 24) bcdToString(appInfo, 20, 4) else "",
            validUntil = if (appInfo.size >= 28) bcdToString(appInfo, 24, 4) else "",
            cardType = if (holderInfo.isNotEmpty()) holderInfo[0].toInt() and 0xFF else 0,
            holderName = if (holderInfo.size >= 22) {
                try {
                    val nameBytes = holderInfo.copyOfRange(2, 22)
                    // 去掉末尾的 0x00 填充
                    val trimmed = nameBytes.takeWhile { it != 0.toByte() }.toByteArray()
                    String(trimmed, Charsets.UTF_8).trim()
                } catch (e: Exception) { "" }
            } else "",
            countryCode = if (mgmtInfo.size >= 4) bytesToHex(mgmtInfo.copyOfRange(0, 4)) else "",
            provinceCode = if (mgmtInfo.size >= 6) bytesToHex(mgmtInfo.copyOfRange(4, 6)) else "",
            cityCode = if (mgmtInfo.size >= 8) bcdToString(mgmtInfo, 6, 2) else "",
            interopType = if (mgmtInfo.size >= 10) bytesToHex(mgmtInfo.copyOfRange(8, 10)) else "",
        )
    }

    // ===================== APDU 工具方法 =====================

    /**
     * 构建 SELECT APDU: 00 A4 04 00 [Lc] [DF name]
     */
    private fun buildSelectApdu(name: ByteArray): ByteArray {
        val apdu = ByteArray(5 + name.size)
        apdu[0] = 0x00  // CLA
        apdu[1] = 0xA4.toByte() // INS: SELECT
        apdu[2] = 0x04  // P1: 按 DF 名选择
        apdu[3] = 0x00  // P2
        apdu[4] = name.size.toByte() // Lc
        System.arraycopy(name, 0, apdu, 5, name.size)
        return apdu
    }

    /**
     * 从 FCI (File Control Information) 中解析 AID
     * 简化 TLV 解析
     */
    private fun parseAidFromFci(fci: ByteArray): String? {
        try {
            val data = fci.copyOfRange(0, fci.size - 2) // 去掉 SW
            // 在 FCI 中搜索 tag 0x4F (AID)
            var i = 0
            while (i < data.size - 2) {
                val tag = data[i].toInt() and 0xFF
                i++
                if (i >= data.size) break
                val len = data[i].toInt() and 0xFF
                i++
                if (tag == 0x4F && i + len <= data.size) {
                    return bytesToHex(data.copyOfRange(i, i + len))
                }
                // 如果是构造类 tag（高位 bit5=1），进入内部继续搜索
                if (tag and 0x20 != 0) {
                    // 不跳过，继续在内部搜索
                    continue
                }
                i += len
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析 FCI 失败: ${e.message}")
        }
        return null
    }

    /**
     * 判断响应是否成功（SW = 9000）
     */
    private fun isSuccess(resp: ByteArray): Boolean {
        return resp.size >= 2 &&
                resp[resp.size - 2] == SW_OK_1 &&
                resp[resp.size - 1] == SW_OK_2
    }

    /**
     * 获取状态字
     */
    private fun getSW(resp: ByteArray): String {
        return if (resp.size >= 2) {
            String.format("%02X%02X", resp[resp.size - 2], resp[resp.size - 1])
        } else "????"
    }

    // ===================== 数据格式转换 =====================

    /**
     * BCD 编码转字符串
     */
    private fun bcdToString(data: ByteArray, offset: Int, length: Int): String {
        val sb = StringBuilder()
        for (i in offset until minOf(offset + length, data.size)) {
            sb.append(String.format("%02X", data[i]))
        }
        return sb.toString()
    }

    /**
     * 字节数组转十六进制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { String.format("%02X", it) }
    }

    /**
     * 十六进制字符串转字节数组
     */
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}

/**
 * 读卡异常
 */
class CardReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
