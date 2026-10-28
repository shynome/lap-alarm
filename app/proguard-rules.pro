# Add project specific ProGuard rules here.

# 保留行号信息，混淆后崩溃堆栈仍可定位；文件名归一化以减小体积
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 说明：本项目无反射 / Class.forName / getIdentifier 调用，
# Compose、androidx.media 均自带 consumer rules，无需额外 -keep。
