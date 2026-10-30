import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** SDK levels and identity shared by every module. spec/06-android-auto.md §1.1, spec/00-README.md */
internal object JalanzBuild {
    const val PACKAGE = "io.github.aldrinadira.jalanz"
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37

    // [ASSUMPTION] 06 §1.1: Android 9; re-check Android Auto's minimum before release.
    const val MIN_SDK = 28

    val JAVA_VERSION = JavaVersion.VERSION_17
}

/** `:core:routing-client` -> `io.github.aldrinadira.jalanz.core.routingclient` */
internal val Project.jalanzNamespace: String
    get() = JalanzBuild.PACKAGE + path.replace(':', '.').replace("-", "")

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()
