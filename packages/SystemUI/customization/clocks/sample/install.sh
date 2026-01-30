
source $ANDROID_BUILD_TOP/build/envsetup.sh

m SystemUIClocks-Sample
if [ $? -ne 0 ]; then
    exit $?
fi

adb root
adb remount
adb sync
adb shell pm uninstall --user 0 com.android.systemui.clocks.sample
adb shell pm install /system_ext/priv-app/SystemUIClocks-Sample/SystemUIClocks-Sample.apk

# New Providers
adb shell pm enable com.android.systemui.clocks.sample/com.android.systemui.clocks.sample.SampleClockProvider

if [ "$1" == "-r" ]; then
    adb reboot
else
    adb shell stop
    adb shell start
fi
