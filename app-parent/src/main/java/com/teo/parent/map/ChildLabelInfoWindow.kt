package com.teo.parent.map

import android.widget.TextView
import com.teo.parent.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

/** Plain rounded label instead of osmdroid's default gray speech-bubble callout. */
class ChildLabelInfoWindow(mapView: MapView) : InfoWindow(R.layout.marker_info_window, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        mView.findViewById<TextView>(R.id.marker_label_text).text = marker.title
    }

    override fun onClose() = Unit
}
