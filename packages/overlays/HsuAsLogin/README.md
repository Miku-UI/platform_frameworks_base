# HsuAsLogin overlay

Headless System User Mode (HSUM) is a mode in which the SYSTEM user (user 0) is not a human user.
This Headless System User (HSU) may or may not be eligible to be switched to. The AOSP default is
that it *cannot* be switched to; it merely hosts the system.

However, it is also possible to support an interactive Headless System User Mode, in which the HSU
can be switched to for various reasons. A particularly important use-case is to have an
interactive HSU that hosts a login screen.

The overlay here contains some basic config values that a login-screen interactive HSU would
generally need.

It is incorporated into `build/make/target/product/hsu_as_login.mk`. A device wishing to use such a
configuration will generally want to add to its make file:
```
$(call inherit-product, build/make/target/product/hsu_as_login.mk)
```

# Core configuration values

This overlay focuses on enabling:
* `config_canSwitchToHeadlessSystemUser = true`
* `config_hsumBootStrategy = 1`


# Other potentially relevant (optional) configuration values

There are several other configurations that may be desired in an HsuAsLogin mode, depending on the
desired behaviour, which we do not affect in this overlay.
In particular, to mandate that user switches must always proceed via first
logging out the current user to the login screen, one may also wish to enable:
* `config_userSwitchingMustGoThroughLoginScreen = true`
* `config_showUserSwitcherByDefault = false`
* `config_allowChangeUserSwitcherEnabled = false`
* `config_enableUserSwitcherUponUserCreation = false`


