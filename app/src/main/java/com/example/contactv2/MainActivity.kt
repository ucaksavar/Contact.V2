package com.example.contactv2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.contactv2.ui.ContactApp
import com.example.contactv2.ui.theme.ContactV2Theme
import com.example.contactv2.viewmodel.ContactViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ContactViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Splash ekranını hemen kapatarak uygulama iskeletinin (DeepCharcoal) gelmesini sağlıyoruz
        splashScreen.setKeepOnScreenCondition { false }
        
        enableEdgeToEdge()
        
        setContent {
            val context = LocalContext.current
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.READ_CONTACTS] == true) {
                    viewModel.fetchContacts(context)
                }
            }

            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CALL_LOG // Gerçek geçmiş için izin şart
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                
                val missingPermissions = permissionsToRequest.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }

                if (missingPermissions.isNotEmpty()) {
                    permissionLauncher.launch(missingPermissions.toTypedArray())
                } else {
                    viewModel.fetchContacts(context)
                }
            }

            ContactV2Theme {
                ContactApp(viewModel)
            }
        }
    }
}
