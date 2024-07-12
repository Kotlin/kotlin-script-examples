/**
 * We need to manually target the correct multiplatform dependencies
 */
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:Repository("https://maven.google.com/")
// See https://mvnrepository.com/artifact/org.jetbrains.compose/compose-full
@file:DependsOn("org.jetbrains.compose:compose-full:1.6.11")
// See https://repo1.maven.org/maven2/org/jetbrains/compose/ui/ui/1.6.11/ui-1.6.11.pom
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
// See https://repo1.maven.org/maven2/org/jetbrains/compose/desktop/desktop-jvm-macos-arm64/1.6.11/desktop-jvm-macos-arm64-1.6.11.pom
@file:DependsOn("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.8.4")
@file:DependsOn("org.jetbrains.skiko:skiko-awt:0.8.4")
// Where is this defined?
@file:DependsOn("androidx.collection:collection-jvm:1.4.1")
// We need to manually target the relevant
@file:DependsOn("androidx.lifecycle:lifecycle-viewmodel-compose-desktop:2.8.3")
@file:DependsOn("androidx.lifecycle:lifecycle-runtime-compose-desktop:2.8.3")

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

val COLORS = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)

@Composable
fun App() {
    var colorIndex by remember { mutableStateOf(0) }
    val color = COLORS[colorIndex]
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(color).size(200.dp, 200.dp), contentAlignment = Alignment.Center) {
            Button(onClick = {
                colorIndex = (colorIndex + 1) % COLORS.size
            }) {
                Text("Click Me!")
            }
        }
    }
}

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(300.dp, 200.dp))
    Window(onCloseRequest = ::exitApplication, title = "Compose in Scripting Demo", state = windowState) {
        App()
    }
}

main()
