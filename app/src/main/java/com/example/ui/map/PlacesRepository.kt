package com.example.ui.map

import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class SearchDiagnostics(
    val category: SafetyCategory,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double,
    val currentRing: String = "0–1 km",
    val endpointUsed: String,
    val stageACount: Int = 0,
    val stageBCount: Int = 0,
    val mergedCount: Int = 0,
    val afterCoordValidCount: Int = 0,
    val afterSemanticCount: Int = 0,
    val afterRadiusCount: Int = 0,
    val afterDedupCount: Int = 0,
    val finalValidCount: Int = 0,
    val nearestName: String = "N/A",
    val nearestDistance: String = "N/A",
    val stageADurationMs: Long = 0L,
    val stageBDurationMs: Long = 0L,
    val searchDurationMs: Long = 0L,
    val finalState: String = "SEARCHING",
    val errorMessage: String? = null
)

sealed class PlacesResult {
    data class Success(
        val services: List<NearbyService>,
        val category: SafetyCategory,
        val totalFound: Int,
        val diagnostics: SearchDiagnostics? = null
    ) : PlacesResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean = false,
        val technicalDetail: String? = null,
        val diagnostics: SearchDiagnostics? = null
    ) : PlacesResult()
}

object PlacesRepository {

    // Logcat tag as mandated for diagnostics
    private const val TAG = "NyayaAIPlaces"

    // Multi-server Overpass failover cluster (100% Free & Open Source)
    private val OVERPASS_ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // In-memory cache with 5-minute TTL
    // Key format: "latBucket_lngBucket_radiusKm_category"
    private val cache = ConcurrentHashMap<String, Pair<Long, List<NearbyService>>>()
    private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L

    /**
     * Searches nearby real OpenStreetMap services using the Overpass API with multi-server failover.
     * 100% billing-free, requires NO Google Cloud Places API, NO credit card, and NO paid SDKs.
     */
    suspend fun searchNearbyServices(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        category: SafetyCategory,
        searchQuery: String = "",
        onProgressUpdate: (SearchDiagnostics) -> Unit = {}
    ): PlacesResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        Log.i(TAG, "========================================")
        Log.i(TAG, "SEARCH ORIGIN:")
        Log.i(TAG, "lat=$latitude")
        Log.i(TAG, "lon=$longitude")
        Log.i(TAG, "radius=$radiusKm")
        Log.i(TAG, "category=${category.name}")
        Log.i(TAG, "SEARCH START category=${category.name} radius=${radiusKm}km lat=$latitude lon=$longitude")

        var activeEndpoint = OVERPASS_ENDPOINTS.first().substringAfter("://").substringBefore("/")

        // Initial progress update to guarantee the UI is never stuck on 'None' or 0/0/0
        var currentDiag = SearchDiagnostics(
            category = category,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            currentRing = "0–1 km",
            endpointUsed = activeEndpoint,
            stageACount = 0,
            stageBCount = 0,
            mergedCount = 0,
            afterCoordValidCount = 0,
            afterSemanticCount = 0,
            afterRadiusCount = 0,
            afterDedupCount = 0,
            finalValidCount = 0,
            nearestName = "N/A",
            nearestDistance = "N/A",
            stageADurationMs = 0L,
            stageBDurationMs = 0L,
            searchDurationMs = 0L,
            finalState = "SEARCHING"
        )
        onProgressUpdate(currentDiag)

