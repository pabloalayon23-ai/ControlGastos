# ControlGastos release hardening.
# Keep JXL model classes used reflectively by the legacy XLS reader.
-keep class jxl.** { *; }
-dontwarn jxl.**
