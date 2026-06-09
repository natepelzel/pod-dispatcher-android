# kotlinx.serialization — keep generated serializers for schema models
-keepclassmembers class dev.poddispatcher.model.** {
    *** Companion;
}
-keepclasseswithmembers class dev.poddispatcher.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
