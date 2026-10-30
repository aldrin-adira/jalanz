import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Pure Kotlin/JVM library with no Android dependency (e.g. :core:toll, shared with the server). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JalanzBuild.JAVA_VERSION
            targetCompatibility = JalanzBuild.JAVA_VERSION
        }
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(JalanzBuild.JAVA_VERSION.toString()))
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
