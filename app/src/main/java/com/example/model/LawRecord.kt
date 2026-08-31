package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "laws")
data class LawRecord(
    @PrimaryKey val lawId: String = "",
    val category: String = "General",
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val reference: String = "",
    val status: String = "active", // "active" or "inactive"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = "Admin Authority",
    val lastUpdatedBy: String = "Admin Authority",
    val officialAuthority: String = "Ministry of Law and Justice",
    val officialSourceUrl: String = "",
    val keywords: String = ""
) {
    fun toLegalTopic(): LegalTopic {
        val numericId = lawId.filter { it.isDigit() }.toIntOrNull() ?: Math.abs(lawId.hashCode())
        val keywordList = if (keywords.isBlank()) {
            listOf(title.lowercase(), category.lowercase())
        } else {
            keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return LegalTopic(
            id = numericId,
            title = title,
            category = category,
            keywords = keywordList,
            summary = if (description.isNotBlank()) description else content,
            next_steps = listOf("Check official government portal", "Consult a registered legal practitioner"),
            official_authority = officialAuthority.ifBlank { "Ministry of Law and Justice" },
            official_source = reference.ifBlank { "The Constitution / Gazette of India" },
            official_source_url = officialSourceUrl
        )
    }
}
