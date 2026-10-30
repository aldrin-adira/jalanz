import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            namespace = JalanzBuild.PACKAGE
            compileSdk = JalanzBuild.COMPILE_SDK
            defaultConfig {
                applicationId = JalanzBuild.PACKAGE
                minSdk = JalanzBuild.MIN_SDK
                targetSdk = JalanzBuild.TARGET_SDK
            }
            compileOptions {
                sourceCompatibility = JalanzBuild.JAVA_VERSION
                targetCompatibility = JalanzBuild.JAVA_VERSION
            }
        }
    }
}
