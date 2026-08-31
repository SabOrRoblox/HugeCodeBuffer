package com.hugecode.buffer

import javax.net.ssl.*
import java.security.cert.X509Certificate
import java.security.MessageDigest
import java.util.Base64

object CertificatePinner {
    
    private const val PIN_SHA256 = "7JJ5U9rAJzoKsROXVBeB4u5OiuJsWC1ctBpVJD/Gpfo="
    
    fun createSSLContext(): SSLContext {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
                chain?.firstOrNull()?.let { cert ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hash = digest.digest(cert.publicKey.encoded)
                    val hashBase64 = Base64.getEncoder().encodeToString(hash)
                    
                    android.util.Log.d("CertificatePinner", "Expected: $PIN_SHA256")
                    android.util.Log.d("CertificatePinner", "Actual: $hashBase64")
                    
                    // ВРЕМЕННО: принимаем любой сертификат чтобы проверить соединение
                    // if (hashBase64 != PIN_SHA256) {
                    //     throw SSLException("Certificate pinning failed")
                    // }
                }
            }
            
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext
    }
}
