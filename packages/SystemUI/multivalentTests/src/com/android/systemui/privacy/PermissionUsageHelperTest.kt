package com.android.systemui.privacy

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.flags.Flags
import android.os.UserHandle
import android.permission.PermissionUsageHelper
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
class PermissionUsageHelperTest : SysuiTestCase() {
    @get:Rule val mocks = MockitoJUnit.rule()

    private val TEST_PKG = "com.test.pkg"
    private val TEST_UID = 12345
    private val TEST_PKG_2 = "com.test.pkg2"
    private val TEST_UID_2 = 54321
    private val TEST_USER_HANDLE = UserHandle.getUserHandleForUid(TEST_UID)
    private val TEST_DEVICE_ID = "test_device_id"

    @Mock private lateinit var mAppOpsManager: AppOpsManager
    @Mock private lateinit var mActivityManager: ActivityManager
    @Mock private lateinit var mLocationManager: LocationManager
    @Mock private lateinit var packageManager: PackageManager

    private lateinit var mHelper: PermissionUsageHelper

    @Before
    fun setup() {
        mContext.addMockSystemService(AppOpsManager::class.java, mAppOpsManager)
        mContext.addMockSystemService(ActivityManager::class.java, mActivityManager)
        mContext.addMockSystemService(LocationManager::class.java, mLocationManager)
        mContext.setMockPackageManager(packageManager)
        mHelper = PermissionUsageHelper(mContext)
    }

    @Test
    fun listDoesNotContainBackgroundApp() {
        val attributedOpEntry = mock(AppOpsManager.AttributedOpEntry::class.java)
        // Ensure the last fg access time is older than the running threshold.
        val oldAccessTime = System.currentTimeMillis() - 20000L
        `when`(attributedOpEntry.getLastAccessForegroundTime(anyInt())).thenReturn(oldAccessTime)
        `when`(attributedOpEntry.isRunning).thenReturn(false)

        val opEntry =
            AppOpsManager.OpEntry(
                AppOpsManager.OP_FINE_LOCATION,
                AppOpsManager.MODE_ALLOWED,
                mapOf(null as String? to attributedOpEntry),
            )

        val packageOps = AppOpsManager.PackageOps(TEST_PKG, TEST_UID, listOf(opEntry))
        val ops = listOf(packageOps)

        `when`(mAppOpsManager.getPackagesForOps(any(Array<String>::class.java), eq(TEST_DEVICE_ID)))
            .thenReturn(ops)

        // Ensure it is a non-system app. A non-system app has no special permission flags.
        `when`(packageManager.getPermissionFlags(any(), any(), any()))
            .thenReturn(
                PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED or
                    PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
            )

        // Ensure it is a background app by setting the process importance to a background state.
        val processInfo = ActivityManager.RunningAppProcessInfo()
        processInfo.uid = TEST_UID
        processInfo.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        `when`(mActivityManager.runningAppProcesses).thenReturn(listOf(processInfo))

        val usages = mHelper.getOpUsageDataForIndicatorsByDevice(true, TEST_DEVICE_ID)
        assertTrue("Usage data should be empty for background apps", usages.isEmpty())
    }

