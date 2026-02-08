package com.fatlosstrack.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.fatlosstrack.data.local.CapturedPhotoStore
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

enum class CaptureMode(val title: String, val subtitle: String) {
    LogMeal("Log a meal", "Take a photo of what you're eating"),
    SuggestMeal("Suggest a meal", "Snap your fridge or ingredients"),
}

/**
 * Full-screen camera capture with real CameraX preview.
 * Captures up to 3 photos, shows thumbnails in a bottom strip.
 * Tap thumbnail to view full-screen, X to remove.
 */
@Composable
fun MealCaptureScreen(
    mode: CaptureMode,
    onAnalyze: (mode: CaptureMode, photoCount: Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Photos state
    val capturedPhotos = remember { mutableStateListOf<Uri>() }
    var viewingIndex by remember { mutableIntStateOf(-1) }

    // Gallery picker — pick up to (3 - current) images
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris ->
        val slots = 3 - capturedPhotos.size
        uris.take(slots).forEach { capturedPhotos.add(it) }
    }

    // CameraX
    val imageCapture = remember { ImageCapture.Builder().build() }
    val coroutineScope = rememberCoroutineScope()

    // Bind camera when permission granted
    val previewView = remember { PreviewView(context) }
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        } catch (e: Exception) {
            Log.e("MealCapture", "Camera bind failed", e)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508)),
    ) {
        if (hasCameraPermission) {
            // ── Camera Preview ──
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 180.dp),
            )
        } else {
            // No permission state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = OnSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Camera permission required",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant permission", color = Primary)
                    }
                }
            }
        }

        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .background(Color.Black.copy(alpha = 0.4f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(mode.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "${capturedPhotos.size}/3 photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }

        // ── Bottom controls area ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Photo thumbnails strip
            if (capturedPhotos.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(capturedPhotos.toList()) { uri ->
                        val idx = capturedPhotos.indexOf(uri)
                        PhotoThumbnail(
                            uri = uri,
                            onRemove = { capturedPhotos.remove(uri) },
                            onView = { viewingIndex = idx },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Shutter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gallery picker
                IconButton(
                    onClick = {
                        if (capturedPhotos.size < 3) {
                            galleryLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        "Gallery",
                        tint = if (capturedPhotos.size < 3) Color.White.copy(alpha = 0.7f) else TrendFlat,
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Shutter
                val canCapture = capturedPhotos.size < 3 && hasCameraPermission
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(3.dp, if (canCapture) Primary else TrendFlat, CircleShape)
                        .clickable(enabled = canCapture) {
                            val photoFile = File(
                                context.cacheDir,
                                "meal_${System.currentTimeMillis()}.jpg"
                            )
                            val outputOptions = ImageCapture.OutputFileOptions
                                .Builder(photoFile)
                                .build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        capturedPhotos.add(Uri.fromFile(photoFile))
                                    }

                                    override fun onError(exc: ImageCaptureException) {
                                        Log.e("MealCapture", "Photo capture failed", exc)
                                    }
                                },
                            )
                        }
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (canCapture) Primary.copy(alpha = 0.15f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CameraAlt, "Take photo",
                        tint = if (canCapture) Primary else TrendFlat,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Confirm → analyze
                AnimatedVisibility(
                    visible = capturedPhotos.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = {
                            CapturedPhotoStore.store(capturedPhotos.toList())
                            onAnalyze(mode, capturedPhotos.size)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Secondary.copy(alpha = 0.25f)),
                    ) {
                        Icon(Icons.Default.Check, "Analyze", tint = Secondary, modifier = Modifier.size(28.dp))
                    }
                }

                if (capturedPhotos.isEmpty()) {
                    Spacer(Modifier.size(52.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // ── Full-screen photo viewer overlay ──
        if (viewingIndex >= 0 && viewingIndex < capturedPhotos.size) {
            PhotoViewer(
                photos = capturedPhotos.toList(),
                currentIndex = viewingIndex,
                onIndexChange = { viewingIndex = it },
                onDelete = { idx ->
                    capturedPhotos.removeAt(idx)
                    when {
                        capturedPhotos.isEmpty() -> viewingIndex = -1
                        idx >= capturedPhotos.size -> viewingIndex = capturedPhotos.size - 1
                    }
                },
                onDismiss = { viewingIndex = -1 },
            )
        }
    }
}

// ── Thumbnail with X remove button ──

@Composable
private fun PhotoThumbnail(
    uri: Uri,
    onRemove: () -> Unit,
    onView: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onView),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Captured photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Delete button (trash can)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

// ── Full-screen photo viewer with navigation ──

@Composable
private fun PhotoViewer(
    photos: List<Uri>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { photos.size },
    )

    // Sync pager → external state
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentIndex) {
            onIndexChange(pagerState.currentPage)
        }
    }

    // Sync external state → pager (e.g. thumbnail tap, arrow tap)
    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
    ) {
        // Swipeable photo pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
        ) { page ->
            AsyncImage(
                model = photos[page],
                contentDescription = "Photo preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }

        // Top bar: close + counter + delete
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Text(
                text = "${currentIndex + 1} / ${photos.size}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )

            IconButton(onClick = { onDelete(currentIndex) }) {
                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(28.dp))
            }
        }

        // Navigation arrows
        if (photos.size > 1) {
            // Previous
            if (currentIndex > 0) {
                IconButton(
                    onClick = { onIndexChange(currentIndex - 1) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Default.ChevronLeft, "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            // Next
            if (currentIndex < photos.size - 1) {
                IconButton(
                    onClick = { onIndexChange(currentIndex + 1) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Default.ChevronRight, "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Bottom thumbnail strip for quick jump
        if (photos.size > 1) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                items(photos) { uri ->
                    val idx = photos.indexOf(uri)
                    val isSelected = idx == currentIndex
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 56.dp else 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Primary else Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable { onIndexChange(idx) },
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}
