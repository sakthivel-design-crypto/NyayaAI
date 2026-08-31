package com.example.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.viewmodel.NyayaViewModel
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "SafetyMapScreen"

// Theme Palette (Dark Navy & Warm Gold)
private val DarkBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val SheetBg = Color(0xFF131D31)
private val GoldAccent = Color(0xFFEAB308)
private val LightGold = Color(0xFFFEF08A)
private val HighContrastWhite = Color(0xFFF8FAFC)
private val SubtitleGray = Color(0xFF94A3B8)
private val BorderSlate = Color(0xFF334155)

// Category Marker & Badge Colors
private val PoliceColor = Color(0xFF3B82F6) // Blue
private val HospitalColor = Color(0xFFEF4444) // Red
private val CourtColor = Color(0xFFF59E0B) // Amber
private val FireColor = Color(0xFFF43F5E) // Rose
private val LegalAidColor = Color(0xFF8B5CF6) // Violet
private val GovernmentColor = Color(0xFF10B981) // Emerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyMapScreen(
    viewModel: NyayaViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp

    // Live User GPS Location & Search Origin (for 150m movement threshold)
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var searchOriginLocation by remember { mutableStateOf<LatLng?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }

    // Map Camera State
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(28.6139, 77.2090), 13.5f)
    }

    // Permission State
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // GPS Service Enabled Check
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
    fun checkGpsEnabled(): Boolean {
        return try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (e: Exception) {
            false
        }
    }
    var isGpsEnabled by remember { mutableStateOf(checkGpsEnabled()) }

    // Fused Location Provider Client
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Update GPS coordinates and trigger 150m movement refresh
    fun applyLocationFix(location: Location?, animateCamera: Boolean = true) {
        if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
            val latLng = LatLng(location.latitude, location.longitude)
            Log.d(TAG, "GPS fix: Lat=${latLng.latitude}, Lng=${latLng.longitude}")
            userLocation = latLng
            isFetchingLocation = false
            viewModel.updateLocation(location.latitude, location.longitude)

            val origin = searchOriginLocation
            if (origin == null) {
                searchOriginLocation = latLng
            } else {
                val distanceResults = FloatArray(1)
                Location.distanceBetween(origin.latitude, origin.longitude, latLng.latitude, latLng.longitude, distanceResults)
                if (distanceResults[0] >= 150f) { // 150m threshold for automatic refresh
                    Log.i(TAG, "User moved ${distanceResults[0]}m -> refreshing OSM nearby services")
                    searchOriginLocation = latLng
                }
            }

            if (animateCamera || !hasCenteredOnUser) {
                hasCenteredOnUser = true
                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(latLng, 14.5f),
                        800
                    )
                }
            }
        }
    }

    // Fetch user location
    @SuppressLint("MissingPermission")
    fun fetchUserLocation(animateCamera: Boolean = true) {
        val finePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarsePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = finePermission || coarsePermission

        if (!hasLocationPermission) {
            isFetchingLocation = false
            return
        }

        isGpsEnabled = checkGpsEnabled()
        if (!isGpsEnabled) {
            isFetchingLocation = false
            return
        }

        isFetchingLocation = true
        Log.i(TAG, "Requesting user location from FusedLocationProviderClient...")

        coroutineScope.launch {
            try {
                val cts = CancellationTokenSource()
                val currentLocation = withContext(Dispatchers.IO) {
                    try {
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cts.token
                        ).await()
                    } catch (e: Exception) {
                        null
                    }
                }

                if (currentLocation != null) {
                    applyLocationFix(currentLocation, animateCamera = animateCamera)
                    return@launch
                }

                val lastLoc = withContext(Dispatchers.IO) {
                    try {
                        fusedLocationClient.lastLocation.await()
                    } catch (e: Exception) {
                        null
                    }
                }
                if (lastLoc != null) {
                    applyLocationFix(lastLoc, animateCamera = animateCamera)
                    return@launch
                }

                val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val bestNativeLoc = listOfNotNull(gpsLoc, netLoc).maxByOrNull { it.time }
                if (bestNativeLoc != null) {
                    applyLocationFix(bestNativeLoc, animateCamera = animateCamera)
                } else {
                    isFetchingLocation = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching location: ${e.message}")
                isFetchingLocation = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineGranted || coarseGranted
        isGpsEnabled = checkGpsEnabled()
        if (hasLocationPermission && isGpsEnabled) {
            fetchUserLocation(animateCamera = true)
        }
    }

    // Continuous Location Updates Listener
    DisposableEffect(hasLocationPermission, isGpsEnabled, lifecycleOwner) {
        var locationCallback: LocationCallback? = null

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                isGpsEnabled = checkGpsEnabled()
                if (hasLocationPermission && isGpsEnabled) {
                    if (userLocation == null) {
                        fetchUserLocation(animateCamera = true)
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (hasLocationPermission && isGpsEnabled) {
            try {
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { loc ->
                            applyLocationFix(loc, animateCamera = !hasCenteredOnUser)
                        }
                    }
                }
                locationCallback = callback
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                    .setMinUpdateDistanceMeters(10f)
                    .setMinUpdateIntervalMillis(2500L)
                    .build()

                fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException on location updates: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Exception on location updates: ${e.message}")
            }

            if (userLocation == null) {
                fetchUserLocation(animateCamera = true)
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationCallback?.let { cb ->
                try {
                    fusedLocationClient.removeLocationUpdates(cb)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing location updates: ${e.message}")
                }
            }
        }
    }

    // Filters, Radius & Search State
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SafetyCategory.POLICE) }
    var selectedRadiusKm by remember { mutableIntStateOf(5) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Nearby Services & UI State
    var nearbyServices by remember { mutableStateOf<List<NearbyService>>(emptyList()) }
    var selectedService by remember { mutableStateOf<NearbyService?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isNetworkError by remember { mutableStateOf(false) }
    var searchVersion by remember { mutableLongStateOf(0L) }
    var lastDiagnostics by remember { mutableStateOf<SearchDiagnostics?>(null) }
    var showDeveloperDiagnostics by remember { mutableStateOf(false) }

    // Bottom Sheet Collapsible / Draggable State
    // Collapsed: ~170dp (shows nearest service spotlight & header)
    // Expanded: ~55% of screen height (shows full list)
    var isSheetExpanded by remember { mutableStateOf(false) }
    val collapsedHeight = 175.dp
    val expandedHeight = (screenHeightDp * 0.54f).coerceIn(360.dp, 560.dp)
    val animatedSheetHeight by animateDpAsState(
        targetValue = if (isSheetExpanded) expandedHeight else collapsedHeight,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
        label = "sheetHeight"
    )

    // Debounce search bar input
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            debouncedQuery = ""
        } else {
            delay(300)
            debouncedQuery = searchQuery
            val lower = searchQuery.trim().lowercase()
            when {
                lower == "police" -> selectedCategory = SafetyCategory.POLICE
                lower == "hospital" || lower == "clinic" || lower == "doctor" -> selectedCategory = SafetyCategory.HOSPITAL
                lower == "fire" || lower == "fire station" -> selectedCategory = SafetyCategory.FIRE
                lower == "court" || lower == "courthouse" -> selectedCategory = SafetyCategory.COURT
                lower == "lawyer" || lower == "legal" || lower == "legal aid" -> selectedCategory = SafetyCategory.LEGAL_AID
                lower == "government" || lower == "govt" -> selectedCategory = SafetyCategory.GOVERNMENT
            }
        }
    }

    // Execute OpenStreetMap Overpass Search via PlacesRepository
    LaunchedEffect(searchOriginLocation, selectedCategory, debouncedQuery, selectedRadiusKm, retryTrigger) {
        val currentLoc = searchOriginLocation ?: userLocation
        if (currentLoc != null) {
            val currentRequestId = ++searchVersion
            isNetworkError = false
            searchJob?.cancel()
            // Clear old category markers and results immediately when category/radius/origin changes
            nearbyServices = emptyList()
            selectedService = null

            searchJob = launch {
                Log.i(TAG, "Querying OSM Places #$currentRequestId: cat=${selectedCategory.name}, radius=${selectedRadiusKm}km, query='$debouncedQuery'")
                try {
                    val result = withTimeoutOrNull(35000L) {
                        PlacesRepository.searchNearbyServices(
                            latitude = currentLoc.latitude,
                            longitude = currentLoc.longitude,
                            radiusKm = selectedRadiusKm.toDouble(),
                            category = selectedCategory,
                            searchQuery = debouncedQuery,
                            onProgressUpdate = { liveDiag ->
                                if (currentRequestId == searchVersion) {
                                    lastDiagnostics = liveDiag
                                }
                            }
                        )
                    }

                    if (currentRequestId == searchVersion) {
                        if (result != null) {
                            when (result) {
                                is PlacesResult.Success -> {
                                    isNetworkError = false
                                    nearbyServices = result.services
                                    lastDiagnostics = result.diagnostics
                                    Log.i(TAG, "OSM query SUCCESS: ${result.services.size} items found")

                                    // Auto-adjust camera so user location and the closest 1-3 services are in view
                                    if (result.services.isNotEmpty()) {
                                        try {
                                            val nearest = result.services.first()
                                            val boundsBuilder = LatLngBounds.builder()
                                            boundsBuilder.include(currentLoc)
                                            // Include up to 3 closest services
                                            result.services.take(3).forEach { svc ->
                                                boundsBuilder.include(LatLng(svc.latitude, svc.longitude))
                                            }
                                            val bounds = boundsBuilder.build()
                                            coroutineScope.launch {
                                                if (nearest.distanceMeters <= 1200f) {
                                                    cameraPositionState.animate(
                                                        CameraUpdateFactory.newLatLngZoom(currentLoc, 15.0f),
                                                        500
                                                    )
                                                } else {
                                                    cameraPositionState.animate(
                                                        CameraUpdateFactory.newLatLngBounds(bounds, 130),
                                                        600
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            coroutineScope.launch {
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(currentLoc, 14.5f),
                                                    500
                                                )
                                            }
                                        }
                                    }
                                }
                                is PlacesResult.Error -> {
                                    Log.w(TAG, "OSM query ERROR: ${result.message}")
                                    isNetworkError = result.isNetworkError
                                    nearbyServices = emptyList()
                                    lastDiagnostics = result.diagnostics
                                }
                            }
                        } else {
                            Log.w(TAG, "OSM query TIMEOUT")
                            isNetworkError = true
                            nearbyServices = emptyList()
                        }
                    }
                } catch (e: Exception) {
                    if (currentRequestId == searchVersion) {
                        Log.e(TAG, "OSM query EXCEPTION: ${e.message}", e)
                        isNetworkError = true
                        nearbyServices = emptyList()
                    }
                }
            }
        } else {
            nearbyServices = emptyList()
        }
    }

    val listState = rememberLazyListState()

    // Map UI properties
    val mapProperties = remember(mapType) {
        MapProperties(
            isMyLocationEnabled = false,
            mapType = mapType,
            mapStyleOptions = if (mapType == MapType.NORMAL) MapStyleOptions(DarkMapStyle.JSON) else null
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false
        )
    }

    var isMapLoaded by remember { mutableStateOf(false) }

    // External Intent Handlers
    fun openNavigationIntent(service: NearbyService) {
        val userPos = userLocation
        val originParam = if (userPos != null) "&origin=${userPos.latitude},${userPos.longitude}" else ""
        val gmmIntentUri = Uri.parse("geo:${service.latitude},${service.longitude}?q=${service.latitude},${service.longitude}(${Uri.encode(service.name)})")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        try {
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/dir/?api=1${originParam}&destination=${service.latitude},${service.longitude}")
            )
            context.startActivity(webIntent)
        }
    }

    fun openOsmMapSearch(service: NearbyService) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.openstreetmap.org/?mlat=${service.latitude}&mlon=${service.longitude}#map=16/${service.latitude}/${service.longitude}")
        )
        try {
            context.startActivity(webIntent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${service.latitude},${service.longitude}")
            )
            context.startActivity(fallbackIntent)
        }
    }

    fun openWebsite(website: String) {
        if (website.isNotBlank()) {
            val url = if (!website.startsWith("http://") && !website.startsWith("https://")) "https://$website" else website
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copyAddressToClipboard(service: NearbyService) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val addressText = if (service.address.isNotBlank()) "${service.name}, ${service.address}" else service.name
        val clip = ClipData.newPlainText("Address", addressText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun dialPhoneNumber(phone: String) {
        if (phone.isNotBlank() && phone.any { it.isDigit() }) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace("[^0-9+]".toRegex(), "")}"))
            context.startActivity(intent)
        }
    }

    // Nearest service (first element in distance-sorted list)
    val nearestService = nearbyServices.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .testTag("safety_map_screen")
    ) {
        // 1. PRIMARY FULL-SCREEN GOOGLE MAP (Renders behind floating controls & bottom sheet)
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("google_map_view"),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapLoaded = { isMapLoaded = true },
            onMapClick = { selectedService = null }
        ) {
            // Live User GPS Marker (Azure Blue)
            userLocation?.let { latLng ->
                Marker(
                    state = MarkerState(position = latLng),
                    title = "Your Location",
                    snippet = "Live GPS: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // Real OpenStreetMap POI Markers with category-specific color hues
            nearbyServices.forEach { service ->
                val markerHue = when (service.category) {
                    SafetyCategory.POLICE -> BitmapDescriptorFactory.HUE_BLUE
                    SafetyCategory.HOSPITAL -> BitmapDescriptorFactory.HUE_RED
                    SafetyCategory.COURT -> BitmapDescriptorFactory.HUE_ORANGE
                    SafetyCategory.FIRE -> BitmapDescriptorFactory.HUE_ROSE
                    SafetyCategory.LEGAL_AID -> BitmapDescriptorFactory.HUE_VIOLET
                    SafetyCategory.GOVERNMENT -> BitmapDescriptorFactory.HUE_GREEN
                    SafetyCategory.ALL -> BitmapDescriptorFactory.HUE_YELLOW
                }

                Marker(
                    state = MarkerState(position = LatLng(service.latitude, service.longitude)),
                    title = service.name,
                    snippet = "${service.category.displayName} • ${PlacesRepository.formatDistance(service.distanceMeters)}",
                    icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                    onClick = {
                        selectedService = service
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(service.latitude, service.longitude), 15.5f),
                                500
                            )
                        }
                        true
                    }
                )
            }
        }

        // 2. Loading Overlay while Map initializes
        AnimatedVisibility(
            visible = !isMapLoaded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GoldAccent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading Safety Map...",
                        color = HighContrastWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. FLOATING TOP BAR: Search Bar, Category Chips, Radius Chips & Status
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter)
        ) {
            // Floating Search Input Field
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = CardBg.copy(alpha = 0.96f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("map_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Dashboard",
                                tint = GoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search police, hospital, court, fire, legal...",
                                color = SubtitleGray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = HighContrastWhite,
                            unfocusedTextColor = HighContrastWhite,
                            cursorColor = GoldAccent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_input")
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = SubtitleGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontally Scrollable Category Chips
            val categories = listOf(
                SafetyCategory.ALL,
                SafetyCategory.POLICE,
                SafetyCategory.HOSPITAL,
                SafetyCategory.FIRE,
                SafetyCategory.COURT,
                SafetyCategory.LEGAL_AID,
                SafetyCategory.GOVERNMENT
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    val chipBg = if (isSelected) GoldAccent else CardBg.copy(alpha = 0.92f)
                    val chipTextColor = if (isSelected) Color.Black else HighContrastWhite
                    val chipBorder = if (isSelected) GoldAccent else BorderSlate

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(16.dp))
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("map_chip_${category.name.lowercase()}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (category) {
                                SafetyCategory.POLICE -> Icons.Default.Shield
                                SafetyCategory.HOSPITAL -> Icons.Default.LocalHospital
                                SafetyCategory.FIRE -> Icons.Default.LocalFireDepartment
                                SafetyCategory.COURT -> Icons.Default.Gavel
                                SafetyCategory.LEGAL_AID -> Icons.Default.Balance
                                SafetyCategory.GOVERNMENT -> Icons.Default.AccountBalance
                                SafetyCategory.ALL -> Icons.Default.Category
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = category.displayName,
                                tint = chipTextColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = category.displayName,
                                color = chipTextColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scan Radius Selector Chips (1 km, 2 km, 5 km, 10 km, 20 km, 50 km)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = CardBg.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate)
                ) {
                    Text(
                        text = "Radius",
                        color = GoldAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                val radii = listOf(1, 2, 5, 10, 20, 50)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(horizontal = 1.dp)
                ) {
                    items(radii) { r ->
                        val isSelected = selectedRadiusKm == r
                        val chipBg = if (isSelected) GoldAccent else CardBg.copy(alpha = 0.88f)
                        val chipTextColor = if (isSelected) Color.Black else HighContrastWhite
                        val chipBorder = if (isSelected) GoldAccent else BorderSlate

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(chipBg)
                                .border(1.dp, chipBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedRadiusKm = r }
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                            .testTag("radius_chip_${r}km")
                        ) {
                            Text(
                                text = "$r km",
                                color = chipTextColor,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Location Permission Required Banner
            if (!hasLocationPermission) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF2D1F15),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = "Location Needed",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location Permission Required",
                                color = HighContrastWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enable location access to find real nearby police, hospitals, courts, and emergency services.",
                                color = SubtitleGray,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Allow", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            } else if (!isGpsEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF2D1F15),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsOff,
                            contentDescription = "GPS Disabled",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location is turned off",
                                color = HighContrastWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Please enable GPS to locate nearby emergency services around you.",
                                color = SubtitleGray,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Turn On", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 4. FLOATING MAP CONTROLS (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .offset(y = (-40).dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Developer Diagnostic Section Toggle Button
            FloatingActionButton(
                onClick = { showDeveloperDiagnostics = !showDeveloperDiagnostics },
                containerColor = if (showDeveloperDiagnostics) GoldAccent else CardBg.copy(alpha = 0.95f),
                contentColor = if (showDeveloperDiagnostics) Color.Black else GoldAccent,
                shape = CircleShape,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("developer_diagnostics_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Developer Diagnostics",
                    modifier = Modifier.size(17.dp)
                )
            }

            // Satellite / Map Layer Toggle
            FloatingActionButton(
                onClick = {
                    mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                },
                containerColor = CardBg.copy(alpha = 0.95f),
                contentColor = GoldAccent,
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("map_type_toggle_button")
            ) {
                Icon(
                    imageVector = if (mapType == MapType.NORMAL) Icons.Default.Layers else Icons.Default.Map,
                    contentDescription = "Toggle Map Layer",
                    modifier = Modifier.size(18.dp)
                )
            }

            // My Location GPS Recenter Button
            FloatingActionButton(
                onClick = {
                    userLocation?.let { searchOriginLocation = it }
                    fetchUserLocation(animateCamera = true)
                },
                containerColor = CardBg.copy(alpha = 0.95f),
                contentColor = GoldAccent,
                shape = CircleShape,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("map_my_location_button")
            ) {
                if (isFetchingLocation) {
                    CircularProgressIndicator(
                        color = GoldAccent,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 4.1. COLLAPSIBLE DEVELOPER DIAGNOSTIC OVERLAY PANEL
        AnimatedVisibility(
            visible = showDeveloperDiagnostics,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 180.dp, start = 14.dp, end = 14.dp)
                .fillMaxWidth()
        ) {
            Surface(
                color = CardBg.copy(alpha = 0.96f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.BugReport, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Category Diagnostics", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showDeveloperDiagnostics = false }, modifier = Modifier.size(20.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SubtitleGray, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test Trigger Action Buttons
                    Text("TEST CATEGORIES:", color = SubtitleGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        val testCategories = listOf(
                            SafetyCategory.POLICE to "POLICE",
                            SafetyCategory.HOSPITAL to "HOSPITAL",
                            SafetyCategory.FIRE to "FIRE",
                            SafetyCategory.COURT to "COURT",
                            SafetyCategory.LEGAL_AID to "LEGAL AID",
                            SafetyCategory.GOVERNMENT to "GOVERNMENT",
                            SafetyCategory.ALL to "ALL"
                        )
                        items(testCategories) { (cat, label) ->
                            Button(
                                onClick = {
                                    selectedCategory = cat
                                    retryTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedCategory == cat) GoldAccent else Color(0xFF222834),
                                    contentColor = if (selectedCategory == cat) Color.Black else HighContrastWhite
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("TEST $label", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Diagnostic Metrics Table
                    val diag = lastDiagnostics
                    val gpsLat = userLocation?.latitude?.let { String.format(Locale.US, "%.5f", it) } ?: "N/A"
                    val gpsLng = userLocation?.longitude?.let { String.format(Locale.US, "%.5f", it) } ?: "N/A"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F141C), RoundedCornerShape(8.dp))
                            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        DiagnosticRow("Category:", selectedCategory.displayName)
                        DiagnosticRow("GPS Origin:", "$gpsLat, $gpsLng")
                        DiagnosticRow("Selected Radius:", "$selectedRadiusKm km")
                        DiagnosticRow("Ring Searching:", diag?.currentRing ?: "0–$selectedRadiusKm km")
                        DiagnosticRow("Active Endpoint:", diag?.endpointUsed ?: "None")
                        DiagnosticRow("Stage A Count:", "${diag?.stageACount ?: 0}")
                        DiagnosticRow("Stage B Count:", "${diag?.stageBCount ?: 0}")
                        DiagnosticRow("Merged Count:", "${diag?.mergedCount ?: 0}")
                        DiagnosticRow("Coordinate Valid:", "${diag?.afterCoordValidCount ?: 0}")
                        DiagnosticRow("Semantic Valid:", "${diag?.afterSemanticCount ?: 0}")
                        DiagnosticRow("Radius Valid:", "${diag?.afterRadiusCount ?: 0}")
                        DiagnosticRow("After Dedup:", "${diag?.afterDedupCount ?: 0}")
                        DiagnosticRow("Final Count:", "${diag?.finalValidCount ?: nearbyServices.size}")
                        DiagnosticRow("Nearest Name:", diag?.nearestName ?: (nearbyServices.firstOrNull()?.name ?: "None"))
                        DiagnosticRow("Nearest Distance:", diag?.nearestDistance ?: "N/A")
                        DiagnosticRow("Search Duration:", "${diag?.searchDurationMs ?: 0} ms")
                        DiagnosticRow(
                            "Final State:",
                            diag?.finalState ?: if (isNetworkError) "NETWORK_ERROR" else if (nearbyServices.isEmpty()) "SUCCESS_EMPTY" else "SUCCESS",
                            valueColor = when (diag?.finalState) {
                                "SUCCESS" -> Color(0xFF10B981)
                                "SUCCESS_EMPTY" -> Color(0xFFF59E0B)
                                "NETWORK_ERROR" -> Color(0xFFEF4444)
                                else -> GoldAccent
                            }
                        )
                        if (!diag?.errorMessage.isNullOrBlank()) {
                            DiagnosticRow("Error:", diag?.errorMessage ?: "", valueColor = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }

        // 5. SELECTED MARKER DETAIL CARD POPUP (When tapping a pin)
        AnimatedVisibility(
            visible = selectedService != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = animatedSheetHeight + 8.dp, start = 14.dp, end = 14.dp)
        ) {
            selectedService?.let { service ->
                val categoryColor = when (service.category) {
                    SafetyCategory.POLICE -> PoliceColor
                    SafetyCategory.HOSPITAL -> HospitalColor
                    SafetyCategory.COURT -> CourtColor
                    SafetyCategory.FIRE -> FireColor
                    SafetyCategory.LEGAL_AID -> LegalAidColor
                    SafetyCategory.GOVERNMENT -> GovernmentColor
                    SafetyCategory.ALL -> GoldAccent
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBg,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldAccent),
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = categoryColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = service.category.displayName,
                                        color = categoryColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                if (service.openingHours.isNotBlank()) {
                                    Text(
                                        text = service.openingHours,
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedService = null },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = SubtitleGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = service.name,
                            color = HighContrastWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = PlacesRepository.formatDistance(service.distanceMeters),
                                color = GoldAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (service.address.isNotBlank()) {
                                Text(
                                    text = " • ${service.address}",
                                    color = SubtitleGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (service.phone.isNotBlank() && service.phone.any { it.isDigit() }) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Phone: ${service.phone}",
                                color = SubtitleGray,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { openNavigationIntent(service) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Route",
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Route", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { openOsmMapSearch(service) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HighContrastWhite),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "OSM View",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("OSM Map", fontSize = 11.sp)
                            }

                            if (service.website.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { openWebsite(service.website) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HighContrastWhite),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Website",
                                        tint = GoldAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            if (service.phone.isNotBlank() && service.phone.any { it.isDigit() }) {
                                IconButton(
                                    onClick = { dialPhoneNumber(service.phone) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(PoliceColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Call",
                                        tint = PoliceColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { copyAddressToClipboard(service) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(BorderSlate.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Address",
                                    tint = HighContrastWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. DRAGGABLE & COLLAPSIBLE BOTTOM SHEET
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedSheetHeight)
                .align(Alignment.BottomCenter)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (delta < -15f) isSheetExpanded = true
                        else if (delta > 15f) isSheetExpanded = false
                    }
                ),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = SheetBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Drag Handle Bar (clickable to toggle expand/collapse)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSheetExpanded = !isSheetExpanded }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BorderSlate)
                    )
                }

                // Header Row with Category Count & Expand/Collapse Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSheetExpanded = !isSheetExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Nearby ${if (selectedCategory == SafetyCategory.ALL) "Services" else selectedCategory.displayName}",
                                color = HighContrastWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (nearbyServices.isNotEmpty()) {
                                Surface(
                                    color = GoldAccent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${nearbyServices.size}",
                                        color = GoldAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        val categoryPlural = if (selectedCategory == SafetyCategory.ALL) "emergency services" else selectedCategory.pluralName.lowercase()
                        Text(
                            text = when {
                                isNetworkError -> "Network connection issue"
                                searchJob?.isActive == true -> "Searching OpenStreetMap..."
                                nearbyServices.isNotEmpty() -> "Found within $selectedRadiusKm km • Sorted by distance"
                                else -> "No $categoryPlural within $selectedRadiusKm km"
                            },
                            color = if (isNetworkError) Color(0xFFEF4444) else SubtitleGray,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Refresh Trigger Button
                        IconButton(
                            onClick = { retryTrigger++ },
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (searchJob?.isActive == true) {
                                CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Expand / Collapse Chevron Button
                        IconButton(
                            onClick = { isSheetExpanded = !isSheetExpanded },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isSheetExpanded) "Collapse" else "Expand",
                                tint = SubtitleGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Sheet Body (Collapsed vs Expanded states)
                when {
                    isNetworkError -> {
                        // NETWORK_ERROR STATE
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Unable to load nearby services. Check your internet connection and retry.",
                                    color = SubtitleGray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { retryTrigger++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    searchJob?.isActive == true && nearbyServices.isEmpty() -> {
                        // SEARCHING STATE
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Finding nearest services via OpenStreetMap...", color = SubtitleGray, fontSize = 11.sp)
                            }
                        }
                    }

                    nearbyServices.isEmpty() -> {
                        // SUCCESS_EMPTY STATE
                        val categoryPlural = if (selectedCategory == SafetyCategory.ALL) "emergency services" else selectedCategory.pluralName.lowercase()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "No $categoryPlural found within $selectedRadiusKm km.",
                                    color = SubtitleGray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (selectedRadiusKm < 50) {
                                    val nextRadius = when (selectedRadiusKm) {
                                        1 -> 2
                                        2 -> 5
                                        5 -> 10
                                        10 -> 20
                                        else -> 50
                                    }
                                    OutlinedButton(
                                        onClick = { selectedRadiusKm = nextRadius },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("Expand Radius to $nextRadius km", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    !isSheetExpanded -> {
                        // COLLAPSED MODE: Prominent NEAREST SERVICE Spotlight Card
                        nearestService?.let { nearest ->
                            val catColor = when (nearest.category) {
                                SafetyCategory.POLICE -> PoliceColor
                                SafetyCategory.HOSPITAL -> HospitalColor
                                SafetyCategory.COURT -> CourtColor
                                SafetyCategory.FIRE -> FireColor
                                SafetyCategory.LEGAL_AID -> LegalAidColor
                                SafetyCategory.GOVERNMENT -> GovernmentColor
                                SafetyCategory.ALL -> GoldAccent
                            }

                            Surface(
                                color = CardBg,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedService = nearest
                                        coroutineScope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(nearest.latitude, nearest.longitude), 15.5f),
                                                500
                                            )
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = catColor.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "NEAREST ${nearest.category.name}",
                                                    color = catColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = PlacesRepository.formatDistance(nearest.distanceMeters),
                                            color = GoldAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = nearest.name,
                                                color = HighContrastWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (nearest.address.isNotBlank()) {
                                                Text(
                                                    text = nearest.address,
                                                    color = SubtitleGray,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (nearest.phone.isNotBlank() && nearest.phone.any { it.isDigit() }) {
                                                IconButton(
                                                    onClick = { dialPhoneNumber(nearest.phone) },
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(PoliceColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                ) {
                                                    Icon(imageVector = Icons.Default.Phone, contentDescription = "Call", tint = PoliceColor, modifier = Modifier.size(13.dp))
                                                }
                                            }

                                            Button(
                                                onClick = { openNavigationIntent(nearest) },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Navigation, contentDescription = "Route", modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Route", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // EXPANDED MODE: Full Distance-Sorted List of All Discovered Services
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(nearbyServices, key = { it.id }) { service ->
                                val categoryColor = when (service.category) {
                                    SafetyCategory.POLICE -> PoliceColor
                                    SafetyCategory.HOSPITAL -> HospitalColor
                                    SafetyCategory.COURT -> CourtColor
                                    SafetyCategory.FIRE -> FireColor
                                    SafetyCategory.LEGAL_AID -> LegalAidColor
                                    SafetyCategory.GOVERNMENT -> GovernmentColor
                                    SafetyCategory.ALL -> GoldAccent
                                }

                                Surface(
                                    color = CardBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selectedService?.id == service.id) GoldAccent else BorderSlate
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedService = service
                                            coroutineScope.launch {
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(service.latitude, service.longitude),
                                                        15.5f
                                                    ),
                                                    500
                                                )
                                            }
                                        }
                                        .testTag("service_card_${service.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Category Dot Indicator
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(categoryColor)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = service.name,
                                                color = HighContrastWhite,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (service.address.isNotBlank()) {
                                                Text(
                                                    text = service.address,
                                                    color = SubtitleGray,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Distance text
                                        Text(
                                            text = PlacesRepository.formatDistance(service.distanceMeters),
                                            color = GoldAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Quick Navigation Button
                                        IconButton(
                                            onClick = { openNavigationIntent(service) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(GoldAccent, RoundedCornerShape(6.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Navigation,
                                                contentDescription = "Route",
                                                tint = Color.Black,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Attribution Line
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "POI Data © OpenStreetMap contributors",
                            color = SubtitleGray.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color = HighContrastWhite
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = SubtitleGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

