package app.rengaine.ui.settings

import com.mikepenz.aboutlibraries.Libs
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La liste exportée par AboutLibraries se lit, et chaque licence a son texte. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LicencesListTest {

    private val libs = Libs.Builder().withJson(File("src/main/res/raw/aboutlibraries.json").readText()).build()

    @Test fun everyLibraryHasALicenceWithItsText() {
        assertTrue(libs.libraries.size > 150)
        for (library in libs.libraries) {
            assertTrue("${library.uniqueId} sans licence", library.licenses.isNotEmpty())
            for (licence in library.licenses) {
                assertTrue("${licence.name} sans texte", !licence.licenseContent.isNullOrBlank())
            }
        }
    }

    @Test fun includesTheTypeface() {
        assertTrue(libs.libraries.any { it.uniqueId == "bricolage-grotesque" && it.licenses.any { l -> l.hash == "OFL-1.1" } })
    }
}
