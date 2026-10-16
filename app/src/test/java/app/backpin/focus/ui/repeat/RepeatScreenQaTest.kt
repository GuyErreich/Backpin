package app.backpin.focus.ui.repeat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.runOffTestThread
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.settings.SettingsScreen
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import androidx.compose.material3.Surface
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RepeatScreenQaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun writesHomeSectionFrame() {
        val home = env(hour = 8)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                home.useCases.createRepeat("Take meds", RepeatBand.MORNING, 1, today)
                home.useCases.createRepeat("Water plants", RepeatBand.NOON, 1, today)
                home.useCases.createRepeat("Make bed", RepeatBand.MORNING, 1, today)
                home.useCases.createRepeat("Vitamins", RepeatBand.MORNING, 1, today)
                home.useCases.markRepeatDone(idOf(home, "Make bed"), today)
                home.useCases.markRepeatDone(idOf(home, "Vitamins"), today)
            }
            showHome(home.viewModel)
            await {
                home.viewModel.uiState.value.ready &&
                    orderedRepeatRows(home.viewModel.todaysRepeats.value).size == 4
            }
            writePng("repeats-home-section.png")
        } finally {
            home.close()
        }
    }

    @Test
    fun writesHomeMissedFrame() {
        val missed = env(hour = 20)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                missed.useCases.createRepeat("Brush teeth", RepeatBand.NIGHT, 1, today)
                missed.useCases.createRepeat("Water plants", RepeatBand.NOON, 1, today)
            }
            showHome(missed.viewModel)
            await {
                missed.viewModel.uiState.value.ready &&
                    missed.viewModel.todaysRepeats.value.noon.single().status == RepeatDayStatus.MISSED
            }
            writePng("repeats-home-missed.png")
        } finally {
            missed.close()
        }
    }

    @Test
    fun writesSheetFrame() {
        compose.setContent {
            FocusStackTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = FocusColors.Midnight) {
                    RepeatSheet(
                        draft = RepeatDraft(title = "Take meds"),
                        onDismiss = {},
                        onCreate = { _, _, _, _ -> },
                        onUpdate = { _, _, _, _, _, _, _ -> },
                        onArchive = {},
                        recognizer = SilentQaRecognizer,
                    )
                }
            }
        }
        compose.waitForIdle()
        writePng("repeats-sheet.png")
    }

    @Test
    fun writesSettingsFrame() {
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("backpin_repeat_ui", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        compose.setContent {
            FocusStackTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = FocusColors.Midnight) {
                    SettingsScreen(
                        preferences = UserPreferences(),
                        onNudgeSelected = { _, _ -> },
                        onQuietHours = { _, _ -> },
                        onCurrentNotification = {},
                        onTodayLimit = {},
                        onAddPhrase = {},
                        onRenamePhrase = { _, _ -> },
                        onRemovePhrase = {},
                        onBandTimes = {},
                        canScheduleExactAlarms = { false },
                    )
                }
            }
        }
        compose.waitForIdle()
        writePng("repeats-settings.png")
    }

    private fun showHome(viewModel: FocusStackViewModel) {
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            FocusStackTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = FocusColors.Midnight) {
                    HomeScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenFocus = {},
                        onOpenInbox = {},
                        onOpenLater = {},
                        onOpenScheduled = {},
                        onOpenDone = {},
                    )
                }
            }
        }
    }

    private fun writePng(name: String) {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val dir = outputDir()
        val file = File(dir, name)
        val bitmap = captureBitmap()
        try {
            FileOutputStream(file).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG compress failed for $name" }
            }
        } finally {
            bitmap.recycle()
        }
        assertTrue("$name is ${file.length()} bytes", file.length() > 1_000)
    }

    private fun captureBitmap(): Bitmap {
        try {
            return compose.onRoot().captureToImage().asAndroidBitmap()
        } catch (_: Throwable) {
            val view = compose.activity.findViewById<View>(android.R.id.content)
            val width = view.width.coerceAtLeast(1)
            val height = view.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return bitmap
        }
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private suspend fun idOf(
        env: Env,
        title: String,
    ): String = env.useCases.observeRepeats().first().single { it.title == title }.id

    private fun env(hour: Int): Env {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "repeat-qa-${UUID.randomUUID()}"),
                    clock = clockAt(hour),
                ),
            )
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        return Env(useCases, database, viewModel, store)
    }

    private data class Env(
        val useCases: FocusUseCases,
        val database: FocusStackDatabase,
        val viewModel: FocusStackViewModel,
        val store: ViewModelStore,
    ) {
        fun close() {
            store.clear()
            database.close()
        }
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)

        fun clockAt(hour: Int): AppClock {
            val millis = DAY.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return AppClock { millis }
        }

        fun outputDir(): File {
            val artifacts = File("/opt/cursor/artifacts")
            if (artifacts.exists() || artifacts.mkdirs()) {
                if (artifacts.canWrite()) return artifacts
            }
            return File("app/build/outputs/repeat-ui-qa").also { it.mkdirs() }
        }
    }
}

private object SilentQaRecognizer : UtteranceRecognizer {
    override fun available(): Boolean = false

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean = false

    override fun cancel() = Unit
}
