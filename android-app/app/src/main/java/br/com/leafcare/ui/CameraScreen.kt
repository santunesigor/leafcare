package br.com.leafcare.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.sp
import br.com.leafcare.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(vm: LeafCareViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var help by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var canSwitch by remember { mutableStateOf(false) }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var ready by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.analyze(it) } }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { if (!permitted) permission.launch(Manifest.permission.CAMERA) }
    DisposableEffect(permitted, lens, owner) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        ready = false
        if (permitted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        val available = future.get()
                        provider = available
                        val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                        val camera = available.bindToLifecycle(owner, selector, preview, imageCapture)
                        hasFlash = camera.cameraInfo.hasFlashUnit()
                        canSwitch = available.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) && available.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                        if (!hasFlash) flash = false
                        ready = true
                    } catch (error: Exception) { vm.error("Não foi possível abrir a câmera. Você pode escolher uma foto da galeria. ${error.message.orEmpty()}") }
                }
            }, executor)
        }
        onDispose { disposed = true; provider?.unbind(preview, imageCapture) }
    }
    CameraContent(onBack = onBack, onHelp = { help = true },
        onGallery = { gallery.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/bmp")) },
        onFlash = { flash = !flash }, onSwitch = { lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK },
        enabled = ready && !capturing, galleryEnabled = !capturing, flashAvailable = hasFlash && ready && !capturing,
        switchAvailable = canSwitch && ready && !capturing, flashOn = flash, showGuide = permitted,
        onCapture = {
            capturing = true
            imageCapture.flashMode = if (flash && hasFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            previewView.display?.let { imageCapture.targetRotation = it.rotation }
            val file = File.createTempFile("capture-", ".jpg", context.cacheDir)
            imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        capturing = false; vm.analyze(Uri.fromFile(file), file)
                    }
                    override fun onError(error: ImageCaptureException) {
                        capturing = false; file.delete(); vm.error("Falha na captura: ${error.message}")
                    }
                })
        }) {
        if (permitted) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Autorize a câmera para fotografar.", color = Color.White)
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Abrir permissões", color = Color.White) }
        }
    }
    if (help) HelpSheet(onDismiss = { help = false })
}

@Composable
fun CameraContent(onBack: () -> Unit, onHelp: () -> Unit, onGallery: () -> Unit, onCapture: () -> Unit,
    onFlash: () -> Unit, onSwitch: () -> Unit, enabled: Boolean, galleryEnabled: Boolean,
    flashAvailable: Boolean, switchAvailable: Boolean, flashOn: Boolean, showGuide: Boolean,
    preview: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val compact = maxHeight < 520.dp
        Column(Modifier.fillMaxSize().then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                RoundIconButton(R.drawable.v3_back, "Voltar", onBack)
                Text("Fotografar folha", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                RoundIconButton(R.drawable.v3_help, "Como tirar a foto", onHelp)
            }
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().then(if (compact) Modifier.height(320.dp) else Modifier.weight(1f))
                .clip(RoundedCornerShape(24.dp)).background(Color(0xFF1D2B1E))) {
                preview()
                if (showGuide) {
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x52081209), .26f to Color.Transparent, .67f to Color.Transparent, 1f to Color(0x6B081209))))
                    Canvas(Modifier.fillMaxSize()) {
                        val x = size.width * .11f; val right = size.width - x
                        val y = size.height * .19f; val bottom = size.height * .83f
                        val arm = 54.dp.toPx(); val radius = 18.dp.toPx()
                        val path = Path().apply {
                            moveTo(x, y + arm); lineTo(x, y + radius); quadraticTo(x, y, x + radius, y); lineTo(x + arm, y)
                            moveTo(right - arm, y); lineTo(right - radius, y); quadraticTo(right, y, right, y + radius); lineTo(right, y + arm)
                            moveTo(x, bottom - arm); lineTo(x, bottom - radius); quadraticTo(x, bottom, x + radius, bottom); lineTo(x + arm, bottom)
                            moveTo(right - arm, bottom); lineTo(right - radius, bottom); quadraticTo(right, bottom, right, bottom - radius); lineTo(right, bottom - arm)
                        }
                        drawPath(path, Color.White, style = Stroke(5.dp.toPx()))
                    }
                    Column(Modifier.align(Alignment.Center).padding(horizontal = 35.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Coloque a folha em foco", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Mantenha a região afetada dentro da moldura", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                    }
                    Column(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                        RoundIconButton(R.drawable.v3_flash, if (flashOn) "Desligar flash" else "Ligar flash", onFlash, flashAvailable, true)
                        RoundIconButton(R.drawable.v3_switch_camera, "Alternar câmera", onSwitch, switchAvailable, true)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Surface(onClick = onGallery, enabled = galleryEnabled, modifier = Modifier.align(Alignment.CenterStart).padding(start = 59.dp).size(48.dp),
                    shape = RoundedCornerShape(14.dp), color = LeafColors.Pale, border = BorderStroke(1.dp, LeafColors.Border)) {
                    Box(contentAlignment = Alignment.Center) { FigmaIcon(R.drawable.v3_gallery, "Abrir galeria", 26) }
                }
                Surface(onClick = onCapture, enabled = enabled, modifier = Modifier.size(74.dp).semantics { contentDescription = "Fotografar" },
                    shape = CircleShape, color = Color.White, border = BorderStroke(3.dp, LeafColors.Green)) {
                    Box(contentAlignment = Alignment.Center) { Box(Modifier.size(57.dp).background(if (enabled) LeafColors.Green else LeafColors.Green.copy(alpha = .4f), CircleShape)) }
                }
            }
            Text("Use boa iluminação e evite sombras", Modifier.fillMaxWidth().padding(bottom = 20.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = LeafColors.Muted)
        }
    }
}
