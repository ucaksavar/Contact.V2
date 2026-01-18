package com.example.contactv2.model

data class FakeCallConfig(
    val name: String = "",
    val number: String = "",
    val delaySeconds: Int = 10,
    val ringtoneUri: String? = null
)
