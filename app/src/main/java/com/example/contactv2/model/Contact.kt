package com.example.contactv2.model

import android.net.Uri

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: Uri? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val expiryTimestamp: Long? = null,
    val rawContactId: Long? = null
)
