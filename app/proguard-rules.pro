# ProGuard / R8 rules for the release build.
#
# Compose and Kotlin ship their own consumer rules, so this file is
# intentionally minimal for now. Add app-specific keep rules here as the
# project grows (e.g. when serializing game state).

# Keep line numbers for readable crash reports, hide the original source name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
