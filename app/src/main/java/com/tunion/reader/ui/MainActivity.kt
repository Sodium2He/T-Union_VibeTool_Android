package com.tunion.reader.ui

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.tunion.reader.R
import com.tunion.reader.model.CardInfo
import com.tunion.reader.model.TransactionRecord
import com.tunion.reader.model.TransitDatabase
import com.tunion.reader.model.TripRecord
import com.tunion.reader.nfc.CardReadException
import com.tunion.reader.nfc.TUnionCardReader
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private val cardReader = TUnionCardReader()

    // 当前读取结果
    private var currentResult: TUnionCardReader.ReadResult? = null

    // Views
    private lateinit var promptView: View
    private lateinit var resultView: View
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var errorText: TextView

    // 卡片信息 Views
    private lateinit var tvBalance: TextView
    private lateinit var tvCardNumber: TextView
    private lateinit var tvCityName: TextView
    private lateinit var tvCardType: TextView
    private lateinit var tvValidPeriod: TextView
    private lateinit var tvIssuerCode: TextView

    // Tab 和列表
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyList: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        TransitDatabase.init(this)
        initViews()
        initNfc()
    }

    private fun initViews() {
        promptView = findViewById(R.id.prompt_view)
        resultView = findViewById(R.id.result_view)
        loadingView = findViewById(R.id.loading_view)
        errorView = findViewById(R.id.error_view)
        errorText = findViewById(R.id.tv_error)

        tvBalance = findViewById(R.id.tv_balance)
        tvCardNumber = findViewById(R.id.tv_card_number)
        tvCityName = findViewById(R.id.tv_city_name)
        tvCardType = findViewById(R.id.tv_card_type)
        tvValidPeriod = findViewById(R.id.tv_valid_period)
        tvIssuerCode = findViewById(R.id.tv_issuer_code)

        tabLayout = findViewById(R.id.tab_layout)
        recyclerView = findViewById(R.id.recycler_view)
        tvEmptyList = findViewById(R.id.tv_empty_list)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Tab 切换
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateList(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // 点击错误界面重新扫描
        errorView.setOnClickListener {
            showPrompt()
        }

        showPrompt()
    }

    private fun initNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "此设备不支持 NFC", Toast.LENGTH_LONG).show()
            return
        }
        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "请先在系统设置中开启 NFC", Toast.LENGTH_LONG).show()
        }

        // 处理启动时的 Intent
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        enableNfcForeground()
    }

    override fun onPause() {
        super.onPause()
        disableNfcForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun enableNfcForeground() {
        val adapter = nfcAdapter ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun disableNfcForeground() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun handleIntent(intent: Intent) {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag != null) {
            readCard(tag)
        }
    }

    private fun readCard(tag: Tag) {
        showLoading()

        thread {
            try {
                val result = cardReader.readCard(tag)
                runOnUiThread {
                    currentResult = result
                    showResult(result)
                }
            } catch (e: CardReadException) {
                runOnUiThread {
                    showError(e.message ?: "读取失败")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("读取异常: ${e.message}")
                }
            }
        }
    }

    // ===================== UI 状态切换 =====================

    private fun showPrompt() {
        promptView.visibility = View.VISIBLE
        resultView.visibility = View.GONE
        loadingView.visibility = View.GONE
        errorView.visibility = View.GONE
    }

    private fun showLoading() {
        promptView.visibility = View.GONE
        resultView.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
    }

    private fun showError(message: String) {
        promptView.visibility = View.GONE
        resultView.visibility = View.GONE
        loadingView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun showResult(result: TUnionCardReader.ReadResult) {
        promptView.visibility = View.GONE
        resultView.visibility = View.VISIBLE
        loadingView.visibility = View.GONE
        errorView.visibility = View.GONE

        val info = result.cardInfo

        tvBalance.text = info.balanceDisplay
        tvCardNumber.text = info.cardNumber
        tvCityName.text = info.cityName
        tvCardType.text = info.cardTypeName

        val from = formatBcdDate(info.validFrom)
        val until = formatBcdDate(info.validUntil)
        tvValidPeriod.text = "$from ～ $until"

        tvIssuerCode.text = info.issuerCode

        // 默认显示交易记录
        tabLayout.getTabAt(0)?.select()
        updateList(0)
    }

    private fun updateList(tabIndex: Int) {
        val result = currentResult ?: return

        when (tabIndex) {
            0 -> { // 交易记录
                if (result.transactions.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    tvEmptyList.visibility = View.VISIBLE
                    tvEmptyList.text = "暂无交易记录"
                } else {
                    recyclerView.visibility = View.VISIBLE
                    tvEmptyList.visibility = View.GONE
                    recyclerView.adapter = TransactionAdapter(result.transactions)
                }
            }
            1 -> { // 行程记录
                if (result.trips.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    tvEmptyList.visibility = View.VISIBLE
                    tvEmptyList.text = "暂无行程记录"
                } else {
                    recyclerView.visibility = View.VISIBLE
                    tvEmptyList.visibility = View.GONE
                    recyclerView.adapter = TripAdapter(result.trips)
                }
            }
        }
    }

    /**
     * 将 BCD 日期（YYYYMMDD）格式化为 YYYY/MM/DD
     */
    private fun formatBcdDate(bcd: String): String {
        return if (bcd.length >= 8) {
            "${bcd.substring(0, 4)}/${bcd.substring(4, 6)}/${bcd.substring(6, 8)}"
        } else {
            bcd
        }
    }
}
