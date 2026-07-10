# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.http.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**

# Jsoup
-keeppackagenames org.jsoup.nodes

# Apache HTTP legacy transport (via google-http-client-apache-v2) references
# LDAP/Kerberos classes not present on Android. Unused by DriveMark — silence R8.
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
