package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.data.model.Channel
import com.example.ui.player.PlayerActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TvBrowseFragment : BrowseSupportFragment() {

    private lateinit var rowsAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "M4DiTV"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = Color.parseColor("#0D0A1A")

        setupRows()
    }

    private fun setupRows() {
        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())
        rowsAdapter = ArrayObjectAdapter(presenterSelector)
        adapter = rowsAdapter

        val cardPresenter = CategoryCardPresenter()

        val app = requireActivity().application as M4DiTVApplication
        lifecycleScope.launch {
            combine(
                app.repository.favoriteChannels,
                app.database.channelDao().getAllChannelsFlow()
            ) { favorites, allChannels ->
                favorites to allChannels
            }.collectLatest { (favorites, allChannels) ->
                rowsAdapter.clear()

                if (favorites.isNotEmpty()) {
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    favorites.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(0, "Favorites")
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }

                val grouped = allChannels.groupBy { it.group }
                var index = 1L
                grouped.forEach { (groupName, channels) ->
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    channels.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(index++, groupName)
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }
            }
        }

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Channel) {
                val intent = Intent(requireActivity(), PlayerActivity::class.java).apply {
                    putExtra("channel_id", item.id)
                    putExtra("category_name", item.group)
                }
                startActivity(intent)
            }
        }
    }
}
