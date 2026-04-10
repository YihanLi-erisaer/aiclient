# sherpa-onnx JNI 必须保留
-keep class com.k2fsa.sherpa.** { *; }

# 保留 native 方法（非常关键）
-keepclasseswithmembernames class * {
    native <methods>;
}

# Kotlin 序列化
-keep class kotlinx.serialization.** { *; }

# Ktor（你在用）
-keep class io.ktor.** { *; }

# Compose（一般不用，但保险）
-keep class androidx.compose.** { *; }

# R8: 依赖里引用 JDK java.lang.management，Android 无此类；忽略缺失（见 minifyReleaseWithR8/missing_rules.txt）
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
