import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = jalanzNamespace
            compileSdk = JalanzBuild.COMPILE_SDK
            defaultConfig {
                minSdk = JalanzBuild.MIN_SDK
            }
            compileOptions {
                sourceCompatibility = JalanzBuild.JAVA_VERSION
                targetCompatibility = JalanzBuild.JAVA_VERSION
            }
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
