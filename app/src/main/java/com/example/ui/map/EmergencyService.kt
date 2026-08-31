package com.example.ui.map

data class EmergencyService(
    val id: String,
    val name: String,
    val category: String, // Police, Hospital, Court, Fire Station, Legal Aid, Government
    val address: String,
    val lat: Double,
    val lng: Double,
    val phone: String = "",
    val website: String = "",
    val openStatus: String = "Open 24/7",
    val rating: Double = 4.6,
    val isVerified: Boolean = true,
    val osmId: Long? = null,
    val googlePlaceId: String? = null
)
