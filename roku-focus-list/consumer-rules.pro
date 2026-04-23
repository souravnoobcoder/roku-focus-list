# roku-focus-list consumer ProGuard rules
# Keep all public API classes
-keep public class com.rokufocus.RokuFocusListState { *; }
-keep public class com.rokufocus.RokuFocusConfig { *; }
-keep public class com.rokufocus.RokuColumnRowConfig { *; }
-keep public class com.rokufocus.RokuAnimationSpec { *; }
-keep public class com.rokufocus.RokuItemScope { *; }
-keep public class com.rokufocus.RokuLazyColumnScope { *; }
-keep public class com.rokufocus.DefaultRokuFocusConfig { *; }

# Keep public composable functions
-keep class com.rokufocus.RokuApiKt { *; }
-keep class com.rokufocus.RokuFocusListStateKt { *; }
-keep class com.rokufocus.RokuFocusHighlightKt { *; }
-keep class com.rokufocus.RokuKeyHandlerKt { *; }
