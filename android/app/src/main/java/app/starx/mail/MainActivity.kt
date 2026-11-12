package app.starx.mail

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import app.starx.mail.core.network.AuthInterceptor
import app.starx.mail.core.security.BiometricAuthManager
import app.starx.mail.core.util.Constants
import app.starx.mail.data.repository.dataStore
import app.starx.mail.domain.repository.AuthRepository
import app.starx.mail.domain.repository.EmailRepository
import app.starx.mail.presentation.navigation.Screen
import app.starx.mail.presentation.navigation.StarXNavGraph
import app.starx.mail.presentation.theme.StarXTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var emailRepository: EmailRepository

    @Inject
    lateinit var biometricAuthManager: BiometricAuthManager

    @Inject
    lateinit var authInterceptor: AuthInterceptor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StarXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isCheckingAuth by remember { mutableStateOf(true) }
                    var startDestination by remember { mutableStateOf(Screen.Onboarding.route) }
                    var isBiometricLocked by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        val token = authRepository.getStoredToken()
                        val isConnected = !token.isNullOrBlank()

                        val prefs = dataStore.data.first()
                        val biometricEnabled = prefs[androidx.datastore.preferences.core.booleanPreferencesKey(Constants.KEY_BIOMETRIC_ENABLED)] ?: false

                        if (isConnected) {
                            startDestination = Screen.Inbox.route
                            if (biometricEnabled && biometricAuthManager.canAuthenticate()) {
                                isBiometricLocked = true
                                biometricAuthManager.prompt(
                                    activity = this@MainActivity,
                                    onSuccess = {
                                        isBiometricLocked = false
                                        isCheckingAuth = false
                                    },
                                    onError = {
                                        // Keep locked or fallback to PIN
                                    }
                                )
                                return@LaunchedEffect
                            }
                        } else {
                            startDestination = Screen.Onboarding.route
                        }

                        isCheckingAuth = false
                    }

                    if (isCheckingAuth || isBiometricLocked) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val navController = rememberNavController()

                        LaunchedEffect(authInterceptor) {
                            authInterceptor.sessionExpiredEvents.collect {
                                authRepository.disconnect()
                                navController.navigate(Screen.Onboarding.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }

                        // Handle incoming notification intent navigation
                        LaunchedEffect(intent) {
                            val emailId = intent?.getStringExtra("EXTRA_EMAIL_ID")
                            val threadId = intent?.getStringExtra("EXTRA_THREAD_ID")
                            if (!emailId.isNullOrBlank() && !threadId.isNullOrBlank()) {
                                navController.navigate(Screen.EmailDetails.createRoute(emailId, threadId))
                            }
                        }

                        StarXNavGraph(
                            navController = navController,
                            startDestination = startDestination,
                            emailRepository = emailRepository
                        )
                    }
                }
            }
        }
    }
}
