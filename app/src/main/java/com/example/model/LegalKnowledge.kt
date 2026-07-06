package com.example.model

data class LegalTopic(
    val id: Int,
    val title: String,
    val category: String,
    val keywords: List<String> = emptyList(),
    val summary: String,
    val next_steps: List<String> = emptyList(),
    val official_authority: String,
    val official_source: String,
    val official_source_url: String
)

data class EmergencyService(
    val id: Int,
    val name: String,
    val category: String, // "Police", "Hospital", "Legal Aid"
    val lat: Double,
    val lng: Double,
    val address: String,
    val phone: String,
    val icon: String,
    val rating: Double,
    val distance: Double = 0.0,
    val details: String = "" // e.g. "24/7 Emergency Room", "E-FIR Desk Active"
)
