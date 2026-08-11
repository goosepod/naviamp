package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.naviamp
import org.jetbrains.compose.resources.painterResource

/** Returns the shared Naviamp application icon without exposing generated resource internals. */
@Composable
fun naviampAppIconPainter(): Painter = painterResource(Res.drawable.naviamp)
