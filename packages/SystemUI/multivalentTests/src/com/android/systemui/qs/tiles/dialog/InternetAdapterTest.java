package com.android.systemui.qs.tiles.dialog;

import static android.platform.test.flag.junit.FlagsParameterization.allCombinationsOf;

import static com.android.systemui.qs.tiles.dialog.InternetDetailsContentController.MAX_WIFI_ENTRY_COUNT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.filters.SmallTest;

import com.android.systemui.Flags;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.flags.SceneContainerFlagParameterizationKt;
import com.android.systemui.qs.flags.QsDetailedView;
import com.android.systemui.qs.flags.QsWifiConfig;
import com.android.systemui.res.R;
import com.android.wifitrackerlib.WifiEntry;

import kotlinx.coroutines.CoroutineScope;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class InternetAdapterTest extends SysuiTestCase {

    private static final String WIFI_KEY = "Wi-Fi_Key";
    private static final String WIFI_TITLE = "Wi-Fi Title";
    private static final String WIFI_SUMMARY = "Wi-Fi Summary";
    private static final int GEAR_ICON_RES_ID = R.drawable.ic_settings_24dp;
    private static final int LOCK_ICON_RES_ID = R.drawable.ic_friction_lock_closed;

    @Rule
    public MockitoRule mRule = MockitoJUnit.rule();
    @Spy
    private Context mSpyContext = mContext;
    @Mock
    CoroutineScope mScope;

    @Mock
    private WifiEntry mInternetWifiEntry;
    @Mock
    private List<WifiEntry> mWifiEntries;
    @Mock
    private WifiEntry mWifiEntry;
    @Mock
    private InternetDetailsContentController mInternetDetailsContentController;
    @Mock
    private Drawable mWifiDrawable;
    @Mock
    private Drawable mGearIcon;
    @Mock
    private Drawable mLockIcon;

    private TestableResources mTestableResources;
    private InternetAdapter mInternetAdapter;
    private InternetAdapter.InternetViewHolder mViewHolder;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        List<FlagsParameterization> aconfigCombinations = allCombinationsOf(
                Flags.FLAG_QS_WIFI_CONFIG,
                Flags.FLAG_QS_TILE_DETAILED_VIEW
        );

        return SceneContainerFlagParameterizationKt.andSceneContainer(aconfigCombinations);
    }

    public InternetAdapterTest(FlagsParameterization flags) {
        super();
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        mTestableResources = mContext.getOrCreateTestableResources();
        when(mInternetWifiEntry.getTitle()).thenReturn(WIFI_TITLE);
        when(mInternetWifiEntry.getSummary(false)).thenReturn(WIFI_SUMMARY);
        when(mInternetWifiEntry.isDefaultNetwork()).thenReturn(true);
        when(mInternetWifiEntry.hasInternetAccess()).thenReturn(true);
        when(mWifiEntry.getKey()).thenReturn(WIFI_KEY);
        when(mWifiEntry.getTitle()).thenReturn(WIFI_TITLE);
        when(mWifiEntry.getSummary(false)).thenReturn(WIFI_SUMMARY);

        // Stub mutate() for mock Drawables
        when(mWifiDrawable.mutate()).thenReturn(mWifiDrawable);
        when(mGearIcon.mutate()).thenReturn(mGearIcon);
        when(mLockIcon.mutate()).thenReturn(mLockIcon);

        mInternetAdapter = new InternetAdapter(mInternetDetailsContentController, mScope,
                QsDetailedView.isEnabled());
        mViewHolder = mInternetAdapter.onCreateViewHolder(new LinearLayout(mContext), 0);
        mInternetAdapter.setWifiEntries(Arrays.asList(mWifiEntry), 1 /* wifiEntriesCount */);
    }

    @Test
    public void getItemCount_returnWifiEntriesCount() {
        for (int i = 0; i < MAX_WIFI_ENTRY_COUNT; i++) {
            mInternetAdapter.setWifiEntries(mWifiEntries, i /* wifiEntriesCount */);

            assertThat(mInternetAdapter.getItemCount()).isEqualTo(i);
        }
    }

    @Test
    public void onBindViewHolder_bindWithOpenWifiNetwork_verifyView() {
        when(mWifiEntry.getSecurity()).thenReturn(WifiEntry.SECURITY_NONE);
        mInternetAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mWifiTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mWifiSummaryText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mWifiIcon.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mWifiEndIcon.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_bindWithSecurityWifiNetwork_verifyView() {
        when(mWifiEntry.getSecurity()).thenReturn(WifiEntry.SECURITY_PSK);
        mInternetAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mWifiTitleText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mWifiSummaryText.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mWifiIcon.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mViewHolder.mWifiEndIcon.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_getWifiDrawableNull_noCrash() {
        when(mInternetDetailsContentController.getWifiDrawable(any())).thenReturn(null);

        mInternetAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mWifiIcon.getDrawable()).isNull();
    }

    @Test
    public void onBindViewHolder_getWifiDrawableNotNull_setWifiIconDrawable() {
        when(mInternetDetailsContentController.getWifiDrawable(any())).thenReturn(mWifiDrawable);

        mInternetAdapter.onBindViewHolder(mViewHolder, 0);

        assertThat(mViewHolder.mWifiIcon.getDrawable()).isEqualTo(mWifiDrawable);
    }

    @Test
    public void setWifiEntries_wifiCountLessThenMaxCount_setWifiCount() {
        final int wifiCount = MAX_WIFI_ENTRY_COUNT - 1;
        mInternetAdapter.mMaxEntriesCount = MAX_WIFI_ENTRY_COUNT;

        mInternetAdapter.setWifiEntries(mWifiEntries, wifiCount);

        assertThat(mInternetAdapter.mWifiEntriesCount).isEqualTo(wifiCount);
    }

    @Test
    public void setWifiEntries_wifiCountGreaterThenMaxCount_setMaxCount() {
        final int wifiCount = MAX_WIFI_ENTRY_COUNT;
        mInternetAdapter.mMaxEntriesCount = MAX_WIFI_ENTRY_COUNT - 1;

        mInternetAdapter.setWifiEntries(mWifiEntries, wifiCount);

        assertThat(mInternetAdapter.mWifiEntriesCount).isEqualTo(mInternetAdapter.mMaxEntriesCount);
    }

    @Test
    public void setMaxEntriesCount_maxCountLessThenZero_doNothing() {
        mInternetAdapter.mMaxEntriesCount = MAX_WIFI_ENTRY_COUNT;
        final int maxCount = -1;

        mInternetAdapter.setMaxEntriesCount(maxCount);

        assertThat(mInternetAdapter.mMaxEntriesCount).isEqualTo(MAX_WIFI_ENTRY_COUNT);
    }

    @Test
    public void setMaxEntriesCount_maxCountGreaterThenWifiCount_updateMaxCount() {
        mInternetAdapter.mWifiEntriesCount = MAX_WIFI_ENTRY_COUNT - 2;
        mInternetAdapter.mMaxEntriesCount = MAX_WIFI_ENTRY_COUNT;
        final int maxCount = MAX_WIFI_ENTRY_COUNT - 1;

        mInternetAdapter.setMaxEntriesCount(maxCount);

        assertThat(mInternetAdapter.mWifiEntriesCount).isEqualTo(MAX_WIFI_ENTRY_COUNT - 2);
        assertThat(mInternetAdapter.mMaxEntriesCount).isEqualTo(maxCount);
    }

    @Test
    public void setMaxEntriesCount_maxCountLessThenWifiCount_updateBothCount() {
        mInternetAdapter.mWifiEntriesCount = MAX_WIFI_ENTRY_COUNT;
        mInternetAdapter.mMaxEntriesCount = MAX_WIFI_ENTRY_COUNT;
        final int maxCount = MAX_WIFI_ENTRY_COUNT - 1;

        mInternetAdapter.setMaxEntriesCount(maxCount);

        assertThat(mInternetAdapter.mWifiEntriesCount).isEqualTo(maxCount);
        assertThat(mInternetAdapter.mMaxEntriesCount).isEqualTo(maxCount);
    }

    @Test
    public void viewHolderShouldEnabled_wifiCanConnect_returnTrue() {
        when(mWifiEntry.canConnect()).thenReturn(true);

        assertThat(mViewHolder.shouldEnabled(mWifiEntry)).isTrue();
    }

    @Test
    public void viewHolderShouldEnabled_wifiCanNotConnect_returnFalse() {
        when(mWifiEntry.canConnect()).thenReturn(false);

        assertThat(mViewHolder.shouldEnabled(mWifiEntry)).isFalse();
    }

    @Test
    public void viewHolderShouldEnabled_wifiCanNotConnectButCanDisconnect_returnTrue() {
        when(mWifiEntry.canConnect()).thenReturn(false);
        when(mWifiEntry.canConnect()).thenReturn(true);

        assertThat(mViewHolder.shouldEnabled(mWifiEntry)).isTrue();
    }

    @Test
    public void viewHolderShouldEnabled_wifiCanNotConnectButIsSaved_returnTrue() {
        when(mWifiEntry.canConnect()).thenReturn(false);
        when(mWifiEntry.isSaved()).thenReturn(true);

        assertThat(mViewHolder.shouldEnabled(mWifiEntry)).isTrue();
    }

    @Test
    public void viewHolderOnWifiClick_wifiShouldEditBeforeConnect_startActivity() {
        when(mWifiEntry.shouldEditBeforeConnect()).thenReturn(true);
        mViewHolder = mInternetAdapter.onCreateViewHolder(new LinearLayout(mSpyContext), 0);
        doNothing().when(mSpyContext).startActivity(any());

        mViewHolder.onWifiClick(mWifiEntry, mock(View.class));

        verify(mInternetDetailsContentController).startActivityForDialog(any());
        verify(mSpyContext, never()).startActivity(any());
    }

    @Test
    public void viewHolderOnWifiClick_wifiCanConnect_connectWifi() {
        when(mWifiEntry.canConnect()).thenReturn(true);

        mViewHolder.onWifiClick(mWifiEntry, mock(View.class));

        verify(mInternetDetailsContentController).connect(mWifiEntry);
    }

    @Test
    public void viewHolderOnWifiClick_wifiCanNotConnectButIsSaved_launchWifiDetailsSetting() {
        when(mWifiEntry.canConnect()).thenReturn(false);
        when(mWifiEntry.isSaved()).thenReturn(true);

        mViewHolder.onWifiClick(mWifiEntry, mock(View.class));

        verify(mInternetDetailsContentController).launchWifiDetailsSetting(anyString(), any());
    }

    @Test
    public void viewHolderUpdateEndIcon_wifiConnected_updateGearIcon() {
        mTestableResources.addOverride(GEAR_ICON_RES_ID, mGearIcon);

        mViewHolder.updateEndIcon(WifiEntry.CONNECTED_STATE_CONNECTED, WifiEntry.SECURITY_PSK);

        assertThat(mViewHolder.mWifiEndIcon.getDrawable()).isEqualTo(mGearIcon);
    }

    @Test
    public void viewHolderUpdateEndIcon_wifiDisconnectedAndSecurityPsk_updateLockIcon() {
        mTestableResources.addOverride(LOCK_ICON_RES_ID, mLockIcon);

        mViewHolder.updateEndIcon(WifiEntry.CONNECTED_STATE_DISCONNECTED, WifiEntry.SECURITY_PSK);

        assertThat(mViewHolder.mWifiEndIcon.getDrawable()).isEqualTo(mLockIcon);
    }

    @Test
    public void viewHolderUpdateEndIcon_wifiDisconnectedAndSecurityNone_hideIcon() {
        mViewHolder.updateEndIcon(WifiEntry.CONNECTED_STATE_DISCONNECTED, WifiEntry.SECURITY_NONE);

        assertThat(mViewHolder.mWifiEndIcon.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void setShowAllWifi_returnWifiEntriesCount() {
        int wifiEntryCount = MAX_WIFI_ENTRY_COUNT * 2;
        when(mWifiEntries.size()).thenReturn(wifiEntryCount);
        mInternetAdapter.setShowAllWifi();
        mInternetAdapter.setWifiEntries(mWifiEntries, wifiEntryCount);
        if (QsWifiConfig.isEnabled()) {
            assertThat(mInternetAdapter.getItemCount()).isEqualTo(wifiEntryCount);
        } else {
            assertThat(mInternetAdapter.getItemCount()).isEqualTo(MAX_WIFI_ENTRY_COUNT);
        }
    }
}
