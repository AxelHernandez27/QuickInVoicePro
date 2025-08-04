    package com.example.workadministration

    import android.content.Context
    import android.util.Log
    import com.google.android.gms.auth.GoogleAuthUtil
    import com.google.android.gms.auth.api.signin.GoogleSignIn
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.withContext

    object TokenHelper {

        suspend fun refreshGoogleToken(context: Context): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return@withContext null
                    val scope = "oauth2:https://www.googleapis.com/auth/calendar"
                    val token = GoogleAuthUtil.getToken(context, account, scope)

                    // Guarda token actualizado en SharedPreferences
                    val prefs = context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("ACCESS_TOKEN", token).apply()

                    Log.d("TokenHelper", "Token actualizado: $token")
                    token
                } catch (e: Exception) {
                    Log.e("TokenHelper", "Error al refrescar token", e)
                    null
                }
            }
        }
    }
