# PKCS12 loads JCA algorithms and SPI implementations by name. Keep the provider
# entry points intact so release builds can read modern keystores on older Android.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
# The provider also registers optional LDAP certificate stores, unused by this app.
-dontwarn javax.naming.**