        // Validate coordinates
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0 || (latitude == 0.0 && longitude == 0.0)) {
            Log.w(TAG, "SEARCH FAILED: Invalid GPS coordinates ($latitude, $longitude)")
            val diag = currentDiag.copy(
                endpointUsed = "Invalid GPS",
                searchDurationMs = System.currentTimeMillis() - startTime,
                finalState = "LOCATION_ERROR",
                errorMessage = "Invalid GPS coordinates"
            )
            onProgressUpdate(diag)
            return@withContext PlacesResult.Error(
                message = "Invalid GPS coordinates",
                isNetworkError = false,
                diagnostics = diag
            )
        }

        val clampedRadiusMeters = (radiusKm * 1000).toInt().coerceIn(500, 50000)
        val cleanQuery = searchQuery.trim()

        // 3 decimal places (~110m bucket) for caching
        val latBucket = String.format(Locale.US, "%.3f", latitude)
        val lngBucket = String.format(Locale.US, "%.3f", longitude)
        val cacheKey = "${latBucket}_${lngBucket}_${radiusKm}_${category.name}"

        // Check Cache (only when not performing a custom text search)
        if (cleanQuery.isBlank()) {
            cache[cacheKey]?.let { (timestamp, cachedList) ->
                if (System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS) {
                    Log.i(TAG, "Cache HIT for $cacheKey: ${cachedList.size} items")
                    val updatedDistances = cachedList.map { svc ->
                        val distM = calculateDistanceMeters(latitude, longitude, svc.latitude, svc.longitude)
                        svc.copy(distanceMeters = distM)
                    }.filter { it.distanceMeters <= clampedRadiusMeters }
                     .sortedBy { it.distanceMeters }

                    val nearest = updatedDistances.firstOrNull()
                    val nearestDist = if (nearest != null) formatDistance(nearest.distanceMeters) else "N/A"
                    val nearestName = nearest?.name ?: "None"
                    val finalState = if (updatedDistances.isNotEmpty()) "SUCCESS" else "SUCCESS_EMPTY"

                    if (nearest != null) {
                        Log.i(TAG, "NEAREST SERVICE:")
                        Log.i(TAG, "name=${nearest.name}")
                        Log.i(TAG, "distance=${nearest.distanceMeters}")
                        Log.i(TAG, "lat=${nearest.latitude}")
                        Log.i(TAG, "lon=${nearest.longitude}")
                    }

                    Log.i(TAG, "FINAL RESULT COUNT: ${updatedDistances.size}")
                    Log.i(TAG, "NEAREST RESULT: $nearestDist ($nearestName)")
                    Log.i(TAG, if (updatedDistances.isNotEmpty()) "SEARCH SUCCESS (Cached)" else "SEARCH EMPTY (Cached)")

                    val diag = currentDiag.copy(
                        endpointUsed = "Memory Cache",
                        currentRing = "0–${clampedRadiusMeters / 1000} km",
                        stageACount = updatedDistances.size,
                        stageBCount = 0,
                        mergedCount = updatedDistances.size,
                        afterCoordValidCount = updatedDistances.size,
                        afterSemanticCount = updatedDistances.size,
                        afterRadiusCount = updatedDistances.size,
                        afterDedupCount = updatedDistances.size,
                        finalValidCount = updatedDistances.size,
                        nearestName = nearestName,
                        nearestDistance = nearestDist,
                        searchDurationMs = System.currentTimeMillis() - startTime,
                        finalState = finalState
                    )
                    onProgressUpdate(diag)

                    return@withContext PlacesResult.Success(
                        services = updatedDistances,
                        category = category,
                        totalFound = updatedDistances.size,
                        diagnostics = diag
                    )
                }
            }
        }

        var endpointUsed = "None"
        var stageACount = 0
        var stageBCount = 0
        var stageADurationTotal = 0L
        var stageBDurationTotal = 0L
        var lastErrorDetail: String? = null
        var hadAnySuccessfulResponse = false

        // Progressive discovery rings:
        // Ring 1: 0–1 km
        // Ring 2: 1–2 km
        // Ring 3: 2–5 km
        // Ring 4: 5–10 km
        // Ring 5: 10–20 km
        // Ring 6: 20–50 km
        val standardRingBoundaries = listOf(1000, 2000, 5000, 10000, 20000, 50000)
        data class SearchRing(val radiusMeters: Int, val ringLabel: String)

        val ringsToSearch = mutableListOf<SearchRing>()
        var prevBoundary = 0
        for (boundary in standardRingBoundaries) {
            if (boundary < clampedRadiusMeters) {
                val label = if (prevBoundary == 0) "0–${boundary / 1000} km" else "${prevBoundary / 1000}–${boundary / 1000} km"
                ringsToSearch.add(SearchRing(boundary, label))
                prevBoundary = boundary
            } else {
                val label = if (prevBoundary == 0) "0–${clampedRadiusMeters / 1000} km" else "${prevBoundary / 1000}–${clampedRadiusMeters / 1000} km"
                ringsToSearch.add(SearchRing(clampedRadiusMeters, label))
                break
            }
        }
        if (ringsToSearch.isEmpty()) {
            ringsToSearch.add(SearchRing(clampedRadiusMeters, "0–${clampedRadiusMeters / 1000} km"))
        }

        val accumulatedRawElements = JSONArray()
        val seenOsmIds = mutableSetOf<String>()

        // Helper to accumulate unique raw elements across stages and rings
        fun mergeRawElements(elements: JSONArray) {
            for (i in 0 until elements.length()) {
                val elem = elements.optJSONObject(i) ?: continue
                val type = elem.optString("type", "node")
                val id = elem.optLong("id", 0L)
                val key = "${type}_$id"
                if (id != 0L && seenOsmIds.add(key)) {
                    accumulatedRawElements.put(elem)
                }
            }
        }

        for (ring in ringsToSearch) {
            val rStep = ring.radiusMeters
            val ringLabel = ring.ringLabel

            Log.i(TAG, "PROGRESSIVE RING START: $ringLabel ($rStep m)")
            currentDiag = currentDiag.copy(
                currentRing = ringLabel,
                searchDurationMs = System.currentTimeMillis() - startTime
            )
            onProgressUpdate(currentDiag)

            // --- STAGE A: PRIMARY STRUCTURED TAG QUERY ---
            val stageAStart = System.currentTimeMillis()
            Log.i(TAG, "STAGE A START (ring: $ringLabel, radius: ${rStep}m)")
            val primaryQuery = buildPrimaryTagQuery(latitude, longitude, rStep, category)
            val primaryResult = executeOverpassCluster(primaryQuery, "Stage A ($ringLabel)") { liveHost ->
                endpointUsed = liveHost
                currentDiag = currentDiag.copy(
                    currentRing = ringLabel,
                    endpointUsed = liveHost,
                    searchDurationMs = System.currentTimeMillis() - startTime
                )
                onProgressUpdate(currentDiag)
            }

            val stageADuration = System.currentTimeMillis() - stageAStart
            stageADurationTotal += stageADuration

            when (primaryResult) {
                is ClusterQueryResult.Success -> {
                    hadAnySuccessfulResponse = true
                    endpointUsed = primaryResult.endpoint
                    stageACount += primaryResult.elements.length()
                    Log.i(TAG, "STAGE A COUNT=${primaryResult.elements.length()} via $endpointUsed in $ringLabel")
                    mergeRawElements(primaryResult.elements)
                }
                is ClusterQueryResult.NetworkFailure -> {
                    Log.w(TAG, "FAILOVER: Stage A query network failure in $ringLabel: ${primaryResult.error}")
                    lastErrorDetail = primaryResult.error
                }
            }

            currentDiag = currentDiag.copy(
                currentRing = ringLabel,
                endpointUsed = endpointUsed,
                stageACount = stageACount,
                stageADurationMs = stageADurationTotal,
                mergedCount = accumulatedRawElements.length(),
                searchDurationMs = System.currentTimeMillis() - startTime
            )
            onProgressUpdate(currentDiag)

            // --- STAGE B: SUPPLEMENTARY REGIONAL NAME / OPERATOR QUERY ---
            // Run Stage B to capture regional and Indian administrative names
            val supplementaryQuery = buildSupplementaryNameQuery(latitude, longitude, rStep, category)
            if (supplementaryQuery.isNotBlank()) {
                val stageBStart = System.currentTimeMillis()
                Log.i(TAG, "STAGE B START (ring: $ringLabel, radius: ${rStep}m)")
                val suppResult = executeOverpassCluster(supplementaryQuery, "Stage B ($ringLabel)") { liveHost ->
                    endpointUsed = liveHost
                    currentDiag = currentDiag.copy(
                        currentRing = ringLabel,
                        endpointUsed = liveHost,
                        searchDurationMs = System.currentTimeMillis() - startTime
                    )
                    onProgressUpdate(currentDiag)
                }

                val stageBDuration = System.currentTimeMillis() - stageBStart
                stageBDurationTotal += stageBDuration

                when (suppResult) {
                    is ClusterQueryResult.Success -> {
                        hadAnySuccessfulResponse = true
                        endpointUsed = suppResult.endpoint
                        stageBCount += suppResult.elements.length()
                        Log.i(TAG, "STAGE B COUNT=${suppResult.elements.length()} via $endpointUsed in $ringLabel")
                        mergeRawElements(suppResult.elements)
                    }
                    is ClusterQueryResult.NetworkFailure -> {
                        Log.w(TAG, "FAILOVER: Stage B query network failure in $ringLabel: ${suppResult.error}")
                    }
                }

                currentDiag = currentDiag.copy(
                    currentRing = ringLabel,
                    endpointUsed = endpointUsed,
                    stageBCount = stageBCount,
                    stageBDurationMs = stageBDurationTotal,
                    mergedCount = accumulatedRawElements.length(),
                    searchDurationMs = System.currentTimeMillis() - startTime
                )
                onProgressUpdate(currentDiag)
            }
        }

        val mergedCount = accumulatedRawElements.length()
        Log.i(TAG, "MERGED COUNT=$mergedCount")

        // If no server responded successfully across all attempts (true network failure)
        if (!hadAnySuccessfulResponse) {
            Log.e(TAG, "SEARCH FAILED: ALL OVERPASS ENDPOINTS FAILED")
            val diag = currentDiag.copy(
                endpointUsed = "All endpoints failed",
                stageACount = stageACount,
                stageBCount = stageBCount,
                mergedCount = 0,
                afterCoordValidCount = 0,
                afterSemanticCount = 0,
                afterRadiusCount = 0,
                afterDedupCount = 0,
                finalValidCount = 0,
                nearestDistance = "N/A",
                stageADurationMs = stageADurationTotal,
                stageBDurationMs = stageBDurationTotal,
                searchDurationMs = System.currentTimeMillis() - startTime,
                finalState = "NETWORK_ERROR",
                errorMessage = lastErrorDetail ?: "All Overpass endpoints unreachable"
            )
            onProgressUpdate(diag)
            return@withContext PlacesResult.Error(
                message = "Unable to load nearby services.",
                isNetworkError = true,
                technicalDetail = lastErrorDetail,
                diagnostics = diag
            )
        }

        // --- STAGE C: COORDINATE EXTRACTION & VALIDATION ---
        var afterCoordValidCount = 0
        var afterSemanticCount = 0
        var afterRadiusCount = 0

        val candidateServices = mutableListOf<NearbyService>()

        for (i in 0 until accumulatedRawElements.length()) {
            val elem = accumulatedRawElements.optJSONObject(i) ?: continue
            val osmId = elem.optLong("id", 0L)
            val type = elem.optString("type", "node")

            var itemLat = elem.optDouble("lat", Double.NaN)
            var itemLng = elem.optDouble("lon", Double.NaN)

            // For ways and relations, extract center coordinates
            if (itemLat.isNaN() || itemLng.isNaN()) {
                val center = elem.optJSONObject("center")
                if (center != null) {
                    itemLat = center.optDouble("lat", Double.NaN)
                    itemLng = center.optDouble("lon", Double.NaN)
                }
            }

            // Fallback to bounds center if center object is missing
            if (itemLat.isNaN() || itemLng.isNaN()) {
                val bounds = elem.optJSONObject("bounds")
                if (bounds != null) {
                    val minlat = bounds.optDouble("minlat", Double.NaN)
                    val maxlat = bounds.optDouble("maxlat", Double.NaN)
                    val minlon = bounds.optDouble("minlon", Double.NaN)
                    val maxlon = bounds.optDouble("maxlon", Double.NaN)
                    if (!minlat.isNaN() && !maxlat.isNaN() && !minlon.isNaN() && !maxlon.isNaN()) {
                        itemLat = (minlat + maxlat) / 2.0
                        itemLng = (minlon + maxlon) / 2.0
                    }
                }
            }

            // Strictly validate extracted coordinates
            if (itemLat.isNaN() || itemLng.isNaN() || itemLat < -90.0 || itemLat > 90.0 || itemLng < -180.0 || itemLng > 180.0 || (itemLat == 0.0 && itemLng == 0.0)) {
                continue
            }
            afterCoordValidCount++

            val tags = elem.optJSONObject("tags") ?: JSONObject()
            val amenity = tags.optString("amenity").lowercase()
            val office = tags.optString("office").lowercase()
            val healthcare = tags.optString("healthcare").lowercase()
            val government = tags.optString("government").lowercase()
            val policeTag = tags.optString("police").lowercase()
            val emergency = tags.optString("emergency").lowercase()

            // Resolve clean name
            val name = resolveName(tags, amenity, office, healthcare, category)

            // --- STAGE D: STRICT SEMANTIC & FALSE-POSITIVE VALIDATION ---
            if (!isValidServiceItem(category, tags, name, amenity, office, healthcare, government, policeTag, emergency)) {
                continue
            }
            afterSemanticCount++

            // Determine inferred category
            val inferredCategory = resolveCategory(category, amenity, office, healthcare, government, policeTag, tags, name)

            // Resolve address from rich OSM address tags
            val address = resolveAddress(tags)

            // Resolve contact info
            val phone = tags.optString("phone").ifBlank {
                tags.optString("contact:phone").ifBlank {
                    tags.optString("emergency:phone").ifBlank {
                        tags.optString("contact:mobile")
                    }
                }
            }

            val website = tags.optString("website").ifBlank {
                tags.optString("contact:website").ifBlank {
                    tags.optString("url").ifBlank {
                        tags.optString("contact:facebook")
                    }
                }
            }

            val openingHours = tags.optString("opening_hours")

            // --- STAGE E: EXACT GPS DISTANCE CALCULATION ---
            val distanceM = calculateDistanceMeters(latitude, longitude, itemLat, itemLng)

            // --- STAGE F: STRICT RADIUS FILTER ---
            if (distanceM > clampedRadiusMeters) {
                continue
            }
            afterRadiusCount++

            val service = NearbyService(
                id = "osm_${type}_$osmId",
                name = name,
                category = inferredCategory,
                latitude = itemLat,
                longitude = itemLng,
                address = address,
                phone = cleanPhone(phone),
                website = cleanWebsite(website),
                distanceMeters = distanceM,
                openingHours = openingHours,
                osmType = type,
                source = "OpenStreetMap"
            )
            candidateServices.add(service)
        }

        Log.i(TAG, "COORD VALID COUNT=$afterCoordValidCount")
        Log.i(TAG, "SEMANTIC VALID COUNT=$afterSemanticCount")
        Log.i(TAG, "RADIUS VALID COUNT=$afterRadiusCount")

        // --- STAGE G: DEDUPLICATION ---
        val deduplicated = deduplicateServices(candidateServices)
        val afterDedupCount = deduplicated.size
        Log.i(TAG, "DEDUP COUNT=$afterDedupCount")

        // --- STAGE H: SORT NEAREST-FIRST (ASCENDING BY DISTANCE) ---
        // CRITICAL: Never trust Overpass ordering; the first item MUST be mathematically nearest!
        deduplicated.sortBy { it.distanceMeters }

        // Custom text search filtering if the user typed in the search bar
        val finalServices = if (cleanQuery.isNotBlank()) {
            val q = cleanQuery.lowercase()
            deduplicated.filter { svc ->
                svc.name.lowercase().contains(q) ||
                svc.category.displayName.lowercase().contains(q) ||
                svc.address.lowercase().contains(q)
            }
        } else {
            deduplicated
        }

        val finalCount = finalServices.size
        val nearest = finalServices.firstOrNull()
        val nearestDist = if (nearest != null) formatDistance(nearest.distanceMeters) else "N/A"
        val nearestName = nearest?.name ?: "None"
        val finalState = if (finalServices.isNotEmpty()) "SUCCESS" else "SUCCESS_EMPTY"
        val durationMs = System.currentTimeMillis() - startTime

        if (nearest != null) {
            Log.i(TAG, "NEAREST SERVICE:")
            Log.i(TAG, "name=${nearest.name}")
            Log.i(TAG, "distance=${nearest.distanceMeters}")
            Log.i(TAG, "lat=${nearest.latitude}")
            Log.i(TAG, "lon=${nearest.longitude}")
        }

        Log.i(TAG, "NEAREST=$nearestDist ($nearestName)")
        Log.i(TAG, "FINAL RESULT COUNT=$finalCount")
        if (finalServices.isNotEmpty()) {
            Log.i(TAG, "SEARCH SUCCESS")
        } else {
            Log.i(TAG, "SEARCH SUCCESS_EMPTY")
        }
        Log.i(TAG, "========================================")

        // Cache valid result sets (when not performing a custom text query)
        if (cleanQuery.isBlank() && finalServices.isNotEmpty()) {
            cache[cacheKey] = Pair(System.currentTimeMillis(), finalServices)
        }

        val cleanEndpoint = if (endpointUsed.contains("://")) {
            endpointUsed.substringAfter("://").substringBefore("/")
        } else if (endpointUsed.isNotBlank() && endpointUsed != "None") {
            endpointUsed
        } else {
            "overpass-api.de"
        }

        val diag = currentDiag.copy(
            category = category,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            currentRing = "0–${clampedRadiusMeters / 1000} km",
            endpointUsed = cleanEndpoint,
            stageACount = stageACount,
            stageBCount = stageBCount,
            mergedCount = mergedCount,
            afterCoordValidCount = afterCoordValidCount,
            afterSemanticCount = afterSemanticCount,
            afterRadiusCount = afterRadiusCount,
            afterDedupCount = afterDedupCount,
            finalValidCount = finalCount,
            nearestName = nearestName,
            nearestDistance = nearestDist,
            stageADurationMs = stageADurationTotal,
            stageBDurationMs = stageBDurationTotal,
            searchDurationMs = durationMs,
            finalState = finalState
        )
        onProgressUpdate(diag)

        return@withContext PlacesResult.Success(
            services = finalServices,
            category = category,
            totalFound = finalServices.size,
            diagnostics = diag
        )
    }

    private sealed class ClusterQueryResult {
        data class Success(val elements: JSONArray, val endpoint: String) : ClusterQueryResult()
        data class NetworkFailure(val error: String) : ClusterQueryResult()
    }

    /**
     * Executes an Overpass QL query sequentially over the multi-server failover cluster.
     * Guaranteed to terminate: catches timeouts and network errors per endpoint and advances to the next.
     */
    private fun executeOverpassCluster(
        overpassQuery: String,
        phase: String,
        onEndpointUpdate: (String) -> Unit
    ): ClusterQueryResult {
        var lastError = "No servers available"

        for ((index, endpoint) in OVERPASS_ENDPOINTS.withIndex()) {
            val hostName = endpoint.substringAfter("://").substringBefore("/")
            Log.i(TAG, "ENDPOINT START $hostName ($phase)")
            onEndpointUpdate(hostName)

            try {
                val formBody = "data=" + URLEncoder.encode(overpassQuery, "UTF-8")
                val request = Request.Builder()
                    .url(endpoint)
                    .post(formBody.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType()))
                    .header("User-Agent", "NyayaAI-SafetyApp/3.0 (Android; OpenStreetMap Overpass)")
                    .header("Accept", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val code = response.code
                Log.i(TAG, "HTTP RESPONSE $code from $hostName")

                if (!response.isSuccessful) {
                    val nextHost = OVERPASS_ENDPOINTS.getOrNull(index + 1)?.substringAfter("://")?.substringBefore("/") ?: "None"
                    Log.w(TAG, "ENDPOINT FAILED HTTP $code $hostName. FAILOVER → $nextHost")
                    onEndpointUpdate("$hostName (HTTP $code) → Failover...")
                    lastError = "HTTP $code from $hostName"
                    continue
                }

                val responseBody = response.body?.string() ?: ""
                if (responseBody.isBlank()) {
                    val nextHost = OVERPASS_ENDPOINTS.getOrNull(index + 1)?.substringAfter("://")?.substringBefore("/") ?: "None"
                    Log.w(TAG, "ENDPOINT EMPTY BODY $hostName. FAILOVER → $nextHost")
                    onEndpointUpdate("$hostName (Empty Body) → Failover...")
                    lastError = "Empty response from $hostName"
                    continue
                }

                val json = try {
                    JSONObject(responseBody)
                } catch (e: Exception) {
                    val nextHost = OVERPASS_ENDPOINTS.getOrNull(index + 1)?.substringAfter("://")?.substringBefore("/") ?: "None"
                    Log.w(TAG, "ENDPOINT INVALID JSON $hostName (${e.message}). FAILOVER → $nextHost")
                    onEndpointUpdate("$hostName (Invalid JSON) → Failover...")
                    lastError = "Invalid JSON from $hostName: ${e.message}"
                    continue
                }

                val elements = json.optJSONArray("elements") ?: JSONArray()
                return ClusterQueryResult.Success(elements, hostName)

            } catch (e: java.net.SocketTimeoutException) {
                val nextHost = OVERPASS_ENDPOINTS.getOrNull(index + 1)?.substringAfter("://")?.substringBefore("/") ?: "None"
                Log.w(TAG, "ENDPOINT TIMEOUT $hostName. FAILOVER → $nextHost")
                onEndpointUpdate("$hostName (TIMEOUT) → Failover...")
                lastError = "Timeout from $hostName"
            } catch (e: java.io.IOException) {
                val nextHost = OVERPASS_ENDPOINTS.getOrNull(index + 1)?.substringAfter("://")?.substringBefore("/") ?: "None"
                Log.w(TAG, "ENDPOINT NETWORK ERROR $hostName (${e.message}). FAILOVER → $nextHost")
                onEndpointUpdate("$hostName (Net Error) → Failover...")
                lastError = "Network error from $hostName: ${e.message}"
            } catch (e: Exception) {
                val nextHost = OVERPASS_ENDPOINTS.getOrNull(index + 1)?.substringAfter("://")?.substringBefore("/") ?: "None"
                Log.w(TAG, "ENDPOINT EXCEPTION $hostName (${e.message}). FAILOVER → $nextHost")
                onEndpointUpdate("$hostName (Error) → Failover...")
                lastError = "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        Log.e(TAG, "SEARCH FAILED: ALL OVERPASS ENDPOINTS FAILED")
        return ClusterQueryResult.NetworkFailure(lastError)
    }

    /**
     * Builds the high-speed indexed primary tag query (Stage A).
     */
    private fun buildPrimaryTagQuery(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        category: SafetyCategory
    ): String {
        val r = radiusMeters
        val clauses = mutableListOf<String>()

        when (category) {
            SafetyCategory.POLICE -> {
                clauses.add("""nwr["amenity"="police"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["office"="police"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["police"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["government"="police"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["building"="police"](around:$r,$lat,$lng);""")
            }

            SafetyCategory.HOSPITAL -> {
                clauses.add("""nwr["amenity"="hospital"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["healthcare"="hospital"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["amenity"="clinic"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["healthcare"="clinic"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["amenity"="doctors"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["healthcare"="doctor"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["amenity"="pharmacy"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["healthcare"="pharmacy"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["building"="hospital"](around:$r,$lat,$lng);""")
            }

            SafetyCategory.FIRE -> {
                clauses.add("""nwr["amenity"="fire_station"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["emergency"="fire_station"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["emergency"="fire_service"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["building"="fire_station"](around:$r,$lat,$lng);""")
            }

            SafetyCategory.COURT -> {
                clauses.add("""nwr["amenity"="courthouse"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["office"="court"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["office"="judiciary"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["government"="judicial"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["court"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["building"="courthouse"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["building"="court"](around:$r,$lat,$lng);""")
            }

            SafetyCategory.LEGAL_AID -> {
                clauses.add("""nwr["office"="lawyer"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["amenity"="lawyer"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["legal_aid"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["service:legal_aid"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["service"="legal_aid"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["office"="legal"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["office"="legal_services"](around:$r,$lat,$lng);""")
            }

            SafetyCategory.GOVERNMENT -> {
                clauses.add("""nwr["office"="government"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["government"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["amenity"="townhall"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["amenity"="government_office"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["amenity"="community_centre"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["building"="government"](around:$r,$lat,$lng);""")
            }

            SafetyCategory.ALL -> {
                clauses.add("""nwr["amenity"~"^(police|hospital|pharmacy|clinic|doctors|fire_station|courthouse|townhall|government_office|community_centre|lawyer)$"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["healthcare"~"^(hospital|clinic|doctor|pharmacy)$"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["office"~"^(lawyer|court|judiciary|government|legal|legal_services|police)$"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["government"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["legal_aid"](around:$r,$lat,$lng);""")
                clauses.add("""nwr["emergency"~"^(fire_station|fire_service)$"](around:$r,$lat,$lng);""")
            }
        }

        val timeoutSec = if (radiusMeters > 20000) 20 else 12
        return """
            [out:json][timeout:$timeoutSec];
            (
              ${clauses.joinToString("\n  ")}
            );
            out center tags;
        """.trimIndent()
    }

    /**
     * Builds the lightweight supplementary name-based query for Indian & regional OSM records (Stage B).
     */
    private fun buildSupplementaryNameQuery(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        category: SafetyCategory
    ): String {
        val r = radiusMeters
        val clauses = mutableListOf<String>()

        when (category) {
            SafetyCategory.POLICE -> {
                clauses.add("""nwr["name"~"Police Station|Police|PS|Thana|Thanah|Chowki|Police Outpost|Outpost|Commissionerate|Kavalthurai|காவல் நிலையம்|காவல்|போலீஸ்|പോലീസ്",i](around:$r,$lat,$lng);""")
                clauses.add("""nwr["official_name"~"Police|Thana|Chowki|Commissionerate|Kavalthurai",i](around:$r,$lat,$lng);""")
                clauses.add("""nwr["operator"~"Police|Kavalthurai",i](around:$r,$lat,$lng);""")
            }

            SafetyCategory.FIRE -> {
                clauses.add("""nwr["name"~"Fire Station|Fire Brigade|Fire & Rescue|Fire Rescue|Fire Service|District Fire Office|Fire Department|தீயணைப்பு நிலையம்|தீயணைப்பு|Fire Station Office",i](around:$r,$lat,$lng);""")
                clauses.add("""nwr["official_name"~"Fire Station|Fire Brigade|Fire Rescue",i](around:$r,$lat,$lng);""")
            }

            SafetyCategory.COURT -> {
                clauses.add("""nwr["name"~"District Court|District Courts|Sessions Court|High Court|Magistrate Court|Magistrate|Taluk Court|Civil Court|Criminal Court|Family Court|Sub Court|Munsif Court|Judicial Magistrate|Tribunal|Lok Adalat|Nyayalaya|Court Complex|நீதிமன்றம்|மாவட்ட நீதிமன்றம்|நீதிமன்ற",i](around:$r,$lat,$lng);""")
                clauses.add("""nwr["official_name"~"Court|Courts|District Court|Sessions Court|High Court|Magistrate|Judicial|Tribunal|Nyayalaya",i](around:$r,$lat,$lng);""")
            }

            SafetyCategory.LEGAL_AID -> {
                clauses.add("""nwr["name"~"DLSA|TLSC|SLSA|Legal Aid|Legal Aid Clinic|Legal Services Authority|District Legal Services Authority|Taluk Legal Services Committee|State Legal Services Authority|Bar Association|Advocate|Advocates|Free Legal Aid|Lawyer|Legal Services|Vakil|சட்ட உதவி|வழக்கறிஞர்",i](around:$r,$lat,$lng);""")
                clauses.add("""nwr["official_name"~"Legal Aid|DLSA|TLSC|SLSA|Legal Services Authority|Bar Association|Advocate",i](around:$r,$lat,$lng);""")
            }

            SafetyCategory.GOVERNMENT -> {
                clauses.add("""nwr["name"~"Government Office|Government Offices|Collectorate|Collector Office|District Collectorate|District Collector Office|Taluk Office|Tahsildar Office|Tahsildar|Revenue Office|RDO|BDO|Block Development Office|Panchayat Office|Village Panchayat|Municipal Office|Municipality|Corporation Office|VAO|Village Administrative Office|Seva Kendra|e-Sevai|eSeva|e-Seva|அரசு அலுவலகம்|வட்டாட்சியர் அலுவலகம்|வருவாய் அலுவலகம்|ஊராட்சி அலுவலகம்|மாவட்ட ஆட்சியர்|பேரூராட்சி",i](around:$r,$lat,$lng);""")
                clauses.add("""nwr["official_name"~"Collectorate|Taluk Office|Tahsildar|Panchayat|Municipal|Revenue Office|Government Office|VAO|BDO|RDO",i](around:$r,$lat,$lng);""")
                clauses.add("""nwr["short_name"~"VAO|BDO|RDO|TALUK|COLLECTORATE",i](around:$r,$lat,$lng);""")
                clauses.add("""nwr["operator"~"Government|Panchayat|Municipality|Revenue Department|Tahsildar|TN Govt|Government of Tamil Nadu",i](around:$r,$lat,$lng);""")
            }

            SafetyCategory.HOSPITAL -> {
                clauses.add("""nwr["name"~"Hospital|Clinic|Nursing Home|Dispensary|Medical Center|Healthcare|Pharmacy|Chemist",i](around:$r,$lat,$lng);""")
            }

            SafetyCategory.ALL -> {
                clauses.add("""nwr["name"~"Police|Thana|Courthouse|Court|Fire Station|Hospital|Clinic|DLSA|Legal Aid|Collectorate|Panchayat|Taluk Office|Tahsildar",i](around:$r,$lat,$lng);""")
            }
        }

        if (clauses.isEmpty()) return ""

        val timeoutSec = if (radiusMeters > 20000) 20 else 12
        return """
            [out:json][timeout:$timeoutSec];
            (
              ${clauses.joinToString("\n  ")}
            );
            out center tags;
        """.trimIndent()
    }

    /**
     * Strict semantic validation and false-positive filtering.
     * Prevents incorrect classifications such as Marriage Halls appearing under Government
     * or Sports/Food Courts appearing under Courts.
     */
    private fun isValidServiceItem(
        category: SafetyCategory,
        tags: JSONObject,
        name: String,
        amenity: String,
        office: String,
        healthcare: String,
        government: String,
        policeTag: String,
        emergency: String
    ): Boolean {
        val lowerName = name.lowercase()

        // Exclude general commercial shops/businesses
        val shop = tags.optString("shop").lowercase()
        if (shop.isNotBlank() && shop !in listOf("chemist", "pharmacy", "medical_supply")) {
            if (amenity !in listOf("police", "hospital", "courthouse", "fire_station") && office !in listOf("court", "judiciary", "government", "lawyer")) {
                return false
            }
        }

        when (category) {
            SafetyCategory.GOVERNMENT -> {
                // CRITICAL FALSE-POSITIVE FILTER:
                // Strictly exclude Marriage Halls, Banquet Halls, Function Halls, Hotels, Temples, Private Commercial entities
                val rejectedWords = listOf(
                    "marriage hall", "wedding hall", "function hall", "banquet hall", "convention hall", "party hall",
                    "kalyana mandapam", "kalyana mandap", "mandapam", "mahal", "kalyana mahal",
                    "hotel", "restaurant", "lodge", "resort", "cafe", "bakery", "canteen", "sweet stall", "mess", "bar", "pub",
                    "school", "college", "university", "institute", "academy", "tuition", "vidyalaya", "matriculation",
                    "temple", "church", "mosque", "ashram", "mandir", "kovil", "dargah", "gurudwara",
                    "sports centre", "sports center", "stadium", "club", "gym", "fitness",
                    "private office", "commercial office", "travels", "enterprises", "agencies", "associates", "finance", "bank", "atm",
                    "real estate", "consultancy", "printing", "press", "tailor", "studio", "beauty parlour", "salon", "jewellers"
                )

                if (rejectedWords.any { lowerName.contains(it) }) {
                    return false
                }

                // If tagged as community_centre, verify it is truly a civic/government building and not a private hall
                if (amenity == "community_centre") {
                    val isCivic = lowerName.contains("panchayat") || lowerName.contains("samudaya") || lowerName.contains("village") ||
                            lowerName.contains("seva") || lowerName.contains("civic") || lowerName.contains("corporation") ||
                            lowerName.contains("municipal") || lowerName.contains("nagar") || government.isNotBlank()
                    if (!isCivic) return false
                }

                // Accept genuine government tags or verified administration titles
                val isGovTagged = office == "government" || government.isNotBlank() || amenity in listOf("townhall", "government_office") || tags.optString("building") == "government"
                val hasGovKeywords = lowerName.contains("collectorate") || lowerName.contains("panchayat") || lowerName.contains("municipal") ||
                        lowerName.contains("taluk") || lowerName.contains("secretariat") || lowerName.contains("tahsildar") ||
                        lowerName.contains("tehsildar") || lowerName.contains("revenue office") || lowerName.contains("vao") ||
                        lowerName.contains("bdo") || lowerName.contains("rdo") || lowerName.contains("seva kendra") ||
                        lowerName.contains("eseva") || lowerName.contains("e-seva") || lowerName.contains("government office") ||
                        lowerName.contains("dept of") || lowerName.contains("department of") || lowerName.contains("ministry") ||
                        lowerName.contains("sub registrar") || lowerName.contains("registrar office") || lowerName.contains("treasury") ||
                        lowerName.contains("pwd") || lowerName.contains("tneb") || lowerName.contains("eb office") || lowerName.contains("post office") ||
                        lowerName.contains("passport seva") || lowerName.contains("aadhaar") || lowerName.contains("அரசு") ||
                        lowerName.contains("வட்டாட்சியர்") || lowerName.contains("வருவாய்") || lowerName.contains("ஊராட்சி") ||
                        lowerName.contains("பேரூராட்சி") || lowerName.contains("மாநகராட்சி") || lowerName.contains("மாவட்ட ஆட்சியர்")

                return isGovTagged || hasGovKeywords
            }

            SafetyCategory.COURT -> {
                // CRITICAL FALSE-POSITIVE FILTER:
                // Exclude food courts, sports courts, and courtyards
                val rejectedCourtWords = listOf(
                    "food court", "food courts",
                    "sports court", "tennis court", "basketball court", "badminton court", "squash court", "volleyball court",
                    "play court", "shuttle court", "turf", "cricket",
                    "court hall", "court yard", "courtyard", "shopping court", "plaza court", "marriott"
                )

                if (rejectedCourtWords.any { lowerName.contains(it) }) {
                    return false
                }

                val isCourtTagged = amenity == "courthouse" || office in listOf("court", "judiciary") || government == "judicial" || tags.has("court") || tags.optString("building") in listOf("courthouse", "court")
                val hasCourtKeywords = lowerName.contains("district court") || lowerName.contains("sessions court") || lowerName.contains("high court") ||
                        lowerName.contains("magistrate") || lowerName.contains("munsif") || lowerName.contains("tribunal") ||
                        lowerName.contains("lok adalat") || lowerName.contains("nyayalaya") || lowerName.contains("nyayalayam") ||
                        lowerName.contains("family court") || lowerName.contains("civil court") || lowerName.contains("criminal court") ||
                        lowerName.contains("taluk court") || lowerName.contains("sub court") || lowerName.contains("court complex") ||
                        lowerName.contains("நீதிமன்றம்") || lowerName.contains("நீதிமன்ற")

                return isCourtTagged || hasCourtKeywords
            }

            SafetyCategory.POLICE -> {
                val rejectedPoliceWords = listOf("canteen", "bazaar", "bakery", "tea stall", "mess", "stores", "travels", "petrol", "quarters")
                if (rejectedPoliceWords.any { lowerName.contains(it) }) {
                    return false
                }

                val isPoliceTagged = amenity == "police" || office == "police" || government == "police" || policeTag.isNotBlank() || tags.optString("building") == "police"
                val hasPoliceKeywords = lowerName.contains("police station") || lowerName.contains("police") || lowerName.contains("thana") ||
                        lowerName.contains("chowki") || lowerName.contains("commissionerate") || lowerName.contains("kavalthurai") ||
                        lowerName.contains("outpost") || lowerName.contains("காவல்") || lowerName.contains("போலீஸ்") || lowerName.contains("പോലീസ്")

                return isPoliceTagged || hasPoliceKeywords
            }

            SafetyCategory.FIRE -> {
                val isFireTagged = amenity == "fire_station" || emergency in listOf("fire_station", "fire_service") || tags.optString("building") == "fire_station"
                val hasFireKeywords = lowerName.contains("fire station") || lowerName.contains("fire brigade") || lowerName.contains("fire rescue") ||
                        lowerName.contains("fire service") || lowerName.contains("fire department") || lowerName.contains("தீயணைப்பு")

                return isFireTagged || hasFireKeywords
            }

            SafetyCategory.HOSPITAL -> {
                val isHospitalTagged = amenity in listOf("hospital", "clinic", "pharmacy", "doctors") || healthcare in listOf("hospital", "clinic", "pharmacy", "doctor") || tags.optString("building") == "hospital"
                val hasHospitalKeywords = lowerName.contains("hospital") || lowerName.contains("clinic") || lowerName.contains("nursing home") ||
                        lowerName.contains("healthcare") || lowerName.contains("pharmacy") || lowerName.contains("dispensary") || lowerName.contains("medical center") || lowerName.contains("chemist")

                return isHospitalTagged || hasHospitalKeywords
            }

            SafetyCategory.LEGAL_AID -> {
                val rejectedLegalWords = listOf("xerox", "photo copy", "documentation shop", "dTP", "real estate", "notary stamp vendor")
                if (rejectedLegalWords.any { lowerName.contains(it) }) {
                    return false
                }

                val isLegalTagged = office in listOf("lawyer", "legal", "legal_services") || amenity == "lawyer" || tags.has("legal_aid") || tags.has("service:legal_aid") || tags.optString("service") == "legal_aid"
                val hasLegalKeywords = lowerName.contains("legal aid") || lowerName.contains("dlsa") || lowerName.contains("tlsc") || lowerName.contains("slsa") ||
                        lowerName.contains("bar association") || lowerName.contains("advocate") || lowerName.contains("lawyer") || lowerName.contains("legal service") ||
                        lowerName.contains("legal services authority") || lowerName.contains("free legal aid") || lowerName.contains("vakil") || lowerName.contains("சட்ட உதவி") || lowerName.contains("வழக்கறிஞர்")

                return isLegalTagged || hasLegalKeywords
            }

            SafetyCategory.ALL -> {
                // Validate against all sub-filters
                return isValidServiceItem(SafetyCategory.POLICE, tags, name, amenity, office, healthcare, government, policeTag, emergency) ||
                       isValidServiceItem(SafetyCategory.FIRE, tags, name, amenity, office, healthcare, government, policeTag, emergency) ||
                       isValidServiceItem(SafetyCategory.COURT, tags, name, amenity, office, healthcare, government, policeTag, emergency) ||
                       isValidServiceItem(SafetyCategory.LEGAL_AID, tags, name, amenity, office, healthcare, government, policeTag, emergency) ||
                       isValidServiceItem(SafetyCategory.GOVERNMENT, tags, name, amenity, office, healthcare, government, policeTag, emergency) ||
                       isValidServiceItem(SafetyCategory.HOSPITAL, tags, name, amenity, office, healthcare, government, policeTag, emergency)
            }
        }
    }

    private fun resolveName(
        tags: JSONObject,
        amenity: String,
        office: String,
        healthcare: String,
        category: SafetyCategory
    ): String {
        val name = tags.optString("name").ifBlank {
            tags.optString("name:en").ifBlank {
                tags.optString("official_name").ifBlank {
                    tags.optString("alt_name").ifBlank {
                        tags.optString("operator").ifBlank {
                            tags.optString("branch")
                        }
                    }
                }
            }
        }

        if (name.isNotBlank()) return name

        // Descriptive fallback title from OSM tags
        return when {
            amenity == "police" || office == "police" -> "Police Station"
            amenity == "hospital" || healthcare == "hospital" -> "Hospital / Medical Center"
            amenity == "pharmacy" || healthcare == "pharmacy" -> "Pharmacy / Chemist"
            amenity in listOf("clinic", "doctors") || healthcare in listOf("clinic", "doctor") -> "Healthcare Clinic"
            amenity == "fire_station" -> "Fire & Rescue Station"
            amenity == "courthouse" || office in listOf("court", "judiciary") -> "Courthouse / Judicial Complex"
            office in listOf("lawyer", "legal", "legal_services") || amenity == "lawyer" -> "Legal Aid / Advocate Office"
            amenity in listOf("townhall", "government_office") || office == "government" -> "Government Administrative Office"
            amenity == "community_centre" -> "Civic Community Center"
            else -> category.singularName
        }
    }

    private fun resolveCategory(
        requestedCategory: SafetyCategory,
        amenity: String,
        office: String,
        healthcare: String,
        government: String,
        policeTag: String,
        tags: JSONObject,
        name: String
    ): SafetyCategory {
        val lowerName = name.lowercase()
        return when {
            amenity == "police" || office == "police" || government == "police" || policeTag.isNotBlank() ||
            lowerName.contains("police") || lowerName.contains("thana") || lowerName.contains("chowki") || lowerName.contains("commissionerate") || lowerName.contains("kavalthurai") || lowerName.contains("காவல்") ->
                SafetyCategory.POLICE

            amenity == "fire_station" || lowerName.contains("fire station") || lowerName.contains("fire brigade") || lowerName.contains("fire rescue") || lowerName.contains("fire service") || lowerName.contains("தீயணைப்பு") ->
                SafetyCategory.FIRE

            amenity == "courthouse" || office in listOf("court", "judiciary") || government == "judicial" ||
            (lowerName.contains("court") && !lowerName.contains("food") && !lowerName.contains("tennis") && !lowerName.contains("badminton") && !lowerName.contains("cricket") && !lowerName.contains("courtyard")) ||
            lowerName.contains("nyayalaya") || lowerName.contains("nyayalayam") || lowerName.contains("tribunal") || lowerName.contains("magistrate") || lowerName.contains("munsif") || lowerName.contains("lok adalat") || lowerName.contains("நீதிமன்றம்") ->
                SafetyCategory.COURT

            tags.has("legal_aid") || tags.has("service:legal_aid") || tags.optString("service") == "legal_aid" || lowerName.contains("legal aid") || lowerName.contains("dlsa") || lowerName.contains("tlsc") || lowerName.contains("slsa") ||
            lowerName.contains("bar association") || office in listOf("lawyer", "legal", "legal_services") || amenity == "lawyer" || lowerName.contains("advocate") || lowerName.contains("lawyer") || lowerName.contains("legal service") || lowerName.contains("vakil") || lowerName.contains("சட்ட உதவி") ->
                SafetyCategory.LEGAL_AID

            amenity in listOf("hospital", "clinic", "doctors", "pharmacy") || healthcare in listOf("hospital", "clinic", "doctor", "pharmacy") ||
            lowerName.contains("hospital") || lowerName.contains("clinic") || lowerName.contains("nursing home") || lowerName.contains("pharmacy") || lowerName.contains("medical") || lowerName.contains("dispensary") ->
                SafetyCategory.HOSPITAL

            amenity in listOf("townhall", "government_office", "community_centre") || office == "government" || government.isNotBlank() ||
            lowerName.contains("collectorate") || lowerName.contains("panchayat") || lowerName.contains("municipal") || lowerName.contains("secretariat") || lowerName.contains("taluk") || lowerName.contains("tahsildar") || lowerName.contains("bhavan") || lowerName.contains("seva kendra") || lowerName.contains("அரசு") || lowerName.contains("வட்டாட்சியர்") || lowerName.contains("மாவட்ட ஆட்சியர்") ->
                SafetyCategory.GOVERNMENT

            requestedCategory != SafetyCategory.ALL -> requestedCategory
            else -> SafetyCategory.GOVERNMENT
        }
    }

    /**
     * Extracts a clean, human-readable address from available OSM address tags.
     */
    private fun resolveAddress(tags: JSONObject): String {
        val full = tags.optString("addr:full")
        if (full.isNotBlank()) return full.trim()

        val parts = mutableListOf<String>()
        val houseNumber = tags.optString("addr:housenumber").ifBlank { tags.optString("addr:housename") }
        val street = tags.optString("addr:street").ifBlank { tags.optString("addr:road") }
        val place = tags.optString("addr:place").ifBlank {
            tags.optString("addr:suburb").ifBlank {
                tags.optString("addr:neighbourhood")
            }
        }
        val city = tags.optString("addr:city").ifBlank {
            tags.optString("addr:town").ifBlank {
                tags.optString("addr:village")
            }
        }
        val district = tags.optString("addr:district")
        val state = tags.optString("addr:state")
        val postcode = tags.optString("addr:postcode")

        if (houseNumber.isNotBlank() && street.isNotBlank()) {
            parts.add("$houseNumber $street")
        } else if (street.isNotBlank()) {
            parts.add(street)
        }
        if (place.isNotBlank() && place != street && place != city) parts.add(place)
        if (city.isNotBlank()) parts.add(city)
        if (district.isNotBlank() && district != city) parts.add(district)
        if (state.isNotBlank() && state != district) parts.add(state)
        if (postcode.isNotBlank()) parts.add(postcode)

        return if (parts.isNotEmpty()) parts.joinToString(", ") else "Address unavailable from OpenStreetMap"
    }

    private fun cleanPhone(phone: String): String {
        if (phone.isBlank()) return ""
        return if (phone.any { it.isDigit() }) phone.trim() else ""
    }

    private fun cleanWebsite(website: String): String {
        if (website.isBlank()) return ""
        val w = website.trim()
        return if (w.contains(".") && !w.startsWith("mailto:")) w else ""
    }

    /**
     * Deduplicates services using OSM ID as primary key and 40-meter spatial proximity
     * with category and name comparison to preserve separate legitimate facilities.
     */
    private fun deduplicateServices(services: List<NearbyService>): MutableList<NearbyService> {
        val deduplicated = mutableListOf<NearbyService>()
        val seenIds = mutableSetOf<String>()

        for (svc in services) {
            if (seenIds.add(svc.id)) {
                val isDuplicate = deduplicated.any { existing ->
                    if (existing.category != svc.category) {
                        false
                    } else {
                        val distM = calculateDistanceMeters(existing.latitude, existing.longitude, svc.latitude, svc.longitude)
                        if (distM < 40f) {
                            val nameA = existing.name.trim().lowercase()
                            val nameB = svc.name.trim().lowercase()
                            nameA == nameB || nameA.contains(nameB) || nameB.contains(nameA)
                        } else {
                            false
                        }
                    }
                }
                if (!isDuplicate) {
                    deduplicated.add(svc)
                }
            }
        }
        return deduplicated
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
        }
    }
}
