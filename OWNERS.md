# Background

Internal only link: go/fb-owners.

As general background, `OWNERS` (especially `<TEAM>_OWNERS`) files expedite code
reviews by helping code authors quickly find relevant reviewers, and they also
ensure that stakeholders are involved in code changes in their areas.

# How to create new OWNERS file and get it reviewed quickly

1. Create your `OWNERS` file change:
  - Please follow structure in "Review Structure" section below.
  - Please include a bug component. See "Bug Component" section below.
2. Follow these steps to get it reviewed in Gerrit quickly:
  - please find the people that normally work on this code. For instance,
    check git history and/or review history. These people who own the code but
    are not yet reflected in the owners system are called the "de facto OWNERS".
  - add a screenshot of recent git maintainers and/or reviewers to the Gerrit
    review in a comment, showing that the "de facto OWNERS" really are
    represented. If you don't do this, whoever is approving the change has to do
    this research themselves. Internal only: go/sniplt will make this fast!
  - add the "de facto OWNERS" as a reviewer to Gerrit initially to approve
    themselves and any other reviewers that you are adding.
  - once they approve, add one (and only one) LAST_RESORT_OWNERS as reviewers
    from frameworks/base/OWNERS. These people get many many reviews, so doing
    research to make sure OWNERS are properly reflected make it very easy for
    them to approve. These are the reviewers that Gerrit will suggest on
    "unowned" files.
3. Relax & celebrate! Good job!

# Review Structure

The structure of `frameworks/base/` is unique among Android repositories, and
it's evolved into a complex interleaved structure over the years.  Because of
this structure, we recommend `<TEAM>_OWNERS` files at the root of
frameworks/base.

Area maintainers are strongly encouraged to list people in a single
authoritative `OWNERS` file in **exactly one** location, preferably at the
`frameworks/base` root directory. Then, other paths should reference that
single authoritative `OWNERS` file using an include directive. This approach
ensures that updates are applied consistently across the tree, reducing
maintenance burden.

For some common teams, these authorative places can be
used:

* `core/java/` contains source that is included in the base classpath, and as
such it's where most APIs are defined:
  * `core/java/android/app/`
  * `core/java/android/content/`
* `services/core/` contains most system services, and these directories
typically have more granularity than `core/java/`, since they can be refactored
without API changes:
  * `services/core/java/com/android/server/net/`
  * `services/core/java/com/android/server/wm/`
* `services/` contains several system services that have been isolated from the
main `services/core/` project:
  * `services/appwidget/`
  * `services/midi/`
* `apex/` contains Mainline modules:
  * `apex/jobscheduler/`
  * `apex/permission/`
* Finally, some teams may have dedicated top-level directories:
  * `media/`
  * `wifi/`

# Bug component

Always include an up-to-date bug component in the top-level `<TEAM>_OWNERS` files:

```
# Bug component: XXX
```

# Examples

The exact syntax of `OWNERS` files can be difficult to get correct, so here are
some common examples:

```
# Complete include of top-level owners from this repo
include /ZYGOTE_OWNERS
# Partial include of top-level owners from this repo
per-file ZygoteFile.java = file:/ZYGOTE_OWNERS
```
```
# Complete include of subdirectory owners from this repo
include /services/core/java/com/android/server/net/OWNERS
# Partial include of subdirectory owners from this repo
per-file NetworkFile.java = file:/services/core/java/com/android/server/net/OWNERS
```
```
# Complete include of top-level owners from another repo
include platform/libcore:/OWNERS
# Partial include of top-level owners from another repo
per-file LibcoreFile.java = file:platform/libcore:/OWNERS
```
```
# Complete include of subdirectory owners from another repo
include platform/frameworks/av:/camera/OWNERS
# Partial include of subdirectory owners from another repo
per-file CameraFile.java = file:platform/frameworks/av:/camera/OWNERS
```
