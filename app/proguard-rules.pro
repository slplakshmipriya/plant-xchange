# Garden Swap ProGuard rules.
# The default android-optimize rules already cover Firebase (AAR consumer
# rules); the entries below protect our own classes that are touched by
# reflection or by name from other SDK machinery.

# API model/DTO classes: parsed and serialized by hand today, but kept
# whole so a future reflection-based consumer (or a Firebase payload
# mapping) can't silently strip fields in release builds.
-keep class com.gardenswap.test.api.** { *; }

# Firebase components instantiated via the manifest / reflection.
-keep class com.gardenswap.test.notifications.GardenSwapMessagingService { *; }
-keep class com.gardenswap.test.GardenSwapApp { *; }
