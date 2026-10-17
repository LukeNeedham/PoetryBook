# Project-specific R8 rules. Compose and AndroidX ship their own consumer rules.

# Shared library (com.lukeneedham:common) serializes these to its log/crash files; untested with R8.
-keep class com.lukeneedham.common.logger.LogEntry { *; }
-keep class com.lukeneedham.common.crash.CrashEntry { *; }
