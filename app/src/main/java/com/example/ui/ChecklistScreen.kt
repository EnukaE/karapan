package com.example.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(viewModel: ChecklistViewModel, modifier: Modifier = Modifier) {
    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    val currentItems by viewModel.currentItems.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedChecklistId.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("කරපන් (Karapan)") }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "") } }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedId == null) {
                LazyColumn {
                    items(checklists) { cl ->
                        ListItem(headlineContent = { Text(cl.name) }, modifier = Modifier.clickable { viewModel.selectedChecklistId.value = cl.id })
                    }
                }
            } else {
                val currentCl = checklists.find { it.id == selectedId }
                IconButton(onClick = { viewModel.selectedChecklistId.value = null }) { Icon(Icons.Default.ArrowBack, "") }
                Text(currentCl?.name ?: "", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(8.dp))
                LazyColumn {
                    items(currentItems) { item ->
                        ListItem(headlineContent = { Text(item.text) }, trailingContent = { Checkbox(item.isCompleted, { viewModel.updateItemCompletion(item, it) }) })
                    }
                }
            }
        }
    }
}

@Composable
fun MapPickerDialog(onDismiss: () -> Unit, onLocationSelected: (String, Double, Double) -> Unit) {
    var selectedLatLng by remember { mutableStateOf(LatLng(6.9271, 79.8612)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Location") },
        text = {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                AndroidView(factory = { context ->
                    MapView(context).apply {
                        onCreate(null)
                        getMapAsync { googleMap ->
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 13f))
                            googleMap.setOnMapClickListener {
                                selectedLatLng = it
                                googleMap.clear()
                                googleMap.addMarker(MarkerOptions().position(it))
                            }
                        }
                    }
                })
            }
        },
        confirmButton = { Button({ onLocationSelected("Selected Location", selectedLatLng.latitude, selectedLatLng.longitude) }) { Text("Confirm") } }
    )
}
