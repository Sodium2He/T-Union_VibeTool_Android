package com.tunion.reader.model

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 交通联合线路站台数据库
 * 数据来源: T-Union_Master (https://github.com/SocialSisterYi/T-Union_Master)
 *
 * 数据文件:
 * - assets/city_codes.json   城市代码 -> 城市名 (351 cities, GB/T 13497-1992)
 * - assets/stations.json     站台完整ID(10 chars) -> 站台名 (5508 stations)
 * - assets/lines.json        线路前缀(8 chars) -> {city, name, type}  (273 lines)
 */
object TransitDatabase {

    private const val TAG = "TransitDatabase"

    private var cityMap: Map<String, String> = emptyMap()      // GBT 13497: "3100" -> "上海市" (用于站台匹配)
    private var unionPayMap: Map<String, String> = emptyMap()  // 银联地区码: "2900" -> "上海市" (卡片存储)
    private var stationMap: Map<String, String> = emptyMap()    // "1000010001" -> "苹果园"
    private var lineMap: Map<String, LineInfo> = emptyMap()     // "10000100" -> LineInfo
    private var initialized = false

    data class LineInfo(
        val city: String,
        val name: String,
        val type: String,  // metro, brt, tram, train, bus
    ) {
        val typeName: String
            get() = when (type) {
                "metro" -> "地铁"
                "brt" -> "快速公交"
                "tram" -> "有轨电车"
                "train" -> "铁路"
                "bus" -> "公交"
                else -> type
            }
    }

    /**
     * 站台查询结果
     */
    data class StationResult(
        val stationName: String?,
        val lineName: String?,
        val lineType: String?,
        val cityName: String?,
    ) {
        val displayText: String
            get() = buildString {
                if (!lineName.isNullOrBlank()) append("$lineName ")
                if (!stationName.isNullOrBlank()) append(stationName)
                else append("未知站台")
            }

        val found: Boolean get() = stationName != null || lineName != null
    }

    /**
     * 从 assets 加载数据库 (应在 Application 或 MainActivity 初始化时调用)
     */
    fun init(context: Context) {
        if (initialized) return
        val startTime = System.currentTimeMillis()

        try {
            cityMap = loadSimpleMap(context, "city_codes.json")
            unionPayMap = loadSimpleMap(context, "unionpay_codes.json")
            stationMap = loadSimpleMap(context, "stations.json")
            lineMap = loadLineMap(context, "lines.json")
            initialized = true

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Database loaded: ${cityMap.size} GBT cities, " +
                    "${unionPayMap.size} UnionPay codes, " +
                    "${stationMap.size} stations, ${lineMap.size} lines (${elapsed}ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load database", e)
        }
    }

    /**
     * 查询城市名称
     */
    fun getCityName(code: String): String {
        // 优先查银联地区码（卡片管理信息使用此编码）
        unionPayMap[code]?.let { return it }
        // 再查 GBT 13497（站台/线路 ID 使用此编码）
        cityMap[code]?.let { return it }
        // 尝试去掉末尾的 0 再查
        if (code.length == 4) {
            val trimmed = code.trimEnd('0').padEnd(4, '0')
            unionPayMap[trimmed]?.let { return it }
            cityMap[trimmed]?.let { return it }
        }
        return "未知城市($code)"
    }

    /**
     * 查询站台信息
     * @param cityCode 城市代码 (4 chars BCD, 来自行程记录 offset 0x20)
     * @param lineStation 线路站台字段 (14 chars BCD, 来自行程记录 offset 0x0A)
     */
    fun resolveStation(cityCode: String, lineStation: String): StationResult {
        if (!initialized) {
            return StationResult(null, null, null, getCityName(cityCode))
        }

        val city = getCityName(cityCode)

        // 策略1: 直接用 lineStation 前 10 位查站台
        val directStation = stationMap[lineStation.take(10)]

        // 策略2: cityCode + lineStation 前 6 位 = 10 位站台ID
        val composedId = cityCode + lineStation.take(6)
        val composedStation = stationMap[composedId]

        // 策略3: 查线路 - 尝试多种前缀长度
        var lineInfo: LineInfo? = null
        // 先尝试完整 8 位
        lineInfo = lineMap[lineStation.take(8)]
        // 再尝试 cityCode + lineStation 前 4 位
        if (lineInfo == null) {
            lineInfo = lineMap[cityCode + lineStation.take(4)]
        }

        // 策略4: 如果都没找到，暴力搜索匹配 cityCode 开头的站台
        val stationName = directStation ?: composedStation ?: run {
            // 尝试在 stationMap 中找以 cityCode 开头、且后缀匹配 lineStation 某部分的条目
            val candidates = stationMap.keys.filter { it.startsWith(cityCode) }
            val ls6 = lineStation.take(6)
            candidates.firstOrNull { it.endsWith(ls6.takeLast(2)) && it.contains(ls6.take(4)) }
                ?.let { stationMap[it] }
        }

        return StationResult(
            stationName = stationName,
            lineName = lineInfo?.name,
            lineType = lineInfo?.typeName,
            cityName = city,
        )
    }

    // ---- 内部加载方法 ----

    private fun loadSimpleMap(context: Context, fileName: String): Map<String, String> {
        val jsonStr = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val json = JSONObject(jsonStr)
        val map = HashMap<String, String>(json.length())
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = json.getString(k)
        }
        return map
    }

    private fun loadLineMap(context: Context, fileName: String): Map<String, LineInfo> {
        val jsonStr = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val json = JSONObject(jsonStr)
        val map = HashMap<String, LineInfo>(json.length())
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val obj = json.getJSONObject(k)
            map[k] = LineInfo(
                city = obj.getString("city"),
                name = obj.getString("name"),
                type = obj.getString("type"),
            )
        }
        return map
    }
}
