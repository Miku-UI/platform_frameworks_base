package com.android.systemui.keyguard

import android.content.ComponentCallbacks2
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.android.systemui.utils.GlobalWindowManager
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
@SmallTest
class ResourceTrimmerTest : SysuiTestCase() {
    val kosmos = testKosmos()

    private val testScope = kosmos.testScope
    private val featureFlags = kosmos.fakeFeatureFlagsClassic
    private val keyguardTransitionRepository = kosmos.fakeKeyguardTransitionRepository

    @Mock private lateinit var globalWindowManager: GlobalWindowManager
    private lateinit var resourceTrimmer: ResourceTrimmer

    @Rule @JvmField public val setFlagsRule = SetFlagsRule()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        resourceTrimmer =
            ResourceTrimmer(
                keyguardTransitionInteractor = kosmos.keyguardTransitionInteractor,
                globalWindowManager = globalWindowManager,
                applicationScope = testScope.backgroundScope,
                bgDispatcher = kosmos.testDispatcher,
            )
        resourceTrimmer.start()
    }

    @Test
    @DisableSceneContainer
    fun keyguardTransitionsToGone_trimsFontCache() =
        testScope.runTest {
            keyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                testScope,
            )
            verify(globalWindowManager, times(1))
                .trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            verifyNoMoreInteractions(globalWindowManager)
        }

    @Test
    @EnableSceneContainer
    fun keyguardTransitionsToGone_trimsFontCache_scene_container() =
        testScope.runTest {
            kosmos.sceneInteractor.snapToScene(Scenes.Lockscreen, "reason")
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            runCurrent()

            kosmos.sceneInteractor.changeScene(Scenes.Gone, "")
            kosmos.setSceneTransition(Idle(Scenes.Gone))

            verify(globalWindowManager, times(1))
                .trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            verifyNoMoreInteractions(globalWindowManager)
        }
}
