// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.ExactAlarmAccess
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.ReleaseWakeUp
import app.backpin.focus.domain.RepeatScheduleSync
import app.backpin.focus.domain.Task
import app.backpin.focus.notifications.BandScheduler
import app.backpin.focus.notifications.RepeatNotificationCoordinator
import app.backpin.focus.notifications.ScheduleReleaseScheduler
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.ui.ChromeState
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.FocusStackViewModelFactory
import app.backpin.focus.ui.FocusUiState
import app.backpin.focus.ui.deriveFocusUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.rules.ExternalResource
import java.time.ZoneId
import java.util.UUID

/**
 * In-memory [FocusStackDatabase]. Main-thread queries are allowed because
 * Robolectric unit tests call Room from the test thread via `runTest` or
 * `runBlocking`.
 */
fun inMemoryFocusDatabase(context: Context): FocusStackDatabase =
    Room
        .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/**
 * One fixture clock and zone for the real repository, use-cases, repeats
 * service, ViewModel factory, band scheduler, repeat notifications, schedule
 * release, and [deriveFocusUi]. Prevents the clock/zone mismatch that hid a
 * past day-refresh bug.
 */
class TestFocusFixture private constructor(
    val context: Context,
    val clock: FakeAppClock,
    val database: FocusStackDatabase,
    val preferences: PreferencesDataStore,
    val repository: FocusRepositoryImpl,
    val useCases: FocusUseCases,
    val repeats: RepeatsService,
) : AutoCloseable {
    val zone: ZoneId
        get() = clock.zone

    fun zoneProvider(): () -> ZoneId = clock::zoneId

    fun viewModelFactory(): FocusStackViewModelFactory =
        FocusStackViewModelFactory(
            useCases = useCases,
            repeatsApi = repeats,
            clock = clock,
        )

    fun viewModel(): FocusStackViewModel =
        FocusStackViewModel(
            useCases = useCases,
            repeatsApi = repeats,
            clock = clock,
        )

    /**
     * Builds the ViewModel through its factory and clears the store after
     * [block], matching the project tap-test rule.
     */
    inline fun <T> withViewModel(block: (FocusStackViewModel) -> T): T {
        val store = ViewModelStore()
        return try {
            val viewModel =
                ViewModelProvider(store, viewModelFactory())[FocusStackViewModel::class.java]
            block(viewModel)
        } finally {
            store.clear()
        }
    }

    fun bandScheduler(
        scope: CoroutineScope,
        workManager: WorkManager,
    ): BandScheduler =
        BandScheduler(
            context = context,
            repository = repository,
            scope = scope,
            clock = clock,
            workManager = workManager,
            zone = clock::zoneId,
        )

    fun repeatNotifications(scheduler: BandScheduler): RepeatNotificationCoordinator =
        RepeatNotificationCoordinator(
            context = context,
            repository = repository,
            scheduler = scheduler,
            clock = clock,
            zone = clock::zoneId,
        )

    fun scheduleReleaseScheduler(workManager: WorkManager): ScheduleReleaseScheduler =
        ScheduleReleaseScheduler(
            workManager = workManager,
            nowEpochMillis = clock::nowEpochMillis,
        )

    fun deriveUi(
        snapshot: FocusSnapshot,
        chrome: ChromeState = sampleChrome(),
    ): FocusUiState =
        deriveFocusUi(
            snapshot = snapshot,
            chrome = chrome,
            localDate = clock.localDate(),
            localTime = clock.localTime(),
        )

    suspend fun seedFullToday(titles: List<String> = defaultTodayTitles()): List<Task> =
        repository.seedFullToday(titles)

    override fun close() {
        database.close()
    }

    companion object {
        /**
         * Builds the repository and [RepeatsService] from one [clock] so they
         * cannot silently use different instants or zones.
         */
        fun open(
            context: Context = ApplicationProvider.getApplicationContext(),
            clock: FakeAppClock = FakeAppClock(),
            preferencesName: String = "focus-test-${UUID.randomUUID()}",
            schedule: RepeatScheduleSync = RepeatScheduleSync {},
            exactAlarms: ExactAlarmAccess = ExactAlarmAccess { false },
            wakeUp: ReleaseWakeUp = ReleaseWakeUp {},
            repeatsContext: Context? = null,
            repeatsScope: CoroutineScope = CoroutineScope(SupervisorJob()),
        ): TestFocusFixture {
            val database = inMemoryFocusDatabase(context)
            val preferences = PreferencesDataStore(context, preferencesName)
            val repository = FocusRepositoryImpl(database, preferences, clock)
            val useCases = FocusUseCases(repository, wakeUp)
            val repeats =
                RepeatsService(
                    repository = repository,
                    schedule = schedule,
                    exactAlarms = exactAlarms,
                    clock = clock,
                    context = repeatsContext,
                    zone = clock::zoneId,
                    scope = repeatsScope,
                )
            return TestFocusFixture(
                context = context,
                clock = clock,
                database = database,
                preferences = preferences,
                repository = repository,
                useCases = useCases,
                repeats = repeats,
            )
        }
    }
}

inline fun <T> useTestFocus(
    context: Context = ApplicationProvider.getApplicationContext(),
    clock: FakeAppClock = FakeAppClock(),
    block: (TestFocusFixture) -> T,
): T = TestFocusFixture.open(context = context, clock = clock).use(block)

/** JUnit 4 rule that opens [TestFocusFixture] and closes the in-memory database. */
class InMemoryFocusRule(
    private val clockFactory: () -> FakeAppClock = { FakeAppClock() },
) : ExternalResource() {
    lateinit var fixture: TestFocusFixture
        private set

    val clock: FakeAppClock
        get() = fixture.clock

    val repository: FocusRepositoryImpl
        get() = fixture.repository

    val repeats: RepeatsService
        get() = fixture.repeats

    val useCases: FocusUseCases
        get() = fixture.useCases

    override fun before() {
        fixture = TestFocusFixture.open(clock = clockFactory())
    }

    override fun after() {
        if (this::fixture.isInitialized) {
            fixture.close()
        }
    }
}
