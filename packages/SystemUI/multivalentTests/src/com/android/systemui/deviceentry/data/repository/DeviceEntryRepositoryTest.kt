package com.android.systemui.deviceentry.data.repository

import android.content.pm.UserInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.widget.LockPatternUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntryRepositoryTest : SysuiTestCase() {

    @Mock private lateinit var lockPatternUtils: LockPatternUtils

    private val kosmos = testKosmos()
    private val userRepository = FakeUserRepository()

    private lateinit var underTest: DeviceEntryRepository

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        userRepository.setUserInfos(USER_INFOS)
        runBlocking { userRepository.setSelectedUserInfo(USER_INFOS[0]) }

        underTest =
            DeviceEntryRepositoryImpl(
                backgroundDispatcher = kosmos.testDispatcher,
                userRepository = userRepository,
                lockPatternUtils = lockPatternUtils,
            )
        kosmos.runCurrent()
    }

    @Test
    fun isLockscreenEnabled() =
        kosmos.runTest {
            whenever(lockPatternUtils.isLockScreenDisabled(USER_INFOS[0].id)).thenReturn(false)
            whenever(lockPatternUtils.isLockScreenDisabled(USER_INFOS[1].id)).thenReturn(true)

            userRepository.setSelectedUserInfo(USER_INFOS[0])
            assertThat(underTest.isLockscreenEnabled()).isTrue()

            userRepository.setSelectedUserInfo(USER_INFOS[1])
            assertThat(underTest.isLockscreenEnabled()).isFalse()
        }

    companion object {
        private val USER_INFOS =
            listOf(
                UserInfo(/* id= */ 100, /* name= */ "First user", /* flags= */ 0),
                UserInfo(/* id= */ 101, /* name= */ "Second user", /* flags= */ 0),
            )
    }
}
