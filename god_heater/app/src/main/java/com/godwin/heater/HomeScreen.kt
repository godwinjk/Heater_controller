package com.godwin.heater


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.godwin.heater.vm.TemperatureViewModel

@Composable
fun TemperatureScreen(viewModel: TemperatureViewModel = viewModel()) {
    val currentTemp by viewModel.currentTemp.collectAsState()
    val setTemp by viewModel.setTemp.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val lastUpdatedTime by viewModel.lastUpdated.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startScan() // Ensures BLE scanning runs only once
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 50.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Current Temperature", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(10.dp))
        Text(" $currentTemp°C", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(20.dp))
        Text("Set Temperature", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(10.dp))
        Text(" $setTemp°C", style = MaterialTheme.typography.headlineLarge)

        Spacer(modifier = Modifier.height(20.dp))

        if (isScanning) {
            CircularProgressIndicator()
        } else {
            Button(onClick = {
                viewModel.startScan()
            }) {
                Text("Click to Update")
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
        Slider(
            value = setTemp.toFloat(),
            onValueChange = { viewModel.updateSetTemp(it.toInt()) },
            onValueChangeFinished = { viewModel.sentTemp() },
            valueRange = 10f..40f,
            steps = 30,
            modifier = Modifier.fillMaxWidth(), enabled = !isScanning
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text("Last Updated: $lastUpdatedTime")
    }
}