plugins {
    // AGP 9 自带 built-in Kotlin，不再需要 org.jetbrains.kotlin.android 插件。
    // compose 编译器插件版本需与 AGP 运行时所依赖的 KGP 版本（2.2.10）一致。
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