    @Test
    fun listContainsBackgroundApp_withinHoldingPeriod() {
        val attributedOpEntry = mock(AppOpsManager.AttributedOpEntry::class.java)
        // Last fg access time is within the holding period (10s for location).
        val recentAccessTime = System.currentTimeMillis() - 5000L
        `when`(attributedOpEntry.getLastAccessForegroundTime(anyInt())).thenReturn(recentAccessTime)
        `when`(attributedOpEntry.getLastAccessTime(anyInt())).thenReturn(recentAccessTime)
        `when`(attributedOpEntry.isRunning).thenReturn(false)

        val opEntry =
            AppOpsManager.OpEntry(
                AppOpsManager.OP_FINE_LOCATION,
                AppOpsManager.MODE_ALLOWED,
                mapOf(null as String? to attributedOpEntry),
            )

        val packageOps = AppOpsManager.PackageOps(TEST_PKG, TEST_UID, listOf(opEntry))
        val ops = listOf(packageOps)

        `when`(mAppOpsManager.getPackagesForOps(any(Array<String>::class.java), eq(TEST_DEVICE_ID)))
            .thenReturn(ops)

        // Ensure it is a non-system app. A non-system app has no special permission flags.
        `when`(packageManager.getPermissionFlags(any(), any(), any()))
            .thenReturn(
                PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED or
                    PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
            )

        // Ensure it is a background app by setting the process importance to a background state.
        val processInfo = ActivityManager.RunningAppProcessInfo()
        processInfo.uid = TEST_UID
        processInfo.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        `when`(mActivityManager.runningAppProcesses).thenReturn(listOf(processInfo))

        val usages = mHelper.getOpUsageDataForIndicatorsByDevice(true, TEST_DEVICE_ID)
        assertFalse(
            "Usage data should not be empty for background apps within holding period",
            usages.isEmpty(),
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun listDoesNotContainBackgroundApp_outsideHoldingPeriod() {
        val attributedOpEntry = mock(AppOpsManager.AttributedOpEntry::class.java)
        // Last fg access is outside holding period (10s) but inside recent period (20s).
        val oldAccessTime = System.currentTimeMillis() - 15000L
        `when`(attributedOpEntry.getLastAccessForegroundTime(anyInt())).thenReturn(oldAccessTime)
        `when`(attributedOpEntry.getLastAccessTime(anyInt())).thenReturn(oldAccessTime)
        `when`(attributedOpEntry.isRunning).thenReturn(false)

        val opEntry =
            AppOpsManager.OpEntry(
                AppOpsManager.OP_FINE_LOCATION,
                AppOpsManager.MODE_ALLOWED,
                mapOf(null as String? to attributedOpEntry),
            )

        val packageOps = AppOpsManager.PackageOps(TEST_PKG, TEST_UID, listOf(opEntry))
        val ops = listOf(packageOps)

        `when`(mAppOpsManager.getPackagesForOps(any(Array<String>::class.java), eq(TEST_DEVICE_ID)))
            .thenReturn(ops)

        // Ensure it is a non-system app. A non-system app has no special permission flags.
        `when`(packageManager.getPermissionFlags(any(), any(), any()))
            .thenReturn(
                PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED or
                    PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
            )

        // Ensure it is a background app by setting the process importance to a background state.
        val processInfo = ActivityManager.RunningAppProcessInfo()
        processInfo.uid = TEST_UID
        processInfo.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        `when`(mActivityManager.runningAppProcesses).thenReturn(listOf(processInfo))

        val usages = mHelper.getOpUsageDataForIndicatorsByDevice(true, TEST_DEVICE_ID)
        // The test name is now misleading. With the new logic, usage within 20s should be shown
        // for apps that have gone to the background.
        assertFalse(
            "Usage data should be shown for background apps within recent period",
            usages.isEmpty(),
        )
        val usage = usages[0]
        assertFalse("Usage should be recent, not active", usage.isActive())
    }

    @Test
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun listContainsForegroundAppRecentlyUsed() {
        val attributedOpEntry = mock(AppOpsManager.AttributedOpEntry::class.java)
        // Access is outside running period (10s) but inside recent period (20s).
        val recentAccessTime = System.currentTimeMillis() - 15000L
        `when`(attributedOpEntry.getLastAccessTime(anyInt())).thenReturn(recentAccessTime)
        `when`(attributedOpEntry.isRunning).thenReturn(false)

        val opEntry =
            AppOpsManager.OpEntry(
                AppOpsManager.OP_FINE_LOCATION,
                AppOpsManager.MODE_ALLOWED,
                mapOf(null as String? to attributedOpEntry),
            )

        val packageOps = AppOpsManager.PackageOps(TEST_PKG, TEST_UID, listOf(opEntry))
        val ops = listOf(packageOps)

        `when`(mAppOpsManager.getPackagesForOps(any(Array<String>::class.java), eq(TEST_DEVICE_ID)))
            .thenReturn(ops)

        // Ensure it is a non-system app. A non-system app has no special permission flags.
        `when`(packageManager.getPermissionFlags(any(), any(), any()))
            .thenReturn(
                PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED or
                    PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
            )

        // Ensure it is a foreground app.
        val processInfo = ActivityManager.RunningAppProcessInfo()
        processInfo.uid = TEST_UID
        processInfo.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        `when`(mActivityManager.runningAppProcesses).thenReturn(listOf(processInfo))

        val usages = mHelper.getOpUsageDataForIndicatorsByDevice(true, TEST_DEVICE_ID)
        assertFalse("Usage data should not be empty for foreground apps", usages.isEmpty())
        val usage = usages[0]
        assertFalse("Usage should be recent, not active", usage.isActive())
    }

    @Test
    @EnableFlags(Flags.FLAG_LOCATION_INDICATORS_ENABLED)
    fun listContainsRecentLocationAndActiveMic() {
        // App 1 using location, used recently in the foreground, now in the background
        val locationAttributedOpEntry = mock(AppOpsManager.AttributedOpEntry::class.java)
        val locationAccessTime = System.currentTimeMillis() - 15000L // 15s ago
        `when`(locationAttributedOpEntry.getLastAccessForegroundTime(anyInt()))
            .thenReturn(locationAccessTime)
        `when`(locationAttributedOpEntry.getLastAccessTime(anyInt())).thenReturn(locationAccessTime)
        `when`(locationAttributedOpEntry.isRunning).thenReturn(false)
        val locationOpEntry =
            AppOpsManager.OpEntry(
                AppOpsManager.OP_FINE_LOCATION,
                AppOpsManager.MODE_ALLOWED,
                mapOf(null as String? to locationAttributedOpEntry),
            )
        val locationPackageOps =
            AppOpsManager.PackageOps(TEST_PKG, TEST_UID, listOf(locationOpEntry))

        // App 2 using mic, currently running in the foreground
        val micAttributedOpEntry = mock(AppOpsManager.AttributedOpEntry::class.java)
        `when`(micAttributedOpEntry.isRunning).thenReturn(true)
        val micOpEntry =
            AppOpsManager.OpEntry(
                AppOpsManager.OP_RECORD_AUDIO,
                AppOpsManager.MODE_ALLOWED,
                mapOf(null as String? to micAttributedOpEntry),
            )
        val micPackageOps = AppOpsManager.PackageOps(TEST_PKG_2, TEST_UID_2, listOf(micOpEntry))

        `when`(mAppOpsManager.getPackagesForOps(any(Array<String>::class.java), eq(TEST_DEVICE_ID)))
            .thenReturn(listOf(locationPackageOps, micPackageOps))

        // Ensure both are non-system apps
        `when`(packageManager.getPermissionFlags(any(), any(), any()))
            .thenReturn(
                PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED or
                    PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
            )

        // App 1 is background, app 2 is foreground
        val processInfo1 = ActivityManager.RunningAppProcessInfo()
        processInfo1.uid = TEST_UID
        processInfo1.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        val processInfo2 = ActivityManager.RunningAppProcessInfo()
        processInfo2.uid = TEST_UID_2
        processInfo2.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        `when`(mActivityManager.runningAppProcesses).thenReturn(listOf(processInfo1, processInfo2))

        val usages = mHelper.getOpUsageDataForIndicatorsByDevice(true, TEST_DEVICE_ID)
        assertTrue("Should have 2 usages", usages.size == 2)

        val locationUsage = usages.find { it.packageName == TEST_PKG }
        val micUsage = usages.find { it.packageName == TEST_PKG_2 }

        assertTrue("Location usage should be present", locationUsage != null)
        assertFalse("Location usage should be recent, not active", locationUsage!!.isActive())

        assertTrue("Mic usage should be present", micUsage != null)
        assertTrue("Mic usage should be active", micUsage!!.isActive())
    }
}
