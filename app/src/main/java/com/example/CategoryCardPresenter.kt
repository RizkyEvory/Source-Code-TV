package com.example

import android.graphics.Color
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.data.model.Channel

class CategoryCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.parseColor("#1A1530"))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val cardView = viewHolder.view as ImageCardView
        cardView.titleText = channel.name
        cardView.contentText = channel.group
        cardView.setMainImageDimensions(160, 120)

        val imageLoader = ImageLoader(viewHolder.view.context)
        val request = ImageRequest.Builder(viewHolder.view.context)
            .data(channel.logoUrl ?: "https://via.placeholder.com/150")
            .target(
                onSuccess = { result ->
                    cardView.mainImage = result
                },
                onError = {
                    // Fallback
                }
            )
            .build()
        imageLoader.enqueue(request)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }
}
