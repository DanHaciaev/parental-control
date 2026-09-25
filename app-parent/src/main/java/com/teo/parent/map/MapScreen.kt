package com.teo.parent.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.teo.parent.R
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasCenteredOnFirstFix by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(15.0)
                    mapView = this
                }
            },
            update = { view ->
                val point = uiState.location ?: return@AndroidView
                val geoPoint = GeoPoint(point.lat, point.lng)
                view.overlays.clear()
                val marker = Marker(view).apply {
                    position = geoPoint
                    icon = ContextCompat.getDrawable(view.context, R.drawable.marker_child_avatar)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = uiState.childName ?: "Ребёнок"
                    infoWindow = ChildLabelInfoWindow(view)
                }
                view.overlays.add(marker)
                if (!hasCenteredOnFirstFix) {
                    hasCenteredOnFirstFix = true
                    view.controller.setZoom(17.0)
                }
                view.controller.setCenter(geoPoint)
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            uiState.location == null -> Text(
                text = "Пока нет данных о местоположении",
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            )
        }

        BatteryBadge(
            status = uiState.deviceStatus,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        ZoomControls(
            onZoomIn = { mapView?.controller?.zoomIn() },
            onZoomOut = { mapView?.controller?.zoomOut() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ZoomControls(onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZoomButton(label = "+", onClick = onZoomIn)
        ZoomButton(label = "–", onClick = onZoomOut)
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.size(44.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

